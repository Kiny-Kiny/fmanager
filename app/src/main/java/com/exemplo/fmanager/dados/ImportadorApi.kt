package com.exemplo.fmanager.dados

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * IMPORTADOR VIA API — api.msmc.cc
 *
 * A API é ótima, mas é projeto de hobby: sem autenticação, sem SLA, sem
 * paginação documentada. Por isso ela NÃO é dependência de gameplay.
 * Roda uma vez, popula o SQLite e sai de cena. Se cair amanhã, o seu
 * jogo continua funcionando igual.
 *
 * Duas decisões que vêm disso:
 *   1. Importa LIGA POR LIGA. Pedir os 16 mil de uma vez estoura.
 *   2. Se qualquer liga falhar, as outras seguem. Importação parcial é
 *      melhor que nenhuma.
 *
 * Sem OkHttp nem Retrofit de propósito: HttpURLConnection e org.json já
 * vêm no Android. Menos dependência, APK menor, menos coisa pra quebrar.
 */

object ImportadorApi {

    private const val BASE = "https://api.msmc.cc/api/eafc"
    private const val TIMEOUT_MS = 20_000

    data class Progresso(
        val ligasFeitas: Int,
        val ligasTotal: Int,
        val jogadores: Int,
        val ligaAtual: String,
    )

