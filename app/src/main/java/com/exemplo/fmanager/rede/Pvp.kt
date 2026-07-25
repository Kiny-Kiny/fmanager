package com.exemplo.fmanager.rede

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.rendimentoEm
import com.exemplo.fmanager.formacao.Slot
import com.exemplo.fmanager.formacao.Tatica
import com.exemplo.fmanager.motor.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/*
 * PVP ASSÍNCRONO — "ULTIMATE TEAM DE TÉCNICO".
 *
 * Três decisões de desenho que sustentam tudo:
 *
 * 1. ORÇAMENTO IGUAL PARA TODOS. Ninguém joga com o Real Madrid porque
 *    escolheu o Real Madrid. Cada técnico recebe o mesmo número de moedas
 *    e monta os 11 do acervo mundial. O que se compara é montar elenco e
 *    armar time — não quem pegou o clube mais rico.
 *
 * 2. ASSÍNCRONO. Os dois jamais precisam estar online juntos. Você
 *    publica seu desafio, alguém aceita quando quiser, e cada lado simula
 *    quando abrir o app. É o que faz um jogo de celular funcionar.
 *
 * 3. SIMULAÇÃO DUPLA COM CONFERÊNCIA. O servidor NÃO simula nada — ele só
 *    guarda dados. Os dois clientes rodam a mesma partida com a mesma
 *    semente e enviam o resultado com checksum. O servidor aceita quando
 *    os dois batem, e marca disputa quando não batem.
 *
 *    Isso é o que permite não escrever servidor: o PocketBase é um banco
 *    com API, não um juiz. O árbitro é o determinismo do motor.
 */

const val ORCAMENTO_PVP = 700L        // em "moedas"
const val VERSAO_PVP = 1

// ------------------------------------------------- CUSTO DO JOGADOR

object Precos {

    /**
     * Custo em moedas. Exponencial de propósito.
     *
     * Com curva linear o melhor elenco seria sempre "onze jogadores de 84"
     * e não haveria decisão. Exponencial força a escolha real do Ultimate
     * Team: dois craques e nove medianos, ou onze bons e nenhum craque?
     */
    fun de(jogador: Jogador): Long {
        val base = (jogador.geral / 60.0).pow(6.2) * 9
        val porIdade = when {
            jogador.idade <= 23 -> 1.18
            jogador.idade <= 29 -> 1.0
            jogador.idade <= 32 -> 0.86
            else -> 0.70
        }
        return (base * porIdade).toLong().coerceAtLeast(1)
    }

    fun totalDe(jogadores: List<Jogador>): Long = jogadores.sumOf { de(it) }
}

// -------------------------------------------------------- CLASSIFICAÇÃO

data class RatingTecnico(
    val id: String,
    val apelido: String,
    val pontos: Int,
    val vitorias: Int,
    val empates: Int,
    val derrotas: Int,
) {
    val jogos get() = vitorias + empates + derrotas

    val divisao: String get() = when {
        pontos >= 1900 -> "Elite"
        pontos >= 1700 -> "Divisão 1"
        pontos >= 1500 -> "Divisão 2"
        pontos >= 1300 -> "Divisão 3"
        else -> "Divisão 4"
    }
}

object Elo {
    const val INICIAL = 1400
    private const val K = 28

    fun novoPonto(meu: Int, dele: Int, resultado: Float): Int {
        val esperado = 1f / (1f + 10f.pow((dele - meu) / 400f))
        return (meu + K * (resultado - esperado)).toInt().coerceAtLeast(800)
    }
}

// ----------------------------------------------------------- DESAFIOS

enum class EstadoDesafio { ABERTO, ACEITO, CONFIRMADO, EM_DISPUTA }

data class Desafio(
    val id: String,
    val donoId: String,
    val donoApelido: String,
    val donoPontos: Int,
    val estado: EstadoDesafio,
    val semente: Long,
    val elencoDono: JSONObject,
    val adversarioId: String? = null,
    val adversarioApelido: String? = null,
    val elencoAdversario: JSONObject? = null,
    val resultadoDono: String? = null,
    val resultadoAdversario: String? = null,
)

