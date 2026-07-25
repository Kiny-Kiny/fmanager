package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Contrato
import com.exemplo.fmanager.dados.Jogador

/*
 * MORAL — consertando um esquecimento meu.
 *
 * O campo `moral` existe em Contrato desde o começo do projeto e NUNCA foi
 * lido por nada. Criei e esqueci. Um número guardado que não afeta nada é
 * pior que não ter o campo: dá a impressão de sistema onde não há.
 *
 * Agora a moral faz três coisas concretas:
 *   1. multiplica o rendimento em campo
 *   2. decide se o jogador aceita renovar contrato
 *   3. gera insatisfação que chega na caixa de entrada
 *
 * E ela se move pelo que acontece: minutos jogados, resultados, conversas,
 * promessas cumpridas ou quebradas.
 */

enum class EstadoMoral(val rotulo: String, val faixa: IntRange) {
    REVOLTADO("Revoltado", 0..19),
    INSATISFEITO("Insatisfeito", 20..39),
    INDIFERENTE("Indiferente", 40..59),
    CONTENTE("Contente", 60..79),
    EUFORICO("Eufórico", 80..100);

    companion object {
        fun de(valor: Int) = entries.firstOrNull { valor in it.faixa } ?: INDIFERENTE
    }
}

object Moral {

    /**
     * Multiplicador de rendimento pela moral.
     *
     * Faixa estreita de propósito — de 0,92 a 1,06. Moral importa, mas um
     * craque revoltado ainda é melhor que um reserva animado. Sistemas que
     * deixam a moral dominar acabam premiando micro-gestão em vez de
     * decisão tática.
     */
    fun multiplicador(moral: Int): Float =
        (0.92f + (moral / 100f) * 0.14f).coerceIn(0.92f, 1.06f)

    /**
     * Como a moral se move numa semana.
     *
     * O peso maior é MINUTOS. Jogador que não joga fica insatisfeito
     * independente de quanto o time ganha — é a reclamação número um do
     * futebol de verdade.
     */
    fun evoluirSemana(
        moralAtual: Int,
        minutosNaSemana: Int,
        vitoriasRecentes: Int,
        derrotasRecentes: Int,
        idade: Int,
        geral: Int,
        melhorDoElenco: Boolean,
    ): Int {
        var m = moralAtual.toFloat()

        // Minutos: o que mais pesa.
        m += when {
            minutosNaSemana >= 80 -> 3.5f
            minutosNaSemana >= 45 -> 1.5f
            minutosNaSemana >= 10 -> -0.5f
            else -> -3.0f
        }

        // Resultados do time.
        m += vitoriasRecentes * 1.6f - derrotasRecentes * 1.8f

        // Craque no banco fica muito mais irritado que um reserva.
        if (melhorDoElenco && minutosNaSemana < 45) m -= 2.5f

        // Veterano sem jogar aceita menos; jovem tem mais paciência.
        if (idade >= 31 && minutosNaSemana < 30) m -= 1.2f
        if (idade <= 21) m += 0.4f

        // Reserva de overall alto num elenco fraco reclama mais.
        if (geral >= 78 && minutosNaSemana < 30) m -= 1.0f

        // Tende devagar para o meio: ninguém fica eufórico para sempre.
        m += (55f - m) * 0.04f

        return m.toInt().coerceIn(0, 100)
    }

    /** Ele aceitaria renovar? Moral baixa fecha a porta. */
    fun aceitaRenovar(moral: Int, aumentoOferecido: Float): Boolean = when {
        moral >= 70 -> aumentoOferecido >= 1.0f
        moral >= 50 -> aumentoOferecido >= 1.15f
        moral >= 30 -> aumentoOferecido >= 1.40f
        else -> false      // revoltado não renova por dinheiro nenhum
    }

    /** Quem está criando caso no vestiário. */
    fun insatisfeitos(
        elenco: List<Jogador>,
        contratos: Map<Int, Contrato>,
    ): List<Pair<Jogador, EstadoMoral>> = elenco
        .mapNotNull { j ->
            val c = contratos[j.id] ?: return@mapNotNull null
            val estado = EstadoMoral.de(c.moral)
            if (estado == EstadoMoral.REVOLTADO || estado == EstadoMoral.INSATISFEITO)
                j to estado else null
        }
        .sortedByDescending { it.first.geral }

    /**
     * Clima do vestiário: a média ponderada pelo overall.
     *
     * Ponderada de propósito — o titular insatisfeito contamina mais que o
     * décimo reserva, e é isso que faz a gestão do craque importar.
     */
    fun climaDoVestiario(
        elenco: List<Jogador>,
        contratos: Map<Int, Contrato>,
    ): Int {
        if (elenco.isEmpty()) return 55
        var soma = 0.0
        var pesos = 0.0
        elenco.forEach { j ->
            val c = contratos[j.id] ?: return@forEach
            val peso = (j.geral / 100.0).pow(3.0)
            soma += c.moral * peso
            pesos += peso
        }
        return if (pesos == 0.0) 55 else (soma / pesos).toInt().coerceIn(0, 100)
    }

    private fun Double.pow(e: Double) = Math.pow(this, e)
}
