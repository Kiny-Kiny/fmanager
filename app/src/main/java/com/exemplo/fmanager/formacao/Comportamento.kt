package com.exemplo.fmanager.formacao

import kotlin.math.sign

/*
 * COMPORTAMENTO DE FUNÇÃO.
 *
 * Aqui está a correção de um erro de design meu: antes as fases eram
 * desenhadas à mão em dois presets — e um deles era o exemplo pessoal de
 * um usuário, promovido a padrão do jogo. Errado.
 *
 * Agora funciona como no Football Manager: cada jogador recebe um
 * COMPORTAMENTO, e a posição dele com a bola é CALCULADA a partir da
 * posição base mais o deslocamento daquele comportamento. Qualquer
 * formação ganha movimento coerente sem ninguém autorar fase por fase.
 *
 * A posição base é a de SEM_POSSE — a organização defensiva, onde o time
 * passa mais tempo estruturado. Com a bola, aplica o deslocamento.
 * Na transição, metade dele.
 */

enum class Comportamento(
    val rotulo: String,
    val descricao: String,
    /** Deslocamento no eixo do ataque. Positivo sobe. */
    internal val dy: Float,
    /** Deslocamento lateral. Positivo vai para o centro, negativo abre. */
    internal val dxCentro: Float,
) {
    // ------------------------------------------------------- GERAL
    FIXO("Mantém a posição",
        "Não abandona a função. Referência para o resto do time.",
        0f, 0f),

    SEGURA("Segura atrás",
        "Recua um pouco mesmo com a bola, para cobrir contra-ataque.",
        -0.05f, 0.03f),

    // ---------------------------------------------------- LATERAIS
    SOBE_ALA("Sobe como ala",
        "Faz o corredor inteiro e sustenta a largura no ataque.",
        0.30f, -0.06f),

    LATERAL_INVERTIDO("Entra no meio",
        "Com a bola abandona a linha de fundo e vira meio-campista.",
        0.16f, 0.22f),

    LATERAL_CONTIDO("Fica na linha de quatro",
        "Sobe pouco. Prioriza o duelo e a cobertura.",
        0.05f, 0.02f),

    // --------------------------------------------------- ZAGUEIROS
    SAI_JOGANDO("Sai jogando",
        "Avança com a bola no pé para ganhar a linha de passe.",
        0.10f, 0f),

    // ----------------------------------------------------- VOLANTES
    CAI_ENTRE_ZAGUEIROS("Cai entre os zagueiros",
        "Desce para formar o terceiro zagueiro na construção.",
        -0.20f, 0.10f),

    PIVO("Fica de pivô",
        "Ancora o meio à frente da defesa e distribui.",
        0.04f, 0.06f),

    CHEGA_NA_AREA("Chega na área",
        "Aparece na segunda bola e conclui de fora.",
        0.22f, 0.04f),

    // -------------------------------------------------------- MEIAS
    RECUA_ARMAR("Recua para armar",
        "Sai da linha de frente para buscar a bola e criar.",
        -0.16f, 0.02f),

    APOIA_ATAQUE("Apoia o ataque",
        "Cola no atacante e ataca o espaço entre as linhas.",
        0.16f, 0.02f),

    ABRE_O_JOGO("Abre o jogo",
        "Puxa para a linha para alargar o campo.",
        0.10f, -0.14f),

    // -------------------------------------------------------- PONTAS
    ESTREITA("Estreita para o centro",
        "Corta para dentro e joga como segundo atacante.",
        0.10f, 0.18f),

    COLA_NA_LINHA("Cola na linha",
        "Encara o lateral pelo lado e cruza.",
        0.08f, -0.05f),

    // ----------------------------------------------------- ATAQUE
    ATACA_PROFUNDIDADE("Ataca a profundidade",
        "Vive no limite do impedimento buscando as costas da zaga.",
        0.08f, 0f),

    FALSO_NOVE("Recua como falso 9",
        "Abandona a área para atrair o zagueiro e criar espaço.",
        -0.18f, 0.02f),

    // ----------------------------------------------------- GOLEIRO
    GOLEIRO_ADIANTADO("Goleiro adiantado",
        "Sobe a linha para dar opção de passe e limpar as costas.",
        0.06f, 0f),

    GOLEIRO_NA_LINHA("Goleiro na linha",
        "Não sai da área.",
        0f, 0f);

    /** Comportamentos que fazem sentido para cada função. */
    companion object {
        fun paraPapel(papel: Papel): List<Comportamento> = when (papel) {
            Papel.GOL -> listOf(GOLEIRO_NA_LINHA, GOLEIRO_ADIANTADO)
            Papel.ZAG -> listOf(FIXO, SAI_JOGANDO, SEGURA)
            Papel.LE, Papel.LD -> listOf(
                LATERAL_CONTIDO, SOBE_ALA, LATERAL_INVERTIDO, FIXO)
            Papel.VOL -> listOf(PIVO, CAI_ENTRE_ZAGUEIROS, SEGURA, CHEGA_NA_AREA)
            Papel.MC -> listOf(FIXO, CHEGA_NA_AREA, RECUA_ARMAR, PIVO, ABRE_O_JOGO)
            Papel.MEI -> listOf(APOIA_ATAQUE, RECUA_ARMAR, CHEGA_NA_AREA, FIXO)
            Papel.ME, Papel.MD -> listOf(
                COLA_NA_LINHA, SOBE_ALA, ESTREITA, ABRE_O_JOGO, FIXO)
            Papel.PE, Papel.PD -> listOf(
                COLA_NA_LINHA, ESTREITA, ATACA_PROFUNDIDADE, FIXO)
            Papel.ATA -> listOf(
                ATACA_PROFUNDIDADE, FIXO, FALSO_NOVE, APOIA_ATAQUE)
        }

        /** Escolha razoável quando ninguém definiu nada. */
        fun padraoDe(papel: Papel): Comportamento = when (papel) {
            Papel.GOL -> GOLEIRO_NA_LINHA
            Papel.ZAG -> FIXO
            Papel.LE, Papel.LD -> LATERAL_CONTIDO
            Papel.VOL -> PIVO
            Papel.MC -> FIXO
            Papel.MEI -> APOIA_ATAQUE
            Papel.ME, Papel.MD -> COLA_NA_LINHA
            Papel.PE, Papel.PD -> COLA_NA_LINHA
            Papel.ATA -> ATACA_PROFUNDIDADE
        }
    }
}

/**
 * Calcula a posição de uma fase a partir da base e do comportamento.
 *
 * O deslocamento lateral é espelhado: "para o centro" significa direita
 * para quem está na esquerda e vice-versa. Sem isso, um lateral esquerdo
 * invertido sairia para fora do campo.
 */
internal fun deslocar(
    baseX: Float,
    baseY: Float,
    comportamento: Comportamento,
    intensidade: Float,
): Pair<Float, Float> {
    val ladoDoCentro = (0.5f - baseX).sign  // +1 se está na esquerda
    val dx = comportamento.dxCentro * intensidade * ladoDoCentro
    val dy = comportamento.dy * intensidade
    return (baseX + dx).coerceIn(0.05f, 0.95f) to
            (baseY + dy).coerceIn(0.03f, 0.97f)
}