class Pvp(private val backend: Backend) {

    // ------------------------------------------------- PUBLICAR DESAFIO

    /**
     * Publica um desafio aberto com o seu elenco e tática.
     *
     * A semente é gerada aqui e fica registrada no desafio: quem aceitar
     * usa a MESMA, e por isso os dois chegam ao mesmo resultado.
     */
    suspend fun publicarDesafio(
        elenco: List<Jogador>,
        slots: List<Slot>,
        tatica: Tatica,
        nomeDoTime: String,
    ): Result<String> {
        val custo = Precos.totalDe(elenco)
        if (custo > ORCAMENTO_PVP) {
            return Result.failure(
                IllegalStateException(
                    "Elenco custa $custo moedas, o limite é $ORCAMENTO_PVP."
                )
            )
        }

        val dados = JSONObject().apply {
            put("versao", VERSAO_PVP)
            put("dono", backend.usuarioId ?: return Result.failure(
                IllegalStateException("Entre na sua conta primeiro.")))
            put("dono_apelido", backend.apelido ?: "Técnico")
            put("estado", EstadoDesafio.ABERTO.name)
            put("semente", Random.nextLong(1, Long.MAX_VALUE / 4))
            put("elenco_dono", Corpos.esquadrao(nomeDoTime, elenco, slots, tatica))
            put("custo_dono", custo)
        }

        return backend.criar("desafios", dados).map { it.optString("id") }
    }

    /**
     * Desafios abertos de outras pessoas, ordenados por proximidade de
     * rating — enfrentar alguém 600 pontos acima não é competição.
     */
    suspend fun desafiosAbertos(meuId: String, meusPontos: Int): Result<List<Desafio>> =
        backend.listar(
            colecao = "desafios",
            filtro = "estado='ABERTO' && dono!='$meuId' && versao=$VERSAO_PVP",
            ordem = "-created",
            limite = 40,
        ).map { registros ->
            registros
                .mapNotNull { desafioDeJson(it) }
                .sortedBy { abs(it.donoPontos - meusPontos) }
        }

    // --------------------------------------------------- ACEITAR

    suspend fun aceitar(
        desafio: Desafio,
        elenco: List<Jogador>,
        slots: List<Slot>,
        tatica: Tatica,
        nomeDoTime: String,
    ): Result<Desafio> {
        val custo = Precos.totalDe(elenco)
        if (custo > ORCAMENTO_PVP) {
            return Result.failure(
                IllegalStateException("Elenco acima do orçamento.")
            )
        }

        val dados = JSONObject().apply {
            put("estado", EstadoDesafio.ACEITO.name)
            put("adversario", backend.usuarioId)
            put("adversario_apelido", backend.apelido ?: "Técnico")
            put("elenco_adversario",
                Corpos.esquadrao(nomeDoTime, elenco, slots, tatica))
            put("custo_adversario", custo)
        }

        return backend.atualizar("desafios", desafio.id, dados)
            .mapCatching { desafioDeJson(it) ?: error("Resposta inválida.") }
    }

    // ------------------------------------------------------- SIMULAR

