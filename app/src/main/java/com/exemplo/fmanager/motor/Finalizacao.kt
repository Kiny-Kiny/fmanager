package com.exemplo.fmanager.motor

import kotlin.math.abs

/*
 * MODELO DE FINALIZAÇÃO — BASEADO EM xG.
 *
 * O modelo anterior estava conceitualmente errado. Ele calculava a chance
 * de gol como "qualidade do atacante contra qualidade do goleiro", e isso
 * dava 41% de conversão por chute. O futebol real converte 10-11%.
 *
 * No futebol de verdade quem manda é a POSIÇÃO. Um chute da entrada da
 * área vale ~5% para qualquer um — Haaland ou um zagueiro. Um chute de
 * dentro da pequena área vale ~30% para qualquer um. O finalizador move
 * esse número em ±30%, não em 400%.
 *
 * Então a estrutura correta é:
 *
 *   probabilidade = xG_da_posição × fatorFinalizador × fatorGoleiro
 *                                × fatorPressão × fatorMinuto
 *
 * O xG ancora. Os fatores temperam. É assim que os modelos de verdade
 * funcionam, e é o que faz o placar sair no lugar.
 *
 * Alvos de calibração (média por time por partida):
 *   finalizações  12–14
 *   no gol        4–5
 *   gols          1,3–1,5
 *   conversão     ~10%
 */
object Finalizacao {

    /**
     * xG cru da posição, antes de qualquer coisa sobre quem chuta.
     *
     * Os patamares seguem as faixas de distância reais: fora da área,
     * entrada da área, dentro da área, pequena área. O termo central
     * penaliza o ângulo fechado — chute da linha de fundo é quase nada,
     * mesmo colado no gol.
     */
    fun xG(avanco: Float, lateral: Float): Float {
        val central = 1f - abs(lateral - 0.5f) * 2f

        val base = when {
            avanco >= 0.93f -> 0.32f    // pequena área
            avanco >= 0.87f -> 0.145f   // dentro da área, perto
            avanco >= 0.81f -> 0.085f   // dentro da área
            avanco >= 0.73f -> 0.042f   // entrada da área
            avanco >= 0.65f -> 0.022f   // fora da área
            else -> 0.010f              // chutão de longe
        }

        // Ângulo: no miolo vale o dobro do que na quina.
        return base * (0.40f + central * 0.95f)
    }

    /**
     * O finalizador. Faixa estreita de propósito: entre um atacante de
     * 50 e um de 95 de finalização a diferença é de cerca de 30% na
     * conversão, não de 4 vezes. Craque erra chance fácil também.
     */
    fun fatorFinalizador(
        finalizacao: Int,
        sangueFrio: Int,
        eficienciaNaPosicao: Float,
        gas: Float,
        bonusTracos: Float,
    ): Float {
        val habilidade = (finalizacao * 0.72f + sangueFrio * 0.28f) / 100f
        return ((0.70f + habilidade * 0.68f) *
                (0.86f + eficienciaNaPosicao * 0.14f) *
                (0.88f + gas * 0.12f) *
                bonusTracos.coerceIn(0.9f, 1.2f))
            .coerceIn(0.55f, 1.55f)
    }

    /** O goleiro. Também faixa estreita: um bom goleiro salva ~20% mais. */
    fun fatorGoleiro(qualidadeGoleiro: Float): Float =
        (1.32f - (qualidadeGoleiro / 100f) * 0.58f).coerceIn(0.70f, 1.35f)

    /** Chutar pressionado é bem pior. Vale muito mais dar o passe. */
    fun fatorPressao(pressao: Float): Float =
        (1f - pressao * 0.42f).coerceIn(0.50f, 1f)

    /**
     * Probabilidade final. O teto de 0.55 existia antes e permitia
     * absurdos; agora o teto real vem do próprio xG.
     */
    fun probabilidade(
        xG: Float,
        finalizador: Float,
        goleiro: Float,
        pressao: Float,
        minuto: Float,
    ): Float = (xG * finalizador * goleiro * pressao * minuto)
        .coerceIn(0.004f, 0.46f)

    /**
     * Decide se o chute foi no gol, para fora, na trave ou bloqueado.
     *
     * Proporção calibrada pelo real: cerca de um terço das finalizações
     * vai no gol, e a maioria das que vão são defendidas.
     */
    fun desfecho(
        finalizacao: Int,
        pressao: Float,
        sorteio: Float,
    ): Lance.Desfecho {
        // Sob pressão, mais bloqueio e mais chute perdido.
        val chanceNoAlvo = (0.20f + (finalizacao / 100f) * 0.26f) *
                (1f - pressao * 0.35f)
        return when {
            sorteio < chanceNoAlvo -> Lance.Desfecho.DEFENDIDO
            sorteio < chanceNoAlvo + 0.16f -> Lance.Desfecho.BLOQUEADO
            sorteio < chanceNoAlvo + 0.20f -> Lance.Desfecho.NA_TRAVE
            else -> Lance.Desfecho.PARA_FORA
        }
    }

    /**
     * PESO DA DECISÃO DE CHUTAR.
     *
     * O outro erro grande: 48% dos lances em zona adiantada viravam
     * chute. Time nenhum finaliza a cada dois toques no campo de ataque.
     *
     * Agora o peso é calibrado para dar 12–14 finalizações por jogo, e
     * depende de três coisas que um jogador de verdade considera: estou
     * perto? estou livre? tenho ângulo?
     */
    fun pesoDeChutar(
        avanco: Float,
        lateral: Float,
        finalizacao: Int,
        pressao: Float,
        liberdadeCriativa: Int,
        pesoDoEstilo: Float,
    ): Float {
        if (avanco < 0.60f) return 0.004f    // de tão longe, quase nunca

        val central = 1f - abs(lateral - 0.5f) * 2f
        val proximidade = ((avanco - 0.58f) / 0.42f).coerceIn(0f, 1f)

        // Cresce ao quadrado com a proximidade: na pequena área quase
        // todo mundo chuta, na entrada da área quase ninguém.
        // 0.70 saiu de uma varredura contra os números reais: dá ~13,7
        // finalizações por time. Valores acima de 0,8 estouram para 16+.
        return (proximidade * proximidade * 0.70f) *
                (0.35f + central * 0.85f) *
                (0.55f + (finalizacao / 100f) * 0.75f) *
                (1f - pressao * 0.45f) *
                pesoDoEstilo *
                (1f + liberdadeCriativa / 400f)
    }
}
