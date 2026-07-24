package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.Contrato
import com.exemplo.fmanager.dados.Jogador
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/*
 * SISTEMA DE CONTRATAÇÕES.
 *
 * O clube vendedor não aceita nem recusa no chute: ele calcula o preço
 * que considera justo e compara com a sua proposta. Coisas que mexem
 * no preço: idade, potencial ainda não atingido, tempo de contrato
 * restante e a diferença de reputação entre os dois clubes.
 */

enum class RespostaProposta { ACEITA, CONTRAPROPOSTA, RECUSADA, NAO_ESTA_A_VENDA }

data class Proposta(
    val jogadorId: Int,
    val clubeCompradorId: Int,
    val valorEur: Long,
    val salarioOferecidoEur: Long,
    val anosContrato: Int,
)

data class RespostaClube(
    val resposta: RespostaProposta,
    val valorExigidoEur: Long = 0,
    val salarioExigidoEur: Long = 0,
    val motivo: String = "",
)

object Transferencias {

    /**
     * Preço que o clube vendedor pede. Parte do valor de mercado do
     * dataset e aplica os multiplicadores da situação.
     */
    fun precoPedido(
        jogador: Jogador,
        contrato: Contrato?,
        clubeVendedor: Clube?,
        clubeComprador: Clube,
        temporadaAtual: Int,
    ): Long {
        var preco = jogador.valorEur.toDouble().coerceAtLeast(50_000.0)

        // Jovem com potencial custa caro; veterano desvaloriza.
        val margem = (jogador.potencial - jogador.geral).coerceAtLeast(0)
        preco *= when {
            jogador.idade <= 21 -> 1.6 + margem * 0.05
            jogador.idade <= 25 -> 1.3 + margem * 0.03
            jogador.idade <= 29 -> 1.0
            jogador.idade <= 32 -> 0.7
            else -> 0.45
        }

        // Contrato acabando derruba o preço. Ano final = quase de graça.
        val anosRestantes = (contrato?.terminaEmTemporada ?: temporadaAtual + 3) - temporadaAtual
        preco *= when {
            anosRestantes <= 0 -> 0.15
            anosRestantes == 1 -> 0.5
            anosRestantes == 2 -> 0.85
            else -> 1.0
        }

        // Clube grande cobra mais de clube pequeno, e vice-versa.
        if (clubeVendedor != null) {
            val diff = (clubeVendedor.reputacao - clubeComprador.reputacao) / 100.0
            preco *= (1.0 + diff * 0.35).coerceIn(0.75, 1.5)
        }

        return preco.roundToLong()
    }

    /** Salário que o jogador exige para assinar. */
    fun salarioExigido(jogador: Jogador, clubeComprador: Clube): Long {
        val base = (jogador.geral.toDouble() / 60.0).pow(4.2) * 2_500
        val porReputacao = 1.0 + (60 - clubeComprador.reputacao) / 180.0
        return (base * porReputacao).roundToLong().coerceAtLeast(1_000)
    }

    /** Avalia a proposta do jogador contra o que o clube pede. */
    fun avaliar(
        proposta: Proposta,
        jogador: Jogador,
        contrato: Contrato?,
        clubeVendedor: Clube?,
        clubeComprador: Clube,
        temporadaAtual: Int,
        rng: Random = Random.Default,
    ): RespostaClube {

        val pedido = precoPedido(
            jogador, contrato, clubeVendedor, clubeComprador, temporadaAtual,
        )
        val salarioPedido = salarioExigido(jogador, clubeComprador)

        // Craque de clube grande simplesmente não sai.
        if (clubeVendedor != null &&
            jogador.geral >= 85 &&
            clubeVendedor.reputacao > clubeComprador.reputacao + 25
        ) {
            return RespostaClube(
                RespostaProposta.NAO_ESTA_A_VENDA,
                motivo = "O clube considera o jogador inegociável.",
            )
        }

        if (proposta.salarioOferecidoEur < salarioPedido * 0.9) {
            return RespostaClube(
                RespostaProposta.CONTRAPROPOSTA,
                valorExigidoEur = pedido,
                salarioExigidoEur = salarioPedido,
                motivo = "O jogador quer salário maior.",
            )
        }

        val razao = proposta.valorEur.toDouble() / pedido
        // Uma pitada de aleatoriedade para o mercado não ser determinístico.
        val humor = 1.0 + (rng.nextDouble() - 0.5) * 0.12

        return when {
            razao * humor >= 1.0 -> RespostaClube(
                RespostaProposta.ACEITA,
                valorExigidoEur = proposta.valorEur,
                salarioExigidoEur = proposta.salarioOferecidoEur,
                motivo = "Proposta aceita.",
            )
            razao >= 0.75 -> RespostaClube(
                RespostaProposta.CONTRAPROPOSTA,
                valorExigidoEur = pedido,
                salarioExigidoEur = salarioPedido,
                motivo = "O clube quer mais.",
            )
            else -> RespostaClube(
                RespostaProposta.RECUSADA,
                valorExigidoEur = pedido,
                salarioExigidoEur = salarioPedido,
                motivo = "Proposta muito abaixo do valor pedido.",
            )
        }
    }

    /** Você tem caixa e espaço na folha para bancar isso? */
    fun podeBancar(
        clube: Clube,
        folhaAtualEur: Long,
        proposta: Proposta,
    ): Pair<Boolean, String> = when {
        proposta.valorEur > clube.caixaEur ->
            false to "Caixa insuficiente."
        folhaAtualEur + proposta.salarioOferecidoEur > clube.folhaMaxEur ->
            false to "Estoura o teto salarial."
        else -> true to "Dentro do orçamento."
    }
}
