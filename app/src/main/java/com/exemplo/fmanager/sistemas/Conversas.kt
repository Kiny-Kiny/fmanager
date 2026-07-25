package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Contrato
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.LinhaTabela

/*
 * CONVERSAS — com o jogador e com a imprensa.
 *
 * As duas telas do OpenFootManager que eu não tinha nada parecido:
 * playertalk e presstalk. Tudo que eu havia construído era tático ou
 * estatístico; faltava a camada humana, que é metade do que faz um manager
 * ser um manager e não uma planilha.
 *
 * A regra de desenho aqui: NENHUMA opção é sempre certa. Elogiar quem está
 * mal soa falso. Prometer titularidade que você não vai cumprir cobra
 * depois. Bater de frente com a imprensa agrada o vestiário e irrita a
 * diretoria. Se existisse uma resposta ótima, a escolha não seria escolha.
 */

// ------------------------------------------------- CONVERSA COM JOGADOR

enum class AssuntoConversa(val rotulo: String) {
    ELOGIAR("Elogiar o desempenho"),
    COBRAR("Cobrar mais dedicação"),
    EXPLICAR_BANCO("Explicar a falta de minutos"),
    PROMETER_MINUTOS("Prometer titularidade"),
    PEDIR_PACIENCIA("Pedir paciência"),
    CONVERSA_FRANCA("Conversa franca sobre o futuro"),
}

data class RespostaConversa(
    val texto: String,
    val deltaMoral: Int,
    /** Promessa registrada, se houver. Cobra depois. */
    val prometeuTitularidade: Boolean = false,
)

object ConversaComJogador {

    /**
     * Quais assuntos fazem sentido agora.
     *
     * Não mostro tudo sempre: oferecer "explicar a falta de minutos" a um
     * titular absoluto seria ruído, e o jogo ficaria com cara de menu em
     * vez de conversa.
     */
    fun assuntosPara(
        jogador: Jogador,
        contrato: Contrato,
        minutosRecentes: Int,
        notaMedia: Float,
        temporadaAtual: Int,
    ): List<AssuntoConversa> = buildList {
        if (notaMedia >= 7.0f) add(AssuntoConversa.ELOGIAR)
        if (notaMedia in 0.1f..6.2f) add(AssuntoConversa.COBRAR)
        if (minutosRecentes < 120) {
            add(AssuntoConversa.EXPLICAR_BANCO)
            add(AssuntoConversa.PROMETER_MINUTOS)
            add(AssuntoConversa.PEDIR_PACIENCIA)
        }
        if (contrato.terminaEmTemporada <= temporadaAtual + 1) {
            add(AssuntoConversa.CONVERSA_FRANCA)
        }
        if (isEmpty()) add(AssuntoConversa.ELOGIAR)
    }

    /**
     * O resultado depende do CONTEXTO, não só da escolha.
     *
     * Elogiar quem está indo bem funciona; elogiar quem está mal soa falso
     * e piora. É essa dependência que faz a conversa ter peso.
     */
    fun conversar(
        assunto: AssuntoConversa,
        jogador: Jogador,
        contrato: Contrato,
        minutosRecentes: Int,
        notaMedia: Float,
        melhorDoElenco: Boolean,
    ): RespostaConversa = when (assunto) {

        AssuntoConversa.ELOGIAR ->
            if (notaMedia >= 7.0f) RespostaConversa(
                "${jogador.nome} agradece o reconhecimento e diz que vai " +
                        "manter o nível.", +8)
            else RespostaConversa(
                "${jogador.nome} ouve o elogio de cara fechada. Ele sabe que " +
                        "não está jogando bem, e o elogio soou vazio.", -4)

        AssuntoConversa.COBRAR ->
            if (jogador.idade <= 23) RespostaConversa(
                "${jogador.nome} aceita a cobrança e promete treinar mais " +
                        "forte. Jovem ainda ouve.", +4)
            else if (melhorDoElenco) RespostaConversa(
                "${jogador.nome} não gostou de ser cobrado publicamente. " +
                        "Ele se considera acima disso.", -9)
            else RespostaConversa(
                "${jogador.nome} recebe a cobrança em silêncio. Ficou " +
                        "incomodado, mas entendeu.", -2)

        AssuntoConversa.EXPLICAR_BANCO ->
            if (minutosRecentes < 30) RespostaConversa(
                "Você explica a situação. ${jogador.nome} continua " +
                        "insatisfeito, mas reconhece a franqueza.", +3)
            else RespostaConversa(
                "${jogador.nome} escuta a explicação e diz que vai esperar " +
                        "a oportunidade.", +5)

        AssuntoConversa.PROMETER_MINUTOS -> RespostaConversa(
            "Você promete titularidade a ${jogador.nome}. Ele sai animado — " +
                    "mas agora essa promessa está registrada.",
            +14, prometeuTitularidade = true,
        )

        AssuntoConversa.PEDIR_PACIENCIA ->
            if (jogador.idade >= 30) RespostaConversa(
                "${jogador.nome} responde que na idade dele não sobra tempo " +
                        "para ter paciência.", -6)
            else RespostaConversa(
                "${jogador.nome} concorda em esperar sua vez.", +4)

        AssuntoConversa.CONVERSA_FRANCA ->
            if (EstadoMoral.de(contrato.moral) >= EstadoMoral.CONTENTE)
                RespostaConversa(
                    "${jogador.nome} diz que está feliz no clube e aberto a " +
                            "renovar.", +6)
            else RespostaConversa(
                "${jogador.nome} deixa claro que está de olho no mercado.", -3)
    }
}

