package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.Partida
import kotlin.math.log2
import kotlin.math.pow

/*
 * COPAS — competições de eliminatória.
 *
 * Diferente da liga, que é pontos corridos: aqui quem perde sai.
 * O chaveamento é gerado uma vez e as rodadas vão sendo preenchidas
 * conforme os vencedores aparecem.
 *
 * A rodada é numerada a partir de 1000 para não colidir com as rodadas
 * da liga na mesma tabela de partidas. Rodada 1001 = primeira fase.
 */

object Copa {

    const val BASE_RODADA = 1000

    /** Ids reservados para as competições de copa. */
    const val ID_COPA_NACIONAL = 9001
    const val ID_CONTINENTAL = 9002

    data class Config(
        val ligaId: Int,
        val nome: String,
        val participantes: Int,
    )

    /**
     * Monta a primeira fase. Só a primeira: as seguintes dependem de
     * quem passar, então são criadas depois por [proximaFase].
     *
     * Os participantes entram por reputação, e o chaveamento cruza
     * forte contra fraco (1 x último, 2 x penúltimo...) como num
     * sorteio com cabeças de chave.
     */
    fun primeiraFase(
        clubes: List<Clube>,
        copaId: Int,
        temporada: Int,
        maxParticipantes: Int = 32,
    ): List<Partida> {
        // Arredonda para a potência de 2 abaixo, para o chaveamento fechar.
        val cabem = 2.0.pow(log2(maxParticipantes.toDouble()).toInt()).toInt()
        val classificados = clubes
            .sortedByDescending { it.reputacao }
            .take(minOf(cabem, potenciaDeDoisAbaixo(clubes.size)))

        if (classificados.size < 2) return emptyList()

        val rodada = BASE_RODADA + 1
        val metade = classificados.size / 2

        return (0 until metade).map { i ->
            val forte = classificados[i]
            val fraco = classificados[classificados.size - 1 - i]
            Partida(
                temporada = temporada,
                rodada = rodada,
                ligaId = copaId,
                // O mais bem colocado joga em casa.
                mandanteId = forte.id,
                visitanteId = fraco.id,
            )
        }
    }

    /**
     * Gera a fase seguinte a partir dos vencedores da anterior.
     * Devolve lista vazia se a fase atual ainda não terminou, ou se
     * a copa já acabou (só sobrou um).
     */
    fun proximaFase(
        partidasDaFase: List<Partida>,
        copaId: Int,
        temporada: Int,
    ): List<Partida> {
        if (partidasDaFase.isEmpty()) return emptyList()
        if (partidasDaFase.any { it.golsMandante == null }) return emptyList()

        val vencedores = partidasDaFase.map { vencedorDe(it) }
        if (vencedores.size < 2) return emptyList()

        val rodada = partidasDaFase.first().rodada + 1
        return vencedores.chunked(2).mapNotNull { par ->
            if (par.size < 2) null
            else Partida(
                temporada = temporada,
                rodada = rodada,
                ligaId = copaId,
                mandanteId = par[0],
                visitanteId = par[1],
            )
        }
    }

    /** Em jogo único, empate é decidido nos pênaltis — aqui, pelo
     *  time da casa, que é a aproximação mais simples e sem viés
     *  estranho (mando de campo vale algo em disputa de pênaltis). */
    fun vencedorDe(p: Partida): Int {
        val gm = p.golsMandante ?: return p.mandanteId
        val gv = p.golsVisitante ?: return p.mandanteId
        return when {
            gm > gv -> p.mandanteId
            gv > gm -> p.visitanteId
            else -> p.mandanteId
        }
    }

    /** Nome da fase pelo número de confrontos restantes. */
    fun nomeDaFase(confrontos: Int): String = when (confrontos) {
        1 -> "Final"
        2 -> "Semifinal"
        4 -> "Quartas de final"
        8 -> "Oitavas de final"
        16 -> "Terceira fase"
        else -> "Fase preliminar"
    }

    /** O clube ainda está vivo na copa? */
    fun aindaNaCopa(clubeId: Int, partidas: List<Partida>): Boolean {
        val jogadas = partidas.filter {
            it.golsMandante != null &&
                    (it.mandanteId == clubeId || it.visitanteId == clubeId)
        }
        if (jogadas.isEmpty()) return partidas.any {
            it.mandanteId == clubeId || it.visitanteId == clubeId
        }
        return vencedorDe(jogadas.maxBy { it.rodada }) == clubeId
    }

    private fun potenciaDeDoisAbaixo(n: Int): Int {
        if (n < 2) return 0
        var p = 2
        while (p * 2 <= n) p *= 2
        return p
    }
}
