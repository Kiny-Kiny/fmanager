package com.exemplo.fmanager.dados

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * IMPORTADOR DO CSV.
 *
 * Roda UMA VEZ, na primeira abertura do app. Depois disso o jogo nunca
 * mais toca no arquivo — tudo vem do SQLite. É isso que faz o app abrir
 * instantâneo mesmo com 18 mil jogadores.
 *
 * COMO USAR:
 *   1. Baixe um dataset do EA FC no Kaggle
 *   2. Coloque o arquivo em app/src/main/assets/jogadores.csv
 *   3. O app importa sozinho na primeira execução
 *
 * Os nomes das colunas mudam de dataset para dataset, então cada campo
 * aceita vários apelidos. Se o seu CSV usar um nome que não está aqui,
 * é só acrescentar na lista.
 */

object ImportadorCsv {

    suspend fun importarSeNecessario(context: Context, db: AppDatabase) =
        withContext(Dispatchers.IO) {
            if (db.jogadores().total() > 0) return@withContext

            context.assets.open("jogadores.csv").bufferedReader().use { leitor ->
                val (jogadores, nomesClubes) = lerJogadores(leitor)

                // Ligas e clubes são deduzidos das colunas do próprio CSV,
                // então "todas as ligas" é o que vier no arquivo.
                val ligas = nomesClubes.map { it.liga }.distinct()
                    .mapIndexed { i, nome ->
                        Liga(id = i + 1, nome = nome, pais = "",
                            reputacao = reputacaoDaLiga(nome))
                    }
                val idPorLiga = ligas.associate { it.nome to it.id }

                val clubes = nomesClubes.mapIndexed { i, c ->
                    val rep = c.mediaGeral.coerceIn(40, 95)
                    Clube(
                        id = i + 1,
                        nome = c.nome,
                        ligaId = idPorLiga.getValue(c.liga),
                        reputacao = rep,
                        // Orçamento derivado da força do elenco.
                        caixaEur = (rep.toDouble().pow(3.4) * 90).roundToLong(),
                        folhaMaxEur = (rep.toDouble().pow(2.9) * 12).roundToLong(),
                    )
                }
                val idPorClube = clubes.associate { it.nome to it.id }

                val comClube = jogadores.map { (j, nomeClube) ->
                    j.copy(clubeId = idPorClube[nomeClube])
                }

                db.ligas().inserirTodas(ligas)
                db.clubes().inserirTodos(clubes)
                db.jogadores().inserirTodos(comClube)
                db.contratos().salvarTodos(gerarContratos(comClube))
            }
        }

    // ------------------------------------------------------ LEITURA

    private data class ClubeBruto(val nome: String, val liga: String, val mediaGeral: Int)

