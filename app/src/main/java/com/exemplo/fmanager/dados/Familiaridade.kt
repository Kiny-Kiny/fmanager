package com.exemplo.fmanager.dados

import com.exemplo.fmanager.formacao.Papel

/*
 * FAMILIARIDADE POSICIONAL.
 *
 * Você tem liberdade total para escalar qualquer um em qualquer lugar.
 * Mas o jogador rende conforme a familiaridade dele com aquela função.
 *
 * Três camadas se somam:
 *   1. ATRIBUTOS  — ele tem as qualidades que o papel exige? (adequacao)
 *   2. FAMILIARIDADE — ele já joga ali, ou é gambiarra? (esta arquivo)
 *   3. ENTROSAMENTO — ele se entende com os vizinhos? (Entrosamento.kt)
 *
 * Um zagueiro rápido e bom de passe tem ATRIBUTOS de lateral, mas se
 * nunca jogou ali, a familiaridade derruba o rendimento. Já um jogador
 * que o dataset lista como versátil sofre bem menos.
 */

enum class NivelFamiliaridade(val rotulo: String, val fator: Float) {
    NATURAL("Natural", 1.00f),
    ALTERNATIVA("Alternativa", 0.94f),
    APRENDIVEL("Adaptável", 0.85f),
    IMPROVISO("Improviso", 0.72f),
    DESASTRE("Fora de função", 0.55f),
}

/** Como a sigla do dataset (CB, LB, CDM...) mapeia para o nosso Papel. */
private val SIGLA_PARA_PAPEL: Map<String, Papel> = mapOf(
    "GK" to Papel.GOL,
    "CB" to Papel.ZAG, "LCB" to Papel.ZAG, "RCB" to Papel.ZAG,
    "LB" to Papel.LE, "LWB" to Papel.LE,
    "RB" to Papel.LD, "RWB" to Papel.LD,
    "CDM" to Papel.VOL, "DM" to Papel.VOL,
    "CM" to Papel.MC, "LCM" to Papel.MC, "RCM" to Papel.MC,
    "CAM" to Papel.MEI, "AM" to Papel.MEI,
    "LM" to Papel.ME, "RM" to Papel.MD,
    "LW" to Papel.PE, "RW" to Papel.PD,
    "LF" to Papel.PE, "RF" to Papel.PD,
    "ST" to Papel.ATA, "CF" to Papel.ATA,
)

/**
 * Posições vizinhas — quem consegue aprender o que.
 * Um volante vira zagueiro com relativa facilidade; um centroavante não.
 */
private val PARENTESCO: Map<Papel, Set<Papel>> = mapOf(
    Papel.GOL to emptySet(),
    Papel.ZAG to setOf(Papel.VOL, Papel.LE, Papel.LD),
    Papel.LE to setOf(Papel.ME, Papel.ZAG, Papel.LD),
    Papel.LD to setOf(Papel.MD, Papel.ZAG, Papel.LE),
    Papel.VOL to setOf(Papel.MC, Papel.ZAG),
    Papel.MC to setOf(Papel.VOL, Papel.MEI, Papel.ME, Papel.MD),
    Papel.MEI to setOf(Papel.MC, Papel.PE, Papel.PD, Papel.ATA),
    Papel.ME to setOf(Papel.PE, Papel.LE, Papel.MC),
    Papel.MD to setOf(Papel.PD, Papel.LD, Papel.MC),
    Papel.PE to setOf(Papel.ME, Papel.ATA, Papel.MEI),
    Papel.PD to setOf(Papel.MD, Papel.ATA, Papel.MEI),
    Papel.ATA to setOf(Papel.PE, Papel.PD, Papel.MEI),
)

/** As posições que o dataset diz que ele joga. */
fun Jogador.papeisConhecidos(): Set<Papel> = buildSet {
    SIGLA_PARA_PAPEL[posicao.trim().uppercase()]?.let { add(it) }
    posicoesAlt.split(",", ";", "|").forEach { sigla ->
        SIGLA_PARA_PAPEL[sigla.trim().uppercase()]?.let { add(it) }
    }
}

fun Jogador.familiaridade(papel: Papel): NivelFamiliaridade {
    val natural = SIGLA_PARA_PAPEL[posicao.trim().uppercase()]
    val conhecidos = papeisConhecidos()

    // Goleiro é caso à parte: ninguém improvisa no gol, e goleiro não
    // vira jogador de linha.
    if (papel == Papel.GOL || natural == Papel.GOL) {
        return if (papel == natural) NivelFamiliaridade.NATURAL
        else NivelFamiliaridade.DESASTRE
    }

    return when {
        papel == natural -> NivelFamiliaridade.NATURAL
        papel in conhecidos -> NivelFamiliaridade.ALTERNATIVA
        conhecidos.any { papel in PARENTESCO.getValue(it) } ->
            NivelFamiliaridade.APRENDIVEL
        // Jogador versátil (muitas estrelas de drible, bom sangue frio)
        // se vira melhor em terreno estranho.
        estrelasDrible >= 4 || sangueFrio >= 80 -> NivelFamiliaridade.IMPROVISO
        else -> NivelFamiliaridade.DESASTRE
    }
}

/**
 * Rendimento efetivo: junta os atributos com a familiaridade.
 * É este número, de 0 a 100, que o motor de partida usa.
 */
fun Jogador.rendimentoEm(papel: Papel): Int =
    (adequacao(papel) * familiaridade(papel).fator).toInt().coerceIn(0, 100)

/** Overall aparente do jogador naquela função — o número que aparece
 *  na tela quando você o arrasta para uma posição estranha. */
fun Jogador.geralEm(papel: Papel): Int =
    (geral * familiaridade(papel).fator).toInt().coerceIn(1, 99)
