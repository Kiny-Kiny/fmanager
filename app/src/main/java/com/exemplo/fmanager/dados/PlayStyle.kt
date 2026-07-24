package com.exemplo.fmanager.dados

import com.exemplo.fmanager.formacao.EstiloJogador

/*
 * PLAYSTYLES DA EA.
 *
 * Vêm prontos da API, um por jogador. São TRAÇOS INATOS — o que o cara
 * sabe fazer de nascença. Não confundir com o EstiloJogador, que é a
 * FUNÇÃO que você atribui a ele no seu sistema.
 *
 * O eFootball tem essa mesma separação: "Playing Style" (a função) e
 * "Player Skills" (os traços). Aqui os dois se conversam — um jogador
 * com Finesse Shot e Rapid destrava Ponta Invertido com folga.
 *
 * O sufixo "+" na API marca a versão de elite do traço, que vale mais.
 */

enum class PlayStyle(val rotulo: String, val chave: String) {
    // Finalização
    FINESSE_SHOT("Chute colocado", "finesse shot"),
    CHIP_SHOT("Cavadinha", "chip shot"),
    POWER_SHOT("Chute forte", "power shot"),
    DEAD_BALL("Bola parada", "dead ball"),
    POWER_HEADER("Cabeceio forte", "power header"),

    // Passe
    PINGED_PASS("Passe tenso", "pinged pass"),
    INCISIVE_PASS("Passe incisivo", "incisive pass"),
    LONG_BALL("Lançamento", "long ball pass"),
    TIKI_TAKA("Tiki taka", "tiki taka"),
    WHIPPED_PASS("Cruzamento rasteiro", "whipped pass"),

    // Condução
    FIRST_TOUCH("Primeiro toque", "first touch"),
    FLAIR("Categoria", "flair"),
    PRESS_PROVEN("Aguenta pressão", "press proven"),
    RAPID("Explosão", "rapid"),
    TECHNICAL("Técnico", "technical"),
    TRICKSTER("Driblador", "trickster"),

    // Defesa
    BLOCK("Bloqueio", "block"),
    BRUISER("Truculento", "bruiser"),
    INTERCEPT("Interceptador", "intercept"),
    JOCKEY("Contenção", "jockey"),
    SLIDE_TACKLE("Carrinho", "slide tackle"),
    ANTICIPATE("Antecipação", "anticipate"),

    // Físico
    AERIAL("Jogo aéreo", "aerial"),
    TRIVELA("Trivela", "trivela"),
    ACROBATIC("Acrobático", "acrobatic"),
    LONG_THROW("Lateral longo", "long throw"),
    RELENTLESS("Incansável", "relentless"),
    QUICK_STEP("Arrancada", "quick step"),

    // Goleiro
    FAR_THROW("Reposição longa", "far throw"),
    FOOTWORK("Jogo de pés", "footwork"),
    CROSS_CLAIMER("Sai bem no cruzamento", "cross claimer"),
    RUSH_OUT("Sai do gol", "rush out"),
    FAR_REACH("Alcance", "far reach"),
    DEFLECTOR("Rebote curto", "deflector");

    companion object {
        private val porChave = entries.associateBy { it.chave }

        /**
         * Converte o texto da API. "Quick Step+" vira QUICK_STEP com
         * elite = true. Devolve null se for um traço que não conhecemos
         * (a EA adiciona novos a cada edição, então isso vai acontecer).
         */
        fun deTexto(bruto: String): Pair<PlayStyle, Boolean>? {
            val limpo = bruto.trim().lowercase()
            val elite = limpo.endsWith("+")
            val chave = limpo.removeSuffix("+").trim()
            return porChave[chave]?.let { it to elite }
        }
    }
}

/** Um traço que o jogador possui, com ou sem o nível de elite. */
data class TracoJogador(val estilo: PlayStyle, val elite: Boolean) {
    /** Elite pesa quase o dobro. */
    val peso: Float get() = if (elite) 1.9f else 1f

    override fun toString() = estilo.rotulo + if (elite) "+" else ""
}

/**
 * Modificadores que os traços somam no rendimento do jogador.
 * O motor lê isto e multiplica junto com as outras camadas.
 */
data class BonusTracos(
    val finalizacao: Float = 1f,
    val criacao: Float = 1f,
    val defesa: Float = 1f,
    val resistencia: Float = 1f,
    val drible: Float = 1f,
)