    private fun lerJogadores(
        leitor: BufferedReader,
    ): Pair<List<Pair<Jogador, String>>, List<ClubeBruto>> {

        val cabecalho = leitor.readLine()?.let { dividirLinha(it) } ?: return emptyList<Pair<Jogador, String>>() to emptyList<ClubeBruto>()
        val col = MapaColunas(cabecalho)

        val jogadores = mutableListOf<Pair<Jogador, String>>()
        val porClube = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        var id = 1

        leitor.forEachLine { linha ->
            if (linha.isBlank()) return@forEachLine
            val campos = dividirLinha(linha)
            if (campos.size < 5) return@forEachLine

            val nome = col.texto(campos, "name", "long_name", "short_name", "Name", "player_name")
            if (nome.isBlank()) return@forEachLine

            val clube = col.texto(campos, "club_name", "Team", "team", "club", "Club")
                .ifBlank { "Sem clube" }
            val liga = col.texto(campos, "league_name", "League", "league")
                .ifBlank { "Liga desconhecida" }
            val geral = col.num(campos, "overall", "OVR", "Overall", "rating")

            porClube.getOrPut(liga) { mutableListOf() }.add(clube to geral)

            jogadores += Jogador(
                id = id++,
                nome = nome,
                idade = col.num(campos, "age", "Age").coerceIn(15, 45),
                nacionalidade = col.texto(campos, "nationality_name", "Nation", "nationality"),
                clubeId = null,
                clube = clube,
                liga = liga,
                posicao = col.texto(campos, "player_positions", "Position", "position")
                    .split(",").first().trim().ifBlank { "CM" },
                posicoesAlt = col.texto(campos, "Alternative Positions", "player_positions"),
                peDominante = col.texto(campos, "preferred_foot", "Preferred Foot"),
                pernaRuim = col.num(campos, "weak_foot", "Weak Foot").coerceIn(1, 5),
                estrelasDrible = col.num(campos, "skill_moves", "Skill Moves").coerceIn(1, 5),
                geral = geral,
                potencial = col.num(campos, "potential", "Potential").coerceAtLeast(geral),
                valorEur = col.numLongo(campos, "value_eur", "Value", "value"),
                salarioEur = col.numLongo(campos, "wage_eur", "Wage", "wage"),
                alturaCm = col.num(campos, "height_cm", "Height"),
                pesoKg = col.num(campos, "weight_kg", "Weight"),

                aceleracao = col.num(campos, "movement_acceleration", "Acceleration"),
                velocidade = col.num(campos, "movement_sprint_speed", "Sprint Speed"),
                posicionamento = col.num(campos, "mentality_positioning", "Positioning"),
                finalizacao = col.num(campos, "attacking_finishing", "Finishing"),
                forcaChute = col.num(campos, "power_shot_power", "Shot Power"),
                chuteLonge = col.num(campos, "power_long_shots", "Long Shots"),
                chutePrimeira = col.num(campos, "attacking_volleys", "Volleys"),
                penaltis = col.num(campos, "mentality_penalties", "Penalties"),
                visao = col.num(campos, "mentality_vision", "Vision"),
                cruzamento = col.num(campos, "attacking_crossing", "Crossing"),
                cobrancaFalta = col.num(campos, "skill_fk_accuracy", "Free Kick Accuracy"),
                passeBaixo = col.num(campos, "attacking_short_passing", "Short Passing"),
                passeAlto = col.num(campos, "skill_long_passing", "Long Passing"),
                curva = col.num(campos, "skill_curve", "Curve"),
                drible = col.num(campos, "skill_dribbling", "Dribbling"),
                agilidade = col.num(campos, "movement_agility", "Agility"),
                equilibrio = col.num(campos, "movement_balance", "Balance"),
                reacoes = col.num(campos, "movement_reactions", "Reactions"),
                controleBola = col.num(campos, "skill_ball_control", "Ball Control"),
                sangueFrio = col.num(campos, "mentality_composure", "Composure"),
                interceptacao = col.num(campos, "mentality_interceptions", "Interceptions"),
                cabeceio = col.num(campos, "attacking_heading_accuracy", "Heading Accuracy"),
                consciencaDef = col.num(campos, "defending_marking_awareness", "Def Awareness"),
                rouboBola = col.num(campos, "defending_standing_tackle", "Standing Tackle"),
                carrinho = col.num(campos, "defending_sliding_tackle", "Sliding Tackle"),
                impulsao = col.num(campos, "power_jumping", "Jumping"),
                resistencia = col.num(campos, "power_stamina", "Stamina"),
                contatoFisico = col.num(campos, "power_strength", "Strength"),
                agressividade = col.num(campos, "mentality_aggression", "Aggression"),
                golMergulho = col.num(campos, "goalkeeping_diving", "GK Diving"),
                golDefesaMao = col.num(campos, "goalkeeping_handling", "GK Handling"),
                golChute = col.num(campos, "goalkeeping_kicking", "GK Kicking"),
                golPosicionamento = col.num(campos, "goalkeeping_positioning", "GK Positioning"),
                golReflexo = col.num(campos, "goalkeeping_reflexes", "GK Reflexes"),
            ) to clube
        }

        // Consolida os clubes com a média de overall do elenco.
        val clubes = mutableListOf<ClubeBruto>()
        porClube.forEach { (liga, lista) ->
            lista.groupBy { it.first }.forEach { (nomeClube, jogs) ->
                clubes += ClubeBruto(nomeClube, liga, jogs.map { it.second }.average().toInt())
            }
        }
        return jogadores to clubes
    }

    /** Resolve o índice de cada coluna uma vez só, não por linha. */
    private class MapaColunas(cabecalho: List<String>) {
        private val indices = cabecalho
            .mapIndexed { i, nome -> nome.trim().trim('"').lowercase() to i }
            .toMap()

        private fun indice(vararg apelidos: String): Int? =
            apelidos.firstNotNullOfOrNull { indices[it.lowercase()] }

        fun texto(campos: List<String>, vararg apelidos: String): String =
            indice(*apelidos)?.let { campos.getOrNull(it) }?.trim()?.trim('"') ?: ""

        fun num(campos: List<String>, vararg apelidos: String): Int {
            val bruto = texto(campos, *apelidos)
            // Aceita "85", "85+2", "180cm", "72kg"
            return bruto.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

        fun numLongo(campos: List<String>, vararg apelidos: String): Long {
            val bruto = texto(campos, *apelidos).replace(Regex("[^0-9.KMkm]"), "")
            val multiplicador = when {
                bruto.endsWith("M", true) -> 1_000_000
                bruto.endsWith("K", true) -> 1_000
                else -> 1
            }
            val numero = bruto.dropLastWhile { !it.isDigit() && it != '.' }
                .toDoubleOrNull() ?: 0.0
            return (numero * multiplicador).roundToLong()
        }
    }

    /** Divide respeitando aspas — nomes de clube costumam ter vírgula. */
    private fun dividirLinha(linha: String): List<String> {
        val campos = mutableListOf<String>()
        val atual = StringBuilder()
        var dentroDeAspas = false

        linha.forEach { c ->
            when {
                c == '"' -> dentroDeAspas = !dentroDeAspas
                c == ',' && !dentroDeAspas -> {
                    campos += atual.toString(); atual.clear()
                }
                else -> atual.append(c)
            }
        }
        campos += atual.toString()
        return campos
    }

    // --------------------------------------------------- CONTRATOS

    private fun gerarContratos(jogadores: List<Jogador>): List<Contrato> {
        val rng = Random(42)   // semente fixa: mesma carreira toda vez
        return jogadores.filter { it.clubeId != null }.map { j ->
            Contrato(
                jogadorId = j.id,
                clubeId = j.clubeId!!,
                salarioSemanalEur = j.salarioEur.coerceAtLeast(500),
                terminaEmTemporada = rng.nextInt(1, 5),
                clausulaRescisaoEur = (j.valorEur * 1.8).roundToLong(),
                moral = rng.nextInt(55, 90),
            )
        }
    }
}
