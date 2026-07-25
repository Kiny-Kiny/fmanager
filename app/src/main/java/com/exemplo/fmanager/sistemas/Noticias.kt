package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.Contrato
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.LinhaTabela

/*
 * CAIXA DE ENTRADA.
 *
 * As mensagens não ficam armazenadas: são DERIVADAS do estado do jogo a
 * cada vez que a tela abre. Isso evita mais uma tabela no banco e garante
 * que nada fique obsoleto — se o jogador se recuperou da lesão, a
 * mensagem sobre ela simplesmente deixa de existir.
 */

enum class TipoNoticia(val rotulo: String) {
    DIRETORIA("Diretoria"),
    OLHEIRO("Olheiro"),
    MEDICO("Departamento médico"),
    CONTRATO("Contratos"),
    IMPRENSA("Imprensa"),
    MERCADO("Mercado"),
}

data class Noticia(
    val tipo: TipoNoticia,
    val titulo: String,
    val corpo: String,
    val urgente: Boolean = false,
)

object CaixaDeEntrada {

    fun gerar(
        clube: Clube,
        temporada: Int,
        rodada: Int,
        elenco: List<Jogador>,
        contratos: Map<Int, Contrato>,
        tabela: List<LinhaTabela>,
        expectativa: Expectativa,
        viveNaCopa: Boolean,
        faseCopa: String,
        caixa: Long,
        folha: Long,
    ): List<Noticia> = buildList {

        // ------------------------------------------------ DIRETORIA
        add(Noticia(
            tipo = TipoNoticia.DIRETORIA,
            titulo = when (expectativa.situacao) {
                Situacao.SEGURO -> "A diretoria aprova seu trabalho"
                Situacao.ESTAVEL -> "Reunião de acompanhamento"
                Situacao.PRESSIONADO -> "A diretoria quer explicações"
                Situacao.AMEACADO -> "Seu cargo está em risco"
            },
            corpo = "${expectativa.resumo} A meta da temporada é terminar " +
                    "entre os ${expectativa.posicaoAlvo} primeiros e chegar " +
                    "às ${expectativa.faseCopaAlvo.lowercase()} na copa. " +
                    "Confiança atual: ${expectativa.confianca}%.",
            urgente = expectativa.situacao == Situacao.AMEACADO,
        ))

        // -------------------------------------------- DEPARTAMENTO MÉDICO
        val lesionados = elenco.filter {
            (contratos[it.id]?.semanasLesionado ?: 0) > 0
        }
        if (lesionados.isNotEmpty()) {
            add(Noticia(
                tipo = TipoNoticia.MEDICO,
                titulo = "${lesionados.size} jogador(es) no departamento médico",
                corpo = lesionados.take(4).joinToString(", ") { j ->
                    "${j.nome} (${contratos[j.id]?.semanasLesionado} sem.)"
                },
                urgente = lesionados.size >= 3,
            ))
        }

        // ------------------------------------------------- CONTRATOS
        val vencendo = elenco.filter { j ->
            val c = contratos[j.id] ?: return@filter false
            c.terminaEmTemporada <= temporada
        }.sortedByDescending { it.geral }

        if (vencendo.isNotEmpty()) {
            add(Noticia(
                tipo = TipoNoticia.CONTRATO,
                titulo = "${vencendo.size} contrato(s) vencendo",
                corpo = "Sem renovação, estes jogadores saem de graça no fim " +
                        "da temporada: " +
                        vencendo.take(5).joinToString(", ") { "${it.nome} (${it.geral})" },
                urgente = vencendo.any { it.geral >= 78 },
            ))
        }

        // --------------------------------------------------- OLHEIRO
        val promessas = elenco
            .filter { it.idade <= 21 && it.potencial - it.geral >= 8 }
            .sortedByDescending { it.potencial }

        if (promessas.isNotEmpty()) {
            val p = promessas.first()
            add(Noticia(
                tipo = TipoNoticia.OLHEIRO,
                titulo = "Relatório: ${p.nome} pode virar titular",
                corpo = "${p.idade} anos, overall ${p.geral} com potencial de " +
                        "${p.potencial}. O olheiro recomenda minutos e treino " +
                        "focado. Temos ${promessas.size} nomes nessa faixa.",
            ))
        }

        // ---------------------------------------------------- MERCADO
        val folgaSalarial = clube.folhaMaxEur - folha
        add(Noticia(
            tipo = TipoNoticia.MERCADO,
            titulo = if (folgaSalarial > 0) "Espaço no orçamento"
            else "Folha salarial estourada",
            corpo = if (folgaSalarial > 0)
                "Caixa de ${formatarValor(caixa)} e folga de " +
                        "${formatarValor(folgaSalarial)} por semana na folha. " +
                        "Dá para se mexer no mercado."
            else
                "A folha passou o teto em ${formatarValor(-folgaSalarial)} " +
                        "por semana. A diretoria pede corte antes de qualquer " +
                        "contratação.",
            urgente = folgaSalarial < 0,
        ))

        // --------------------------------------------------- IMPRENSA
        val minha = tabela.firstOrNull { it.clubeId == clube.id }
        val posicao = tabela.indexOfFirst { it.clubeId == clube.id } + 1
        if (minha != null && minha.jogos > 0) {
            add(Noticia(
                tipo = TipoNoticia.IMPRENSA,
                titulo = tituloImprensa(posicao, expectativa.posicaoAlvo, minha),
                corpo = "${minha.jogos} jogos: ${minha.vitorias}V " +
                        "${minha.empates}E ${minha.derrotas}D, " +
                        "${minha.golsPro} gols a favor e ${minha.golsContra} contra. " +
                        "Saldo de ${minha.saldo}." +
                        if (viveNaCopa) " Segue vivo na copa, na $faseCopa."
                        else " Já fora da copa.",
            ))
        }

        // Elenco curto é problema prático, vale avisar.
        if (elenco.size < 16) {
            add(Noticia(
                tipo = TipoNoticia.DIRETORIA,
                titulo = "Elenco curto",
                corpo = "Você tem ${elenco.size} jogadores. Com lesões e " +
                        "suspensões, isso não fecha uma temporada. " +
                        "Considere contratar.",
                urgente = elenco.size < 14,
            ))
        }
    }

    private fun tituloImprensa(
        posicao: Int, alvo: Int, linha: LinhaTabela,
    ): String = when {
        posicao == 1 -> "Líder isolado depois de ${linha.jogos} rodadas"
        posicao <= alvo -> "Campanha dentro do esperado"
        posicao <= alvo + 3 -> "Time oscila e frustra a torcida"
        else -> "Pressão aumenta após sequência ruim"
    }

    private fun formatarValor(v: Long): String = when {
        v >= 1_000_000 -> "€%.1fM".format(v / 1_000_000.0)
        v >= 1_000 -> "€%.0fK".format(v / 1_000.0)
        else -> "€$v"
    }
}
