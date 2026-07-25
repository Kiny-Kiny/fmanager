package com.exemplo.fmanager.motor

import com.exemplo.fmanager.formacao.ApoioDefensivo
import com.exemplo.fmanager.formacao.Fase
import com.exemplo.fmanager.formacao.Papel
import kotlin.math.abs
import kotlin.math.pow

/*
 * FORÇAS POR SETOR — extraído para ser compartilhado.
 *
 * O motor instantâneo e o motor ao vivo usam exatamente a mesma conta,
 * então uma partida assistida e uma simulada dão resultados coerentes.
 *
 * O ponto central: cada setor é calculado com as posições da FASE
 * correspondente. Defesa vem de "sem a bola", ataque de "com a bola",
 * meio da transição. É isso que faz a sua 4-2-3-1 que vira 3-2-5
 * realmente defender como uma e atacar como a outra.
 */
internal class Forcas(time: TimeEmCampo, mandoDeCampo: Boolean) {

    val defesa: Float
    val meio: Float
    val ataque: Float
    val tatica = time.tatica
    val goleiro = time.escalacao
        .firstOrNull { it.slot.em(Fase.SEM_POSSE).papel == Papel.GOL }

    companion object {
        const val VANTAGEM_CASA = 1.06f
    }

    init {
        var d = 0f; var m = 0f; var a = 0f

        time.escalacao.forEach { jc ->
            val j = jc.jogador

            val posDef = jc.slot.em(Fase.SEM_POSSE)
            if (posDef.papel != Papel.GOL) {
                val peso = (1f - posDef.y).pow(1.5f)
                val valor = (j.consciencaDef + j.rouboBola +
                        j.interceptacao + j.contatoFisico) / 4f
                val apoio = when (jc.slot.instrucoes.apoio) {
                    ApoioDefensivo.RECUA_SEMPRE -> 1.2f
                    ApoioDefensivo.NAO_RECUA -> 0.6f
                    ApoioDefensivo.EQUILIBRADO -> 1f
                }
                d += valor * peso * jc.eficiencia(Fase.SEM_POSSE) *
                        jc.mod.contribuicaoDefensiva * apoio * jc.tracos.defesa
            }

            val posAtq = jc.slot.em(Fase.COM_POSSE)
            if (posAtq.papel != Papel.GOL) {
                val peso = posAtq.y.pow(1.5f)
                val valor = (j.finalizacao + j.posicionamento +
                        j.drible + j.velocidade) / 4f
                a += valor * peso * jc.eficiencia(Fase.COM_POSSE) * jc.tracos.drible
            }

            val posMei = jc.slot.em(Fase.TRANSICAO)
            if (posMei.papel != Papel.GOL) {
                val peso = (1f - abs(posMei.y - 0.5f) * 2f).coerceAtLeast(0f)
                val valor = (j.passeBaixo + j.visao +
                        j.controleBola + j.resistencia) / 4f
                m += valor * peso * jc.eficiencia(Fase.TRANSICAO) * jc.tracos.criacao
            }
        }

        val pressao = 1f + (tatica.intensidadePressao - 50) / 250f
        val linha = 1f + (tatica.alturaLinha - 50) / 300f
        val compacta = 1f + (tatica.compactacao - 50) / 300f
        val casa = if (mandoDeCampo) VANTAGEM_CASA else 1f

        defesa = d * compacta * (2f - linha) * casa
        meio = m * pressao * compacta * casa
        ataque = a * linha * (1f + tatica.riscoNoPasse / 300f) * casa
    }
}
