package com.exemplo.fmanager.formacao

/*
 * MODELO BASE DA FORMAÇÃO.
 *
 * Sistema de coordenadas usado em todo o projeto:
 *   x = 0.0 (lateral esquerda)   ->  1.0 (lateral direita)
 *   y = 0.0 (sua linha de fundo) ->  1.0 (linha de fundo adversária)
 */

enum class Papel(val sigla: String) {
    GOL("GOL"), ZAG("ZAG"), LE("LE"), LD("LD"),
    VOL("VOL"), MC("MC"), MEI("MEI"), ME("ME"), MD("MD"),
    PE("PE"), PD("PD"), ATA("ATA")
}

enum class Movimentacao { FICA_NA_POSICAO, EQUILIBRADO, ATACA_ESPACO, CORTA_PRA_DENTRO }
enum class ApoioDefensivo { NAO_RECUA, EQUILIBRADO, RECUA_SEMPRE }
enum class Marcacao { POR_ZONA, POR_HOMEM }

data class Instrucoes(
    val movimentacao: Movimentacao = Movimentacao.EQUILIBRADO,
    val apoio: ApoioDefensivo = ApoioDefensivo.EQUILIBRADO,
    val pressao: Int = 50,
    val amplitude: Int = 50,
    val marcacao: Marcacao = Marcacao.POR_ZONA,
)

/**
 * Define o papel a partir da zona do campo. Função pura, sem estado:
 * roda a cada frame do arrasto sem custo.
 */
fun papelPorZona(x: Float, y: Float): Papel {
    val naEsquerda = x < 0.22f
    val naDireita = x > 0.78f

    return when {
        y < 0.12f -> Papel.GOL

        y < 0.33f -> when {
            naEsquerda -> Papel.LE
            naDireita -> Papel.LD
            else -> Papel.ZAG
        }

        y < 0.46f -> when {
            naEsquerda -> Papel.ME
            naDireita -> Papel.MD
            else -> Papel.VOL
        }

        y < 0.62f -> when {
            naEsquerda -> Papel.ME
            naDireita -> Papel.MD
            else -> Papel.MC
        }

        y < 0.78f -> when {
            naEsquerda -> Papel.ME
            naDireita -> Papel.MD
            else -> Papel.MEI
        }

        else -> when {
            x < 0.25f -> Papel.PE
            x > 0.75f -> Papel.PD
            else -> Papel.ATA
        }
    }
}
