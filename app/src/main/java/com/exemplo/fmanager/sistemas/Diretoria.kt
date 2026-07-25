package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.LinhaTabela

/*
 * DIRETORIA.
 *
 * O que a diretoria espera sai da reputação do clube em relação à liga.
 * Assumir o lanterna e terminar no meio da tabela é sucesso; assumir o
 * favorito e terminar em quinto é fracasso. É essa relação que dá sentido
 * a começar pequeno.
 */

enum class Situacao(val rotulo: String) {
    SEGURO("Trabalho aprovado"),
    ESTAVEL("Sob observação"),
    PRESSIONADO("Pressionado"),
    AMEACADO("Demissão iminente"),
}

data class Objetivo(
    val descricao: String,
    val cumprido: Boolean,
    val progresso: String,
)

data class Expectativa(
    val posicaoAlvo: Int,
    val totalClubes: Int,
    val faseCopaAlvo: String,
    val objetivos: List<Objetivo>,
    val situacao: Situacao,
    val confianca: Int,          // 0..100
) {
    val resumo: String get() = when (situacao) {
        Situacao.SEGURO -> "A diretoria está satisfeita com o trabalho."
        Situacao.ESTAVEL -> "A diretoria acompanha de perto."
        Situacao.PRESSIONADO -> "A diretoria esperava mais nesta altura."
        Situacao.AMEACADO -> "Sua permanência está em risco."
    }
}

object Diretoria {

    /**
     * Posição que a diretoria considera aceitável.
     * O clube mais forte tem que brigar por título; o mais fraco só
     * precisa não cair.
     */
    fun posicaoEsperada(clube: Clube, todosDaLiga: List<Clube>): Int {
        if (todosDaLiga.isEmpty()) return 1
        val ordenados = todosDaLiga.sortedByDescending { it.reputacao }
        val ranking = ordenados.indexOfFirst { it.id == clube.id } + 1
        val n = ordenados.size

        // A meta é a própria força, com uma folga de duas posições.
        return (ranking + 2).coerceIn(1, n)
    }

    fun faseEsperadaNaCopa(clube: Clube, todosDaLiga: List<Clube>): String {
        val ordenados = todosDaLiga.sortedByDescending { it.reputacao }
        val ranking = ordenados.indexOfFirst { it.id == clube.id } + 1
        val n = ordenados.size.coerceAtLeast(1)
        return when {
            ranking <= n / 8 -> "Final"
            ranking <= n / 4 -> "Semifinal"
            ranking <= n / 2 -> "Quartas de final"
            else -> "Oitavas de final"
        }
    }

    fun avaliar(
        clube: Clube,
        todosDaLiga: List<Clube>,
        tabela: List<LinhaTabela>,
        rodadasJogadas: Int,
        totalRodadas: Int,
        viveNaCopa: Boolean,
    ): Expectativa {
        val alvo = posicaoEsperada(clube, todosDaLiga)
        val faseAlvo = faseEsperadaNaCopa(clube, todosDaLiga)
        val posicaoAtual = tabela.indexOfFirst { it.clubeId == clube.id } + 1
        val minhaLinha = tabela.firstOrNull { it.clubeId == clube.id }

        val objetivos = buildList {
            add(Objetivo(
                descricao = "Terminar entre os $alvo primeiros",
                cumprido = posicaoAtual in 1..alvo,
                progresso = if (posicaoAtual > 0) "${posicaoAtual}º de ${tabela.size}"
                else "sem jogos",
            ))
            add(Objetivo(
                descricao = "Chegar às $faseAlvo na copa",
                cumprido = viveNaCopa,
                progresso = if (viveNaCopa) "ainda na disputa" else "eliminado",
            ))
            minhaLinha?.let { l ->
                val media = if (l.jogos == 0) 0.0 else l.pontos.toDouble() / l.jogos
                add(Objetivo(
                    descricao = "Manter média acima de 1,4 ponto por jogo",
                    cumprido = media >= 1.4,
                    progresso = "%.2f por jogo".format(media),
                ))
            }
        }

        // A confiança pesa a diferença entre onde você está e onde
        // deveria estar, e amadurece conforme a temporada avança.
        val diferenca = if (posicaoAtual == 0) 0 else alvo - posicaoAtual
        val maturidade = if (totalRodadas == 0) 0f
        else (rodadasJogadas.toFloat() / totalRodadas).coerceIn(0f, 1f)

        val confianca = (58 + diferenca * 6 * (0.4f + maturidade))
            .toInt().coerceIn(0, 100)

        val situacao = when {
            confianca >= 70 -> Situacao.SEGURO
            confianca >= 48 -> Situacao.ESTAVEL
            confianca >= 26 -> Situacao.PRESSIONADO
            else -> Situacao.AMEACADO
        }

        return Expectativa(
            posicaoAlvo = alvo,
            totalClubes = tabela.size,
            faseCopaAlvo = faseAlvo,
            objetivos = objetivos,
            situacao = situacao,
            confianca = confianca,
        )
    }
}