    /**
     * Monta a partida a partir dos dois elencos publicados.
     *
     * O dono do desafio é sempre o mandante nos DOIS aparelhos. Sem essa
     * regra fixa cada lado montaria a partida invertida e os resultados
     * nunca bateriam.
     */
    fun montarPartida(desafio: Desafio): PartidaAoVivo? {
        val elencoAdv = desafio.elencoAdversario ?: return null

        fun montar(pacote: JSONObject, id: Int): TimeEmCampo? {
            val jogadores = pacote.getJSONArray("jogadores").let { arr ->
                (0 until arr.length()).map { jogadorDeJson(arr.getJSONObject(it)) }
            }
            val slots = pacote.getJSONArray("slots").let { arr ->
                (0 until arr.length()).map { slotDeJson(arr.getJSONObject(it)) }
            }
            val tatica = taticaDeJson(pacote.getJSONObject("tatica"))
            val porId = jogadores.associateBy { it.id }
            val usados = mutableSetOf<Int>()

            val emCampo = slots.take(11).map { s ->
                val j = s.jogadorId?.let { porId[it] }?.takeIf { it.id !in usados }
                    ?: jogadores.firstOrNull { it.id !in usados }
                    ?: return null
                usados += j.id
                JogadorEmCampo(j, s, entrosamento = 50, moral = 65)
            }
            if (emCampo.size < 11) return null

            return TimeEmCampo(
                clubeId = id,
                nome = pacote.optString("clube").ifBlank { "Time" },
                escalacao = emCampo,
                tatica = tatica,
                reservas = jogadores.filter { it.id !in usados }.take(7),
            )
        }

        val casa = montar(desafio.elencoDono, 1) ?: return null
        val fora = montar(elencoAdv, 2) ?: return null

        val p = PartidaAoVivo(casa, fora, Random(desafio.semente))
        // Os dois lados são humanos: nada de treinador automático.
        p.desligarIA()
        return p
    }

    /**
     * Envia o resultado com checksum.
     *
     * Quando os dois resultados chegam e batem, o desafio é CONFIRMADO e
     * o rating se move. Quando não batem, vai para EM_DISPUTA e ninguém
     * pontua — é assim que um cliente modificado consegue no máximo
     * anular a partida, nunca fabricar uma vitória.
     */
    suspend fun enviarResultado(
        desafio: Desafio,
        resultado: Resultado,
        souDono: Boolean,
    ): Result<EstadoDesafio> {
        val assinatura = assinaturaDoResultado(resultado)
        val campo = if (souDono) "resultado_dono" else "resultado_adversario"

        val outro = if (souDono) desafio.resultadoAdversario
        else desafio.resultadoDono

        val novoEstado = when {
            outro == null -> EstadoDesafio.ACEITO          // esperando o outro
            outro == assinatura -> EstadoDesafio.CONFIRMADO
            else -> EstadoDesafio.EM_DISPUTA
        }

        val dados = JSONObject().apply {
            put(campo, assinatura)
            put("estado", novoEstado.name)
            if (novoEstado == EstadoDesafio.CONFIRMADO) {
                put("gols_dono", resultado.golsMandante)
                put("gols_adversario", resultado.golsVisitante)
            }
        }

        return backend.atualizar("desafios", desafio.id, dados)
            .map { novoEstado }
    }

    /**
     * Assinatura do resultado. Cobre placar e as estatísticas principais —
     * se as duas simulações divergirem em qualquer ponto material, os
     * textos não batem.
     */
    private fun assinaturaDoResultado(r: Resultado): String = Cripto.impressao(
        buildString {
            append(r.golsMandante).append(':').append(r.golsVisitante).append('|')
            append(r.statsMandante.chutes).append(':')
            append(r.statsVisitante.chutes).append('|')
            append(r.statsMandante.passesCertos).append(':')
            append(r.statsVisitante.passesCertos).append('|')
            append(r.statsMandante.faltas).append(':')
            append(r.statsVisitante.faltas).append('|')
            append(r.lances.size)
        }
    )

    // ---------------------------------------------------- CLASSIFICAÇÃO

    suspend fun classificacao(): Result<List<RatingTecnico>> =
        backend.listar("tecnicos", ordem = "-pontos", limite = 50)
            .map { registros ->
                registros.map {
                    RatingTecnico(
                        id = it.optString("id"),
                        apelido = it.optString("apelido").ifBlank { "Técnico" },
                        pontos = it.optInt("pontos", Elo.INICIAL),
                        vitorias = it.optInt("vitorias"),
                        empates = it.optInt("empates"),
                        derrotas = it.optInt("derrotas"),
                    )
                }
            }

