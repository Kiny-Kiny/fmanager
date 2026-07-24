package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.LinhaTabela
import com.exemplo.fmanager.dados.Partida

/*
 * TEMPORADA — calendário e classificação.
 */

object Temporada {

    /**
     * Gera todos os jogos de pontos corridos, turno e returno,
     * pelo método do rodízio (algoritmo do círculo).
     *
     * Com N times dá N-1 rodadas por turno. Se N for ímpar, entra um
     * time fantasma e quem enfrenta ele folga naquela rodada.
     */
    fun gerarCalendario(
        clubes: List<Clube>,
        ligaId: Int,
        temporada: Int,
    ): List<Partida> {
        if (clubes.size < 2) return emptyList()

        val ids = clubes.map { it.id }.toMutableList()
        if (ids.size % 2 != 0) ids.add(-1)   // folga

        val n = ids.size
        val rodadasPorTurno = n - 1
        val partidas = mutableListOf<Partida>()

        val fixo = ids[0]
        val rotativo = ids.drop(1).toMutableList()

        repeat(rodadasPorTurno) { r ->
            val rodada = mutableListOf<Pair<Int, Int>>()

            // O time fixo joga contra o primeiro da lista rotativa.
            rodada += if (r % 2 == 0) fixo to rotativo[0] else rotativo[0] to fixo

            // O resto se enfrenta em pares das pontas para o meio.
            for (i in 1 until n / 2) {
                val a = rotativo[i]
                val b = rotativo[rotativo.size - i]
                rodada += if ((r + i) % 2 == 0) a to b else b to a
            }

            rodada.filter { it.first != -1 && it.second != -1 }
                .forEach { (casa, fora) ->
                    // Turno
                    partidas += Partida(
                        temporada = temporada, rodada = r + 1, ligaId = ligaId,
                        mandanteId = casa, visitanteId = fora,
                    )
                    // Returno, com o mando invertido
                    partidas += Partida(
                        temporada = temporada, rodada = r + 1 + rodadasPorTurno,
                        ligaId = ligaId, mandanteId = fora, visitanteId = casa,
                    )
                }

            // Rotaciona para a próxima rodada.
            rotativo.add(0, rotativo.removeAt(rotativo.size - 1))
        }

        return partidas.sortedBy { it.rodada }
    }

    /**
     * Monta a classificação a partir das partidas já jogadas.
     * Critérios: pontos, saldo, gols pró, nome.
     */
    fun classificacao(
        clubes: List<Clube>,
        partidas: List<Partida>,
    ): List<LinhaTabela> {
        val mapa = clubes.associate { it.id to Acumulador(it.nome) }

        partidas.forEach { p ->
            val gm = p.golsMandante ?: return@forEach
            val gv = p.golsVisitante ?: return@forEach

            mapa[p.mandanteId]?.registrar(gm, gv)
            mapa[p.visitanteId]?.registrar(gv, gm)
        }

        return mapa.map { (id, a) ->
            LinhaTabela(
                clubeId = id, nome = a.nome, jogos = a.jogos,
                vitorias = a.v, empates = a.e, derrotas = a.d,
                golsPro = a.gp, golsContra = a.gc,
            )
        }.sortedWith(
            compareByDescending<LinhaTabela> { it.pontos }
                .thenByDescending { it.saldo }
                .thenByDescending { it.golsPro }
                .thenBy { it.nome }
        )
    }

    private class Acumulador(val nome: String) {
        var jogos = 0; var v = 0; var e = 0; var d = 0
        var gp = 0; var gc = 0

        fun registrar(pro: Int, contra: Int) {
            jogos++; gp += pro; gc += contra
            when {
                pro > contra -> v++
                pro == contra -> e++
                else -> d++
            }
        }
    }
}
