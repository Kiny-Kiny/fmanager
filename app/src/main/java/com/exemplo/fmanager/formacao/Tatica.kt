package com.exemplo.fmanager.formacao

/**
 * ESTILO DE JOGO DO TIME.
 *
 * Cada campo vai de 0 a 100 e entra direto no motor de simulação.
 * Junto com as instruções individuais de cada slot, é isso que forma
 * o "seu estilo" — não existe preset escondido por trás.
 */
/*
 * INSTRUÇÕES DE EQUIPE — no modelo do Football Manager.
 *
 * Diferente dos sete controles contínuos: são interruptores que mudam
 * comportamentos específicos, e cada um tem um custo real. Linha de
 * impedimento rouba muitas bolas e toma gol nas costas. Cera protege o
 * resultado e cansa o time. Nada é grátis.
 */
enum class InstrucaoEquipe(val rotulo: String, val descricao: String) {
    LINHA_DE_IMPEDIMENTO("Linha de impedimento",
        "Rouba muitas bolas no impedimento, mas expõe as costas da zaga."),
    PRESSAO_APOS_PERDA("Pressão após perda",
        "Cinco segundos de caça à bola ao perder. Cansa muito."),
    EXPLORAR_OS_LADOS("Explorar os lados",
        "Concentra a construção pelos corredores e cruza mais."),
    JOGAR_PELO_MEIO("Jogar pelo meio",
        "Insiste no miolo. Mais chance clara, mais bola perdida."),
    BOLA_NO_HOMEM_ALVO("Bola no homem-alvo",
        "Prioriza o passe no atacante de referência."),
    FAZER_CERA("Fazer cera",
        "Segura o resultado gastando tempo. A torcida detesta."),
    SAIR_JOGANDO_CURTO("Sair jogando curto",
        "Constrói do goleiro. Bonito quando dá certo, fatal quando não."),
    TIRO_LONGO_DO_GOLEIRO("Ligação direta",
        "Goleiro lança. Perde posse, mas evita risco na saída.");
}

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

    /**
     * MENTALIDADE — o controle mestre, como no FM.
     *
     * 0 = muito retraído, 100 = muito ofensivo. Não substitui os outros
     * sete: ele os DESLOCA. Assim você ajusta a postura geral com um
     * gesto e continua podendo afinar cada peça depois.
     */
    val mentalidade: Int = 50,

    val instrucoes: Set<InstrucaoEquipe> = emptySet(),
) {
    /**
     * Os valores efetivos, com a mentalidade aplicada.
     *
     * O motor lê ESTES, não os brutos. É isso que faz a mentalidade
     * valer de verdade em vez de ser um número decorativo.
     */
    fun efetiva(): Tatica {
        val d = (mentalidade - 50) / 50f    // -1 .. +1
        fun m(v: Int, peso: Float) = (v + d * peso).toInt().coerceIn(0, 100)

        return copy(
            alturaLinha = m(alturaLinha, 26f),
            intensidadePressao = m(intensidadePressao, 20f),
            velocidadeConstrucao = m(velocidadeConstrucao, 14f),
            riscoNoPasse = m(riscoNoPasse, 18f),
            contraAtaque = m(contraAtaque, -16f),
            compactacao = m(compactacao, -10f),
        )
    }

    fun tem(i: InstrucaoEquipe) = i in instrucoes

    fun alternar(i: InstrucaoEquipe) = copy(
        instrucoes = if (tem(i)) instrucoes - i else instrucoes + i
    )

    val rotuloMentalidade: String get() = when {
        mentalidade >= 84 -> "Muito ofensiva"
        mentalidade >= 66 -> "Ofensiva"
        mentalidade >= 56 -> "Positiva"
        mentalidade >= 45 -> "Equilibrada"
        mentalidade >= 34 -> "Cautelosa"
        mentalidade >= 16 -> "Defensiva"
        else -> "Muito retraída"
    }
}

/** Presets como ponto de partida — você edita tudo depois. */
object Estilos {
    val equilibrado = Tatica(mentalidade = 50)

    val posse = Tatica(
        velocidadeConstrucao = 25, alturaLinha = 65, intensidadePressao = 70,
        compactacao = 60, contraAtaque = 10, riscoNoPasse = 35,
        mentalidade = 62,
        instrucoes = setOf(
            InstrucaoEquipe.SAIR_JOGANDO_CURTO,
            InstrucaoEquipe.PRESSAO_APOS_PERDA,
        ),
    )