    /** Atualiza o próprio rating depois de um desafio confirmado. */
    suspend fun registrarResultado(
        meusPontos: Int,
        pontosDoOutro: Int,
        meusGols: Int,
        golsDele: Int,
        vitoriasAtuais: Int,
        empatesAtuais: Int,
        derrotasAtuais: Int,
    ): Result<Int> {
        val id = backend.usuarioId
            ?: return Result.failure(IllegalStateException("Sem sessão."))

        val pontuacao = when {
            meusGols > golsDele -> 1f
            meusGols == golsDele -> 0.5f
            else -> 0f
        }
        val novos = Elo.novoPonto(meusPontos, pontosDoOutro, pontuacao)

        val dados = JSONObject().apply {
            put("pontos", novos)
            put("vitorias", vitoriasAtuais + if (pontuacao == 1f) 1 else 0)
            put("empates", empatesAtuais + if (pontuacao == 0.5f) 1 else 0)
            put("derrotas", derrotasAtuais + if (pontuacao == 0f) 1 else 0)
        }

        return backend.atualizar("tecnicos", id, dados).map { novos }
    }

    // ---------------------------------------------------------- LEITURA

    private fun desafioDeJson(o: JSONObject): Desafio? = runCatching {
        Desafio(
            id = o.getString("id"),
            donoId = o.getString("dono"),
            donoApelido = o.optString("dono_apelido").ifBlank { "Técnico" },
            donoPontos = o.optInt("dono_pontos", Elo.INICIAL),
            estado = runCatching {
                EstadoDesafio.valueOf(o.optString("estado"))
            }.getOrDefault(EstadoDesafio.ABERTO),
            semente = o.optLong("semente", 1L),
            elencoDono = o.getJSONObject("elenco_dono"),
            adversarioId = o.optString("adversario").ifBlank { null },
            adversarioApelido = o.optString("adversario_apelido").ifBlank { null },
            elencoAdversario = o.optJSONObject("elenco_adversario"),
            resultadoDono = o.optString("resultado_dono").ifBlank { null },
            resultadoAdversario = o.optString("resultado_adversario")
                .ifBlank { null },
        )
    }.getOrNull()

    /** Meus desafios: os que publiquei e os que aceitei. */
    suspend fun meusDesafios(meuId: String): Result<List<Desafio>> =
        backend.listar(
            colecao = "desafios",
            filtro = "dono='$meuId' || adversario='$meuId'",
            ordem = "-updated",
            limite = 30,
        ).map { it.mapNotNull { r -> desafioDeJson(r) } }
}

// -------------------------------------------------- MONTADOR DE ELENCO

/*
 * Sugestão automática dentro do orçamento.
 *
 * Existe porque montar onze jogadores de 16 mil na mão, respeitando um
 * teto de moedas, é trabalhoso na primeira vez. A sugestão dá um ponto de
 * partida jogável; a otimização fina fica com você, que é onde está a
 * graça.
 */
object MontadorPvp {

    fun sugerir(
        universo: List<Jogador>,
        slots: List<Slot>,
        orcamento: Long = ORCAMENTO_PVP,
    ): List<Jogador> {
        val escolhidos = mutableListOf<Jogador>()
        var restante = orcamento
        val usados = mutableSetOf<Int>()

        // Goleiro primeiro: é o único posto insubstituível.
        val ordem = slots.sortedBy {
            if (it.papelPrincipal.sigla == "GOL") 0 else 1
        }

        ordem.forEachIndexed { indice, slot ->
            val postosRestantes = ordem.size - indice
            // Reserva o mínimo para os postos que ainda faltam, senão o
            // primeiro slot come o orçamento inteiro.
            val teto = (restante - (postosRestantes - 1) * 8).coerceAtLeast(1)

            val melhor = universo
                .asSequence()
                .filter { it.id !in usados && Precos.de(it) <= teto }
                // Melhor rendimento POR MOEDA, não melhor rendimento.
                // É a conta que o Ultimate Team obriga a fazer.
                .maxByOrNull {
                    it.rendimentoEm(slot.papelPrincipal).toDouble() /
                            Precos.de(it).coerceAtLeast(1)
                }

            if (melhor != null) {
                escolhidos += melhor
                usados += melhor.id
                restante -= Precos.de(melhor)
                slot.jogadorId = melhor.id
                slot.nome = melhor.nome
            }
        }

        return escolhidos
    }
}
