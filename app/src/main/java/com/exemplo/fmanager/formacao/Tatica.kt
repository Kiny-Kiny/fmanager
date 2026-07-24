package com.exemplo.fmanager.formacao

/**
 * ESTILO DE JOGO DO TIME.
 *
 * Cada campo vai de 0 a 100 e entra direto no motor de simulação.
 * Junto com as instruções individuais de cada slot, é isso que forma
 * o "seu estilo" — não existe preset escondido por trás.
 */
data class Tatica(
    /** 0 = toque de bola paciente, 100 = vertical direto */
    val velocidadeConstrucao: Int = 50,

    /** 0 = linha recuada, 100 = linha alta e adiantada */
    val alturaLinha: Int = 50,

    /** 0 = espera o adversário, 100 = pressiona na saída de bola */
    val intensidadePressao: Int = 50,

    /** 0 = espalha pelos lados, 100 = concentra pelo meio */
    val compactacao: Int = 50,

    /** 0 = jogo apoiado, 100 = busca o contra-ataque sempre */
    val contraAtaque: Int = 30,

    /** 0 = evita o drible, 100 = incentiva o um contra um */
    val liberdadeCriativa: Int = 50,

    /** Quanto risco assumir no passe. Mais risco = mais chance criada
     *  e mais perda de posse. */
    val riscoNoPasse: Int = 50,
)

/** Presets como ponto de partida — você edita tudo depois. */
object Estilos {
    val equilibrado = Tatica()

    val posse = Tatica(
        velocidadeConstrucao = 25, alturaLinha = 65, intensidadePressao = 70,
        compactacao = 60, contraAtaque = 10, riscoNoPasse = 35,
    )

    val contraAtaque = Tatica(
        velocidadeConstrucao = 85, alturaLinha = 30, intensidadePressao = 30,
        compactacao = 70, contraAtaque = 90, riscoNoPasse = 65,
    )

    val pressaoAlta = Tatica(
        velocidadeConstrucao = 70, alturaLinha = 85, intensidadePressao = 90,
        compactacao = 65, contraAtaque = 40, riscoNoPasse = 60,
    )

    val retranca = Tatica(
        velocidadeConstrucao = 60, alturaLinha = 15, intensidadePressao = 20,
        compactacao = 85, contraAtaque = 70, riscoNoPasse = 30,
    )

    val todos = listOf(
        "Equilibrado" to equilibrado,
        "Posse de bola" to posse,
        "Contra-ataque" to contraAtaque,
        "Pressão alta" to pressaoAlta,
        "Retranca" to retranca,
    )
}