    val contraAtaque = Tatica(
        velocidadeConstrucao = 85, alturaLinha = 30, intensidadePressao = 30,
        compactacao = 70, contraAtaque = 90, riscoNoPasse = 65,
        mentalidade = 38,
        instrucoes = setOf(InstrucaoEquipe.TIRO_LONGO_DO_GOLEIRO),
    )

    val pressaoAlta = Tatica(
        velocidadeConstrucao = 70, alturaLinha = 85, intensidadePressao = 90,
        compactacao = 65, contraAtaque = 40, riscoNoPasse = 60,
        mentalidade = 78,
        instrucoes = setOf(
            InstrucaoEquipe.LINHA_DE_IMPEDIMENTO,
            InstrucaoEquipe.PRESSAO_APOS_PERDA,
        ),
    )

    val retranca = Tatica(
        velocidadeConstrucao = 60, alturaLinha = 15, intensidadePressao = 20,
        compactacao = 85, contraAtaque = 70, riscoNoPasse = 30,
        mentalidade = 20,
        instrucoes = setOf(
            InstrucaoEquipe.TIRO_LONGO_DO_GOLEIRO,
            InstrucaoEquipe.FAZER_CERA,
        ),
    )

    val todos = listOf(
        "Equilibrado" to equilibrado,
        "Posse de bola" to posse,
        "Contra-ataque" to contraAtaque,
        "Pressão alta" to pressaoAlta,
        "Retranca" to retranca,
    )
}

/*
 * TÁTICA PADRÃO DO CLUBE.
 *
 * Cada clube que você assume já vem com um estilo, e ele não é sorteado:
 * sai dos atributos do próprio elenco. Um time rápido e pouco técnico
 * herda contra-ataque. Um time de bons passadores herda posse. Um elenco
 * fraco herda retranca, porque é o que faz sentido com o que ele tem.
 *
 * Isso é lido uma vez, quando você assume o clube. Depois é seu.
 */
object TaticaDoClube {

    fun derivarDe(
        velocidadeMedia: Int,
        passeMedio: Int,
        forcaMedia: Int,
        resistenciaMedia: Int,
        geralMedio: Int,
    ): Pair<String, Tatica> {

        // Time fraco não tem como propor jogo: recua e espera.
        if (geralMedio < 62) {
            return "Retranca" to Estilos.retranca.copy(
                intensidadePressao = 20 + (resistenciaMedia - 60).coerceIn(0, 20),
                contraAtaque = 60 + (velocidadeMedia - 60).coerceIn(0, 25),
            )
        }

        // O que mais se destaca no elenco define o estilo.
        val perfilPasse = passeMedio - geralMedio
        val perfilVelocidade = velocidadeMedia - geralMedio
        val perfilFisico = ((forcaMedia + resistenciaMedia) / 2) - geralMedio

        return when {
            perfilPasse >= 2 && perfilPasse >= perfilVelocidade ->
                "Posse de bola" to Estilos.posse.copy(
                    riscoNoPasse = 30 + perfilPasse.coerceIn(0, 20),
                )

            perfilVelocidade >= 2 ->
                "Contra-ataque" to Estilos.contraAtaque.copy(
                    contraAtaque = 75 + perfilVelocidade.coerceIn(0, 20),
                )

            perfilFisico >= 2 && resistenciaMedia >= 72 ->
                "Pressão alta" to Estilos.pressaoAlta.copy(
                    intensidadePressao = 80 + (resistenciaMedia - 72).coerceIn(0, 15),
                )

            else -> "Equilibrado" to Estilos.equilibrado
        }
    }
}

/**
 * Teto de reputação que você pode assumir nesta temporada.
 *
 * Começar num clube de elite tira o sentido do modo carreira. Aqui a
 * porta abre por temporada: você prova o trabalho num clube pequeno e
 * os grandes vão ficando ao alcance.
 */
fun reputacaoMaximaPara(temporada: Int): Int = when (temporada) {
    1 -> 66
    2 -> 72
    3 -> 78
    4 -> 84
    else -> 100
}

fun descricaoDoTeto(temporada: Int): String = when (temporada) {
    1 -> "Primeira temporada: clubes até reputação 66"
    2 -> "Segunda temporada: clubes até reputação 72"
    3 -> "Terceira temporada: clubes até reputação 78"
    4 -> "Quarta temporada: clubes até reputação 84"
    else -> "Sem restrição — todos os clubes ao alcance"
}
