package com.exemplo.fmanager.formacao

import com.exemplo.fmanager.dados.Jogador

/*
 * ESTILO DE JOGO DO JOGADOR.
 *
 * Não é enfeite: cada estilo muda de verdade como o jogador se comporta
 * na simulação. Um Finalizador de Área quase não aparece na construção,
 * mas é quem mais recebe a bola na área. Um Lateral Invertido entra no
 * meio-campo com a posse em vez de subir pela linha de fundo.
 *
 * E o jogador não pode ter qualquer estilo: os requisitos são atributos
 * mínimos. Um zagueiro lento não vira Líbero por decreto.
 */

enum class EstiloJogador(
    val rotulo: String,
    val descricao: String,
    val papeisNaturais: Set<Papel>,
) {
    // ----------------------------------------------------- ATAQUE
    FINALIZADOR_AREA(
        "Finalizador de área",
        "Vive na área. Some da construção, aparece no momento do gol.",
        setOf(Papel.ATA),
    ),
    HOMEM_ALVO(
        "Homem-alvo",
        "Segura a bola de costas e disputa tudo pelo alto.",
        setOf(Papel.ATA),
    ),
    FALSO_NOVE(
        "Falso 9",
        "Abandona a área para criar. Arrasta o zagueiro com ele.",
        setOf(Papel.ATA, Papel.MEI),
    ),
    ATACANTE_MOVEL(
        "Atacante móvel",
        "Ataca as costas da zaga em diagonal, vive no limite do impedimento.",
        setOf(Papel.ATA, Papel.PE, Papel.PD),
    ),

    // ------------------------------------------------------ PONTAS
    PONTA_INVERTIDO(
        "Ponta invertido",
        "Corta para dentro no pé bom e finaliza.",
        setOf(Papel.PE, Papel.PD, Papel.ME, Papel.MD),
    ),
    PONTA_CLASSICO(
        "Ponta clássico",
        "Cola na linha, encara o lateral e cruza.",
        setOf(Papel.PE, Papel.PD, Papel.ME, Papel.MD),
    ),

    // ------------------------------------------------------- MEIO
    ARMADOR_AVANCADO(
        "Armador avançado",
        "O cérebro no último terço. Todo passe decisivo passa por ele.",
        setOf(Papel.MEI, Papel.MC),
    ),
    ARMADOR_RECUADO(
        "Armador recuado",
        "Comanda de trás. Troca o lado do campo com passe longo.",
        setOf(Papel.VOL, Papel.MC),
    ),
    BOX_TO_BOX(
        "Box to box",
        "Cobre as duas áreas. Come o campo inteiro no fôlego.",
        setOf(Papel.MC, Papel.VOL, Papel.MEI),
    ),
    CAO_DE_GUARDA(
        "Cão de guarda",
        "Destrói a jogada adversária antes de ela nascer.",
        setOf(Papel.VOL, Papel.MC),
    ),
    MEIA_CHEGANDO(
        "Meia chegando na área",
        "Aparece na segunda bola e chuta de fora.",
        setOf(Papel.MC, Papel.MEI),
    ),

    // ---------------------------------------------------- LATERAIS
    LATERAL_OFENSIVO(
        "Lateral ofensivo",
        "Sobe sempre pela linha e sustenta a largura do time.",
        setOf(Papel.LE, Papel.LD),
    ),
    LATERAL_INVERTIDO(
        "Lateral invertido",
        "Com a posse, entra no meio-campo em vez de subir pela linha.",
        setOf(Papel.LE, Papel.LD),
    ),
    ALA(
        "Ala",
        "Faz o corredor inteiro, da própria área até a do adversário.",
        setOf(Papel.LE, Papel.LD, Papel.ME, Papel.MD),
    ),
    LATERAL_DEFENSIVO(
        "Lateral defensivo",
        "Não abandona a linha de quatro. Prioriza o duelo.",
        setOf(Papel.LE, Papel.LD),
    ),

    // ---------------------------------------------------- ZAGUEIROS
    ZAGUEIRO_CONSTRUTOR(
        "Zagueiro construtor",
        "Sai jogando, quebra linha com passe vertical.",
        setOf(Papel.ZAG),
    ),
    ZAGUEIRO_MARCADOR(
        "Zagueiro marcador",
        "Sobe na marcação e vence no corpo. Não arrisca no passe.",
        setOf(Papel.ZAG),
    ),
    LIBERO(
        "Líbero",
        "Sai da linha com a bola no pé e vira homem a mais no meio.",
        setOf(Papel.ZAG, Papel.VOL),
    ),

    // ------------------------------------------------------ GOLEIRO
    GOLEIRO_LINHA(
        "Goleiro-líbero",
        "Joga adiantado e limpa as costas da defesa.",
        setOf(Papel.GOL),
    ),
    GOLEIRO_AREA(
        "Goleiro de área",
        "Fica na linha. Reflexo puro.",
        setOf(Papel.GOL),
    );

    /** Atributos mínimos para o jogador poder assumir este estilo. */
    fun requisitos(): List<Pair<String, Int>> = when (this) {
        FINALIZADOR_AREA -> listOf("Finalização" to 74, "Posicionamento" to 72)
        HOMEM_ALVO -> listOf("Contato físico" to 74, "Cabeceio" to 72)
        FALSO_NOVE -> listOf("Visão" to 72, "Passe baixo" to 70, "Controle de bola" to 72)
        ATACANTE_MOVEL -> listOf("Velocidade" to 78, "Aceleração" to 76)
        PONTA_INVERTIDO -> listOf("Drible" to 74, "Finalização" to 68)
        PONTA_CLASSICO -> listOf("Cruzamento" to 72, "Velocidade" to 72)
        ARMADOR_AVANCADO -> listOf("Visão" to 76, "Passe baixo" to 74)
        ARMADOR_RECUADO -> listOf("Passe alto" to 74, "Visão" to 72)
        BOX_TO_BOX -> listOf("Resistência" to 78, "Passe baixo" to 66)
        CAO_DE_GUARDA -> listOf("Roubo de bola" to 72, "Interceptação" to 70)
        MEIA_CHEGANDO -> listOf("Chute de longe" to 72, "Posicionamento" to 68)
        LATERAL_OFENSIVO -> listOf("Resistência" to 74, "Cruzamento" to 68)
        LATERAL_INVERTIDO -> listOf("Passe baixo" to 70, "Controle de bola" to 68)
        ALA -> listOf("Resistência" to 80, "Velocidade" to 74)
        LATERAL_DEFENSIVO -> listOf("Roubo de bola" to 70, "Consciência defensiva" to 68)
        ZAGUEIRO_CONSTRUTOR -> listOf("Passe baixo" to 68, "Sangue frio" to 68)
        ZAGUEIRO_MARCADOR -> listOf("Contato físico" to 74, "Roubo de bola" to 72)
        LIBERO -> listOf("Velocidade" to 70, "Passe baixo" to 70, "Controle de bola" to 68)
        GOLEIRO_LINHA -> listOf("Chute (GOL)" to 66, "Posicionamento (GOL)" to 70)
        GOLEIRO_AREA -> listOf("Reflexo (GOL)" to 72)
    }

    private fun valorDe(j: Jogador, atributo: String): Int = when (atributo) {
        "Finalização" -> j.finalizacao
        "Posicionamento" -> j.posicionamento
        "Contato físico" -> j.contatoFisico
        "Cabeceio" -> j.cabeceio
        "Visão" -> j.visao
        "Passe baixo" -> j.passeBaixo
        "Passe alto" -> j.passeAlto
        "Controle de bola" -> j.controleBola
        "Velocidade" -> j.velocidade
        "Aceleração" -> j.aceleracao
        "Drible" -> j.drible
        "Cruzamento" -> j.cruzamento
        "Resistência" -> j.resistencia
        "Roubo de bola" -> j.rouboBola
        "Interceptação" -> j.interceptacao
        "Chute de longe" -> j.chuteLonge
        "Consciência defensiva" -> j.consciencaDef
        "Sangue frio" -> j.sangueFrio
        "Chute (GOL)" -> j.golChute
        "Posicionamento (GOL)" -> j.golPosicionamento
        "Reflexo (GOL)" -> j.golReflexo
        else -> 0
    }

    /** O jogador tem atributo para assumir este estilo? */
    fun disponivelPara(j: Jogador): Boolean =
        requisitos().all { (attr, min) -> valorDe(j, attr) >= min }

    /** O que falta para ele destravar o estilo. Serve de guia de treino. */
    fun faltaPara(j: Jogador): List<Pair<String, Int>> =
        requisitos().mapNotNull { (attr, min) ->
            val atual = valorDe(j, attr)
            if (atual < min) attr to (min - atual) else null
        }

    companion object {
        /** Estilos que este jogador consegue exercer, ordenados pelo
         *  quanto sobram os requisitos (melhor encaixe primeiro). */
        fun disponiveisPara(j: Jogador): List<EstiloJogador> =
            entries.filter { it.disponivelPara(j) }

        /** Sugestão automática para o papel em que ele foi escalado. */
        fun sugerir(j: Jogador, papel: Papel): EstiloJogador? =
            entries
                .filter { papel in it.papeisNaturais && it.disponivelPara(j) }
                .maxByOrNull { estilo ->
                    estilo.requisitos().sumOf { (attr, min) ->
                        (estilo.valorDe(j, attr) - min)
                    }
                }
    }
}