fun List<TracoJogador>.bonus(): BonusTracos {
    var fin = 1f; var cri = 1f; var def = 1f; var res = 1f; var dri = 1f
    val passo = 0.045f

    forEach { traco ->
        val p = passo * traco.peso
        when (traco.estilo) {
            PlayStyle.FINESSE_SHOT, PlayStyle.POWER_SHOT,
            PlayStyle.CHIP_SHOT, PlayStyle.POWER_HEADER,
            PlayStyle.ACROBATIC, PlayStyle.TRIVELA -> fin += p

            PlayStyle.DEAD_BALL -> { fin += p * .6f; cri += p * .6f }

            PlayStyle.PINGED_PASS, PlayStyle.INCISIVE_PASS,
            PlayStyle.LONG_BALL, PlayStyle.TIKI_TAKA,
            PlayStyle.WHIPPED_PASS -> cri += p

            PlayStyle.BLOCK, PlayStyle.BRUISER, PlayStyle.INTERCEPT,
            PlayStyle.JOCKEY, PlayStyle.SLIDE_TACKLE,
            PlayStyle.ANTICIPATE -> def += p

            PlayStyle.RELENTLESS -> res += p * 1.5f
            PlayStyle.PRESS_PROVEN -> { res += p * .7f; cri += p * .4f }
            PlayStyle.QUICK_STEP, PlayStyle.RAPID -> { dri += p; fin += p * .4f }

            PlayStyle.TRICKSTER, PlayStyle.TECHNICAL,
            PlayStyle.FLAIR, PlayStyle.FIRST_TOUCH -> dri += p

            PlayStyle.AERIAL -> { fin += p * .6f; def += p * .5f }
            PlayStyle.LONG_THROW -> cri += p * .3f

            // Goleiro: tudo entra como defesa
            PlayStyle.FAR_THROW, PlayStyle.FOOTWORK, PlayStyle.CROSS_CLAIMER,
            PlayStyle.RUSH_OUT, PlayStyle.FAR_REACH,
            PlayStyle.DEFLECTOR -> def += p
        }
    }

    // Teto para nenhum jogador virar sobre-humano por acumular traços.
    return BonusTracos(
        finalizacao = fin.coerceAtMost(1.32f),
        criacao = cri.coerceAtMost(1.32f),
        defesa = def.coerceAtMost(1.32f),
        resistencia = res.coerceAtMost(1.35f),
        drible = dri.coerceAtMost(1.32f),
    )
}

/**
 * Traços que combinam com cada função tática.
 * Serve para a tela sugerir: "esse cara nasceu pra ser ponta invertido".
 */
fun List<TracoJogador>.afinidadeCom(estilo: EstiloJogador): Int {
    val ideais: Set<PlayStyle> = when (estilo) {
        EstiloJogador.FINALIZADOR_AREA -> setOf(
            PlayStyle.FINESSE_SHOT, PlayStyle.POWER_SHOT, PlayStyle.ACROBATIC)
        EstiloJogador.HOMEM_ALVO -> setOf(
            PlayStyle.AERIAL, PlayStyle.POWER_HEADER, PlayStyle.BRUISER)
        EstiloJogador.FALSO_NOVE -> setOf(
            PlayStyle.INCISIVE_PASS, PlayStyle.FIRST_TOUCH, PlayStyle.TIKI_TAKA)
        EstiloJogador.ATACANTE_MOVEL -> setOf(
            PlayStyle.QUICK_STEP, PlayStyle.RAPID)
        EstiloJogador.PONTA_INVERTIDO -> setOf(
            PlayStyle.FINESSE_SHOT, PlayStyle.TRIVELA, PlayStyle.TRICKSTER)
        EstiloJogador.PONTA_CLASSICO -> setOf(
            PlayStyle.WHIPPED_PASS, PlayStyle.QUICK_STEP, PlayStyle.TRICKSTER)
        EstiloJogador.ARMADOR_AVANCADO -> setOf(
            PlayStyle.INCISIVE_PASS, PlayStyle.PINGED_PASS, PlayStyle.TECHNICAL)
        EstiloJogador.ARMADOR_RECUADO -> setOf(
            PlayStyle.LONG_BALL, PlayStyle.PINGED_PASS, PlayStyle.PRESS_PROVEN)
        EstiloJogador.BOX_TO_BOX -> setOf(
            PlayStyle.RELENTLESS, PlayStyle.PRESS_PROVEN)
        EstiloJogador.CAO_DE_GUARDA -> setOf(
            PlayStyle.INTERCEPT, PlayStyle.JOCKEY, PlayStyle.ANTICIPATE,
            PlayStyle.BRUISER)
        EstiloJogador.MEIA_CHEGANDO -> setOf(
            PlayStyle.POWER_SHOT, PlayStyle.FINESSE_SHOT)
        EstiloJogador.LATERAL_OFENSIVO, EstiloJogador.ALA -> setOf(
            PlayStyle.RELENTLESS, PlayStyle.QUICK_STEP, PlayStyle.WHIPPED_PASS)
        EstiloJogador.LATERAL_INVERTIDO -> setOf(
            PlayStyle.TIKI_TAKA, PlayStyle.PRESS_PROVEN, PlayStyle.TECHNICAL)
        EstiloJogador.LATERAL_DEFENSIVO -> setOf(
            PlayStyle.JOCKEY, PlayStyle.BLOCK, PlayStyle.SLIDE_TACKLE)
        EstiloJogador.ZAGUEIRO_CONSTRUTOR -> setOf(
            PlayStyle.PINGED_PASS, PlayStyle.LONG_BALL, PlayStyle.PRESS_PROVEN)
        EstiloJogador.ZAGUEIRO_MARCADOR -> setOf(
            PlayStyle.BRUISER, PlayStyle.AERIAL, PlayStyle.BLOCK)
        EstiloJogador.LIBERO -> setOf(
            PlayStyle.ANTICIPATE, PlayStyle.QUICK_STEP, PlayStyle.PINGED_PASS)
        EstiloJogador.GOLEIRO_LINHA -> setOf(
            PlayStyle.RUSH_OUT, PlayStyle.FOOTWORK, PlayStyle.FAR_THROW)
        EstiloJogador.GOLEIRO_AREA -> setOf(
            PlayStyle.FAR_REACH, PlayStyle.DEFLECTOR, PlayStyle.CROSS_CLAIMER)
    }

    val pontos = filter { it.estilo in ideais }.sumOf { it.peso.toDouble() }
    return ((pontos / ideais.size) * 100).toInt().coerceIn(0, 100)
}