    /**
     * Popula o banco. `jogo` aceita "fc25" ou "fc26".
     *
     * Chame com onProgresso para alimentar a barra na tela — a
     * importação leva alguns minutos numa conexão comum.
     */
    suspend fun importar(
        db: AppDatabase,
        jogo: String = "fc26",
        genero: String = "m",
        onProgresso: (Progresso) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val nomesLigas = buscarLigas(jogo, genero)
            if (nomesLigas.isEmpty()) error("A API não devolveu nenhuma liga.")

            val ligas = nomesLigas.mapIndexed { i, nome ->
                Liga(id = i + 1, nome = nome, pais = "", reputacao = 50)
            }
            db.ligas().inserirTodas(ligas)

            var proximoIdClube = 1
            var totalJogadores = 0

            ligas.forEach { liga ->
                onProgresso(
                    Progresso(ligas.indexOf(liga), ligas.size, totalJogadores, liga.nome)
                )

                // Uma liga que falha não derruba a importação inteira.
                val brutos = runCatching {
                    buscarJogadoresDaLiga(liga.nome, jogo, genero)
                }.getOrElse { return@forEach }

                if (brutos.isEmpty()) return@forEach

                // Clubes saem dos próprios jogadores.
                val porTime = brutos.groupBy { it.time.ifBlank { "Sem clube" } }
                val clubes = porTime.map { (nomeTime, jogs) ->
                    val rep = jogs.map { it.geral }.average().toInt().coerceIn(35, 95)
                    Clube(
                        id = proximoIdClube++,
                        nome = nomeTime,
                        ligaId = liga.id,
                        reputacao = rep,
                        caixaEur = (rep.toDouble().pow(3.4) * 90).roundToLong(),
                        folhaMaxEur = (rep.toDouble().pow(2.9) * 12).roundToLong(),
                    )
                }
                val idPorTime = clubes.associate { it.nome to it.id }

                val jogadores = brutos.map { b ->
                    b.paraJogador(idPorTime[b.time.ifBlank { "Sem clube" }], liga.nome)
                }

                db.clubes().inserirTodos(clubes)
                db.jogadores().inserirTodos(jogadores)
                db.contratos().salvarTodos(gerarContratos(jogadores))

                totalJogadores += jogadores.size
            }

            // Reputação da liga = média dos clubes dela.
            ligas.forEach { liga ->
                val clubes = db.clubes().porLiga(liga.id)
                if (clubes.isNotEmpty()) {
                    db.ligas().inserirTodas(listOf(
                        liga.copy(reputacao = clubes.map { it.reputacao }.average().toInt())
                    ))
                }
            }

            onProgresso(Progresso(ligas.size, ligas.size, totalJogadores, "Concluído"))
            totalJogadores
        }
    }

    // ------------------------------------------------------ REQUISIÇÕES

    private fun buscarLigas(jogo: String, genero: String): List<String> {
        val corpo = get("$BASE/leagues?game=$jogo&gender=$genero") ?: return emptyList()
        val json = JSONArray(corpo)
        return (0 until json.length()).mapNotNull { i ->
            when (val item = json.get(i)) {
                is String -> item
                is JSONObject -> item.optString("league")
                    .ifBlank { item.optString("name") }
                else -> null
            }.takeIf { !it.isNullOrBlank() }
        }
    }

    private fun buscarJogadoresDaLiga(
        liga: String, jogo: String, genero: String,
    ): List<JogadorBruto> {
        val ligaCodificada = URLEncoder.encode(liga, "UTF-8")
        val corpo = get(
            "$BASE/players?league=$ligaCodificada&game=$jogo&gender=$genero"
        ) ?: return emptyList()

        // A API devolve array puro, mas alguns endpoints envelopam em
        // { "players": [...] }. Aceita os dois.
        val array = when {
            corpo.trimStart().startsWith("[") -> JSONArray(corpo)
            else -> JSONObject(corpo).optJSONArray("players") ?: JSONArray()
        }

        return (0 until array.length()).mapNotNull { i ->
            runCatching { JogadorBruto.de(array.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun get(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FManager/0.1")
            try {
                if (responseCode !in 200..299) return@run null
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }
    }.getOrNull()

    // ----------------------------------------------------------- DTO

    /**
     * Na API TODO valor vem como string, inclusive número, e vários
     * campos vêm vazios (goleiro tem diving, jogador de linha não).
     * Por isso todo acesso passa por num()/txt(), que nunca estouram.
     */
    private class JogadorBruto(private val j: JSONObject) {

        private fun txt(campo: String): String =
            j.optString(campo, "").trim().takeIf { it != "null" } ?: ""

        private fun num(campo: String, padrao: Int = 0): Int =
            txt(campo).takeWhile { it.isDigit() }.toIntOrNull() ?: padrao

        val id = num("id")
        val nome = txt("name")
        val time = txt("team")
        val geral = num("ovr", 50)

        val tracos: List<TracoJogador> = run {
            val array = j.optJSONArray("play style") ?: return@run emptyList()
            (0 until array.length()).mapNotNull { i ->
                PlayStyle.deTexto(array.optString(i))
                    ?.let { (estilo, elite) -> TracoJogador(estilo, elite) }
            }
        }

        fun paraJogador(clubeId: Int?, liga: String) = Jogador(
            id = id,
            nome = nome,
            idade = num("age", 25).coerceIn(15, 45),
            nacionalidade = txt("nation"),
            clubeId = clubeId,
            clube = time,
            liga = liga,
            posicao = txt("position").ifBlank { "CM" },
            posicoesAlt = txt("alternative positions"),
            peDominante = txt("preferred foot"),
            pernaRuim = num("weak foot", 3).coerceIn(1, 5),
            estrelasDrible = num("skill moves", 3).coerceIn(1, 5),
            geral = geral,
            // A API não traz potencial: estima pela idade. Jovem tem
            // margem, veterano já chegou onde ia chegar.
            potencial = estimarPotencial(geral, num("age", 25)),
            valorEur = estimarValor(geral, num("age", 25)),
            salarioEur = estimarSalario(geral),
            alturaCm = num("height", 180),
            pesoKg = num("weight", 75),

            aceleracao = num("acceleration"),
            velocidade = num("sprint speed"),
            posicionamento = num("positioning"),
            finalizacao = num("finishing"),
            forcaChute = num("shot power"),
            chuteLonge = num("long shots"),
            chutePrimeira = num("volleys"),
            penaltis = num("penalties"),
            visao = num("vision"),
            cruzamento = num("crossing"),
            cobrancaFalta = num("free kick accuracy"),
            passeBaixo = num("short passing"),
            passeAlto = num("long passing"),
            curva = num("curve"),
            drible = num("dribbling"),
            agilidade = num("agility"),
            equilibrio = num("balance"),
            reacoes = num("reactions"),
            controleBola = num("ball control"),
            sangueFrio = num("composure"),
            interceptacao = num("interceptions"),
            cabeceio = num("heading accuracy"),
            consciencaDef = num("def awareness"),
            rouboBola = num("standing tackle"),
            carrinho = num("sliding tackle"),
            impulsao = num("jumping"),
            resistencia = num("stamina"),
            contatoFisico = num("strength"),
            agressividade = num("aggression"),

            golMergulho = num("diving"),
            golDefesaMao = num("handling"),
            golChute = num("kicking"),
            golPosicionamento = num("gk positioning"),
            golReflexo = num("reflexes"),

            // O pulo do gato: a URL da carta do jogador.
            urlFoto = txt("card").ifBlank { null },
            // Guardados como texto simples separado por vírgula.
            tracosTexto = tracos.joinToString(",") {
                it.estilo.name + if (it.elite) "+" else ""
            },
        )

        companion object {
            fun de(json: JSONObject) = JogadorBruto(json)

            private fun estimarPotencial(geral: Int, idade: Int): Int = when {
                idade <= 18 -> geral + (10..18).random()
                idade <= 21 -> geral + (6..14).random()
                idade <= 24 -> geral + (3..9).random()
                idade <= 27 -> geral + (0..4).random()
                else -> geral
            }.coerceAtMost(94)

            private fun estimarValor(geral: Int, idade: Int): Long {
                val base = (geral.toDouble() / 55.0).pow(7.5) * 90_000
                val fatorIdade = when {
                    idade <= 21 -> 1.7
                    idade <= 25 -> 1.4
                    idade <= 29 -> 1.0
                    idade <= 32 -> 0.6
                    else -> 0.3
                }
                return (base * fatorIdade).roundToLong().coerceAtLeast(30_000)
            }

            private fun estimarSalario(geral: Int): Long =
                ((geral.toDouble() / 60.0).pow(4.2) * 2_500)
                    .roundToLong().coerceAtLeast(800)
        }
    }

    private fun gerarContratos(jogadores: List<Jogador>): List<Contrato> {
        val rng = Random(42)
        return jogadores.filter { it.clubeId != null }.map { j ->
            Contrato(
                jogadorId = j.id,
                clubeId = j.clubeId!!,
                salarioSemanalEur = j.salarioEur,
                terminaEmTemporada = rng.nextInt(1, 5),
                clausulaRescisaoEur = (j.valorEur * 1.8).roundToLong(),
                moral = rng.nextInt(55, 90),
            )
        }
    }
}

/** Reconstrói os traços a partir do texto salvo no banco. */
fun Jogador.tracos(): List<TracoJogador> =
    tracosTexto.split(",").filter { it.isNotBlank() }.mapNotNull { bruto ->
        val elite = bruto.endsWith("+")
        val nome = bruto.removeSuffix("+")
        runCatching { PlayStyle.valueOf(nome) }.getOrNull()
            ?.let { TracoJogador(it, elite) }
    }