// --------------------------------------------- COLETIVA DE IMPRENSA

enum class TomDaResposta(val rotulo: String) {
    CONFIANTE("Confiante"),
    CAUTELOSO("Cauteloso"),
    DEFENSIVO("Defensivo"),
    PROVOCADOR("Provocador"),
}

data class Pergunta(
    val texto: String,
    val contexto: String,
)

data class EfeitoResposta(
    val comentario: String,
    val deltaMoralElenco: Int,
    val deltaConfiancaDiretoria: Int,
    val deltaTorcida: Int,
)

object Coletiva {

    /**
     * As perguntas nascem da situação real do time. Uma coletiva com
     * perguntas genéricas seria decorativa; assim ela comenta o que
     * acabou de acontecer.
     */
    fun perguntas(
        posicao: Int,
        posicaoAlvo: Int,
        forma: List<Char>,
        viveNaCopa: Boolean,
        climaVestiario: Int,
        proximoEmCasa: Boolean,
    ): List<Pergunta> = buildList {
        val ultimas = forma.takeLast(3)

        if (ultimas.count { it == 'D' } >= 2) add(Pergunta(
            "Três jogos, duas derrotas. O time perdeu o rumo?",
            "sequência ruim",
        )) else if (ultimas.all { it == 'V' } && ultimas.size == 3) add(Pergunta(
            "Três vitórias seguidas. Dá para sonhar com mais?",
            "boa fase",
        ))

        if (posicao > posicaoAlvo && posicao > 0) add(Pergunta(
            "A ${posicao}ª colocação está abaixo do esperado. " +
                    "O senhor assume a responsabilidade?",
            "abaixo da meta",
        ))

        if (!viveNaCopa) add(Pergunta(
            "A eliminação na copa foi um fracasso?",
            "eliminado",
        ))

        if (climaVestiario < 45) add(Pergunta(
            "Circulam rumores de insatisfação no vestiário. Procede?",
            "clima ruim",
        ))

        add(Pergunta(
            if (proximoEmCasa) "O que esperar do jogo em casa?"
            else "Jogar fora tem sido um problema. E agora?",
            "próximo jogo",
        ))
    }

    /**
     * Cada tom tem custo. Nenhum é a resposta certa sempre.
     *
     * Confiante agrada torcida e vestiário mas irrita a diretoria se o time
     * está mal. Defensivo protege você e desanima o grupo. Provocador
     * eletriza a torcida e é o mais arriscado de todos.
     */
    fun responder(
        pergunta: Pergunta,
        tom: TomDaResposta,
        timeVaiBem: Boolean,
    ): EfeitoResposta = when (tom) {

        TomDaResposta.CONFIANTE ->
            if (timeVaiBem) EfeitoResposta(
                "Você projeta confiança e a sala compra. O grupo gosta de " +
                        "ver o treinador firme.", +5, +3, +6)
            else EfeitoResposta(
                "Sua confiança soa desconectada do que está em campo. A " +
                        "diretoria não gostou do tom.", +4, -6, -2)

        TomDaResposta.CAUTELOSO -> EfeitoResposta(
            "Resposta medida, sem manchete. Ninguém se empolga, ninguém se " +
                    "ofende.", +1, +2, 0)

        TomDaResposta.DEFENSIVO ->
            if (timeVaiBem) EfeitoResposta(
                "Você se defende de uma acusação que ninguém fez. Passou " +
                        "insegurança.", -3, -2, -4)
            else EfeitoResposta(
                "Você protege o próprio trabalho. A diretoria entende, o " +
                        "grupo não se sente defendido.", -5, +3, -3)

        TomDaResposta.PROVOCADOR ->
            if (timeVaiBem) EfeitoResposta(
                "Você provoca e a torcida adora. Vira manchete — e agora tem " +
                        "que sustentar em campo.", +8, -4, +12)
            else EfeitoResposta(
                "Provocar perdendo pegou muito mal. Sobrou para todo lado.",
                -6, -9, -8)
    }
}

// ------------------------------------------------- PROMESSAS EM ABERTO

/*
 * Promessa registrada é promessa cobrada.
 *
 * Sem isso "prometer titularidade" seria um botão de +14 de moral sem
 * custo, e a escolha não teria peso nenhum.
 */
data class Promessa(
    val jogadorId: Int,
    val tipo: String,
    val temporada: Int,
    val prazoEmSemanas: Int,
    val semanasPassadas: Int = 0,
) {
    val vencida: Boolean get() = semanasPassadas >= prazoEmSemanas
}

object Promessas {

    /** Penalidade quando a promessa vence sem ser cumprida. */
    const val QUEBRA_DE_PROMESSA = -22

    fun cumprida(promessa: Promessa, minutosNoPeriodo: Int): Boolean =
        when (promessa.tipo) {
            "titularidade" -> minutosNoPeriodo >= promessa.prazoEmSemanas * 55
            else -> true
        }
}