/**
 * Modificadores que o estilo aplica na simulação.
 * O motor lê isto, não o enum diretamente.
 */
data class ModificadorEstilo(
    /** Peso para ser escolhido como finalizador da jogada. */
    val pesoFinalizacao: Float = 1f,
    /** Peso para ser escolhido como criador da jogada. */
    val pesoCriacao: Float = 1f,
    /** Multiplicador na contribuição defensiva. */
    val contribuicaoDefensiva: Float = 1f,
    /** Deslocamento extra em y na fase com posse (positivo = sobe). */
    val avancoComPosse: Float = 0f,
    /** Deslocamento em x rumo ao centro na fase com posse. */
    val estreitamento: Float = 0f,
    /** Custo físico: quanto o estilo desgasta ao longo do jogo. */
    val desgaste: Float = 1f,
)

fun EstiloJogador?.modificador(): ModificadorEstilo = when (this) {
    null -> ModificadorEstilo()

    EstiloJogador.FINALIZADOR_AREA -> ModificadorEstilo(
        pesoFinalizacao = 1.8f, pesoCriacao = 0.4f,
        contribuicaoDefensiva = 0.5f, avancoComPosse = 0.04f,
    )
    EstiloJogador.HOMEM_ALVO -> ModificadorEstilo(
        pesoFinalizacao = 1.4f, pesoCriacao = 0.9f,
        contribuicaoDefensiva = 0.7f, desgaste = 1.1f,
    )
    EstiloJogador.FALSO_NOVE -> ModificadorEstilo(
        pesoFinalizacao = 0.9f, pesoCriacao = 1.7f,
        avancoComPosse = -0.10f, desgaste = 1.15f,
    )
    EstiloJogador.ATACANTE_MOVEL -> ModificadorEstilo(
        pesoFinalizacao = 1.5f, avancoComPosse = 0.06f, desgaste = 1.25f,
    )

    EstiloJogador.PONTA_INVERTIDO -> ModificadorEstilo(
        pesoFinalizacao = 1.45f, pesoCriacao = 1.1f, estreitamento = 0.14f,
    )
    EstiloJogador.PONTA_CLASSICO -> ModificadorEstilo(
        pesoFinalizacao = 0.75f, pesoCriacao = 1.5f,
        estreitamento = -0.06f, desgaste = 1.15f,
    )

    EstiloJogador.ARMADOR_AVANCADO -> ModificadorEstilo(
        pesoCriacao = 1.9f, pesoFinalizacao = 1.0f, contribuicaoDefensiva = 0.6f,
    )
    EstiloJogador.ARMADOR_RECUADO -> ModificadorEstilo(
        pesoCriacao = 1.6f, pesoFinalizacao = 0.5f,
        contribuicaoDefensiva = 1.05f, avancoComPosse = -0.05f,
    )
    EstiloJogador.BOX_TO_BOX -> ModificadorEstilo(
        pesoFinalizacao = 1.15f, pesoCriacao = 1.15f,
        contribuicaoDefensiva = 1.2f, avancoComPosse = 0.05f, desgaste = 1.4f,
    )
    EstiloJogador.CAO_DE_GUARDA -> ModificadorEstilo(
        pesoFinalizacao = 0.3f, pesoCriacao = 0.6f,
        contribuicaoDefensiva = 1.55f, avancoComPosse = -0.04f, desgaste = 1.2f,
    )
    EstiloJogador.MEIA_CHEGANDO -> ModificadorEstilo(
        pesoFinalizacao = 1.5f, pesoCriacao = 0.9f, avancoComPosse = 0.07f,
    )

    EstiloJogador.LATERAL_OFENSIVO -> ModificadorEstilo(
        pesoCriacao = 1.3f, contribuicaoDefensiva = 0.8f,
        avancoComPosse = 0.12f, desgaste = 1.35f,
    )
    EstiloJogador.LATERAL_INVERTIDO -> ModificadorEstilo(
        pesoCriacao = 1.4f, contribuicaoDefensiva = 1.0f,
        avancoComPosse = 0.06f, estreitamento = 0.22f, desgaste = 1.15f,
    )
    EstiloJogador.ALA -> ModificadorEstilo(
        pesoCriacao = 1.35f, contribuicaoDefensiva = 0.95f,
        avancoComPosse = 0.16f, desgaste = 1.5f,
    )
    EstiloJogador.LATERAL_DEFENSIVO -> ModificadorEstilo(
        pesoCriacao = 0.7f, contribuicaoDefensiva = 1.35f,
        avancoComPosse = -0.03f, desgaste = 0.9f,
    )

    EstiloJogador.ZAGUEIRO_CONSTRUTOR -> ModificadorEstilo(
        pesoCriacao = 1.25f, contribuicaoDefensiva = 1.0f,
    )
    EstiloJogador.ZAGUEIRO_MARCADOR -> ModificadorEstilo(
        pesoCriacao = 0.5f, contribuicaoDefensiva = 1.35f,
    )
    EstiloJogador.LIBERO -> ModificadorEstilo(
        pesoCriacao = 1.4f, contribuicaoDefensiva = 1.1f,
        avancoComPosse = 0.08f, desgaste = 1.2f,
    )

    EstiloJogador.GOLEIRO_LINHA -> ModificadorEstilo(
        pesoCriacao = 1.2f, contribuicaoDefensiva = 1.1f, avancoComPosse = 0.05f,
    )
    EstiloJogador.GOLEIRO_AREA -> ModificadorEstilo(
        contribuicaoDefensiva = 1.15f,
    )
}
