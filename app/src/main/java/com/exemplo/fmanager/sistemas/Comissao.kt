package com.exemplo.fmanager.sistemas

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.pow
import kotlin.random.Random

/*
 * COMISSÃO TÉCNICA.
 *
 * Outra lacuna: o treino funcionava sozinho, como se o clube não tivesse
 * ninguém aplicando. Um clube pequeno e um gigante evoluíam jogadores na
 * mesma velocidade, o que apagava boa parte da diferença entre eles.
 *
 * Agora a comissão multiplica o que o treino rende, e contratar staff
 * compete por orçamento com contratar jogador. É uma escolha real: um
 * preparador físico bom pode valer mais que o quarto zagueiro.
 */

enum class Cargo(val rotulo: String, val efeito: String) {
    AUXILIAR("Auxiliar técnico",
        "Melhora o rendimento geral do trabalho de campo."),
    PREPARADOR_FISICO("Preparador físico",
        "Acelera ganho de resistência e reduz lesão por desgaste."),
    TREINADOR_DE_GOLEIROS("Treinador de goleiros",
        "Evolui os atributos de goleiro muito mais rápido."),
    ANALISTA("Analista de desempenho",
        "Melhora a precisão dos relatórios e das notas."),
    OLHEIRO_CHEFE("Olheiro-chefe",
        "Reduz o tempo para conhecer um jogador observado."),
    FISIOTERAPEUTA("Fisioterapeuta",
        "Encurta o tempo de recuperação de lesões."),
}

@Entity(tableName = "comissao", indices = [Index("clubeId")])
data class MembroComissao(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clubeId: Int = 0,
    val nome: String,
    val cargo: Cargo,
    /** 1 a 20, no estilo dos atributos de staff do FM. */
    val competencia: Int,
    val salarioSemanalEur: Long,
) {
    /** Multiplicador do efeito. Competência 10 é neutra. */
    val fator: Float get() = 0.80f + (competencia / 20f) * 0.45f

    val faixa: String get() = when {
        competencia >= 17 -> "Referência"
        competencia >= 13 -> "Muito bom"
        competencia >= 9 -> "Adequado"
        competencia >= 5 -> "Limitado"
        else -> "Amador"
    }
}

object Comissao {

    /**
     * Efeito da comissão sobre o treino.
     *
     * Sem auxiliar nenhum o time treina a 82% do que treinaria com uma
     * comissão completa e competente. Não zero — o jogador ainda evolui
     * jogando — mas a diferença ao longo de uma temporada é grande.
     */
    fun fatorDeTreino(membros: List<MembroComissao>, foco: FocoTreino): Float {
        val auxiliar = membros.firstOrNull { it.cargo == Cargo.AUXILIAR }
        val base = auxiliar?.fator ?: 0.82f

        val especialista = when (foco) {
            FocoTreino.FISICO -> membros
                .firstOrNull { it.cargo == Cargo.PREPARADOR_FISICO }
            FocoTreino.GOLEIRO -> membros
                .firstOrNull { it.cargo == Cargo.TREINADOR_DE_GOLEIROS }
            else -> null
        }

        // O especialista soma por cima do auxiliar, não substitui.
        return base * (especialista?.let { 1f + (it.fator - 0.8f) * 0.9f } ?: 1f)
    }

    /** Semanas de observação que o olheiro-chefe adianta. */
    fun bonusDeObservacao(membros: List<MembroComissao>): Int =
        membros.firstOrNull { it.cargo == Cargo.OLHEIRO_CHEFE }
            ?.let { if (it.competencia >= 14) 2 else 1 } ?: 0

    /** Redução no tempo de lesão, de 0 a 0,4. */
    fun reducaoDeLesao(membros: List<MembroComissao>): Float =
        membros.firstOrNull { it.cargo == Cargo.FISIOTERAPEUTA }
            ?.let { (it.competencia / 20f) * 0.40f } ?: 0f

    fun folhaSemanal(membros: List<MembroComissao>): Long =
        membros.sumOf { it.salarioSemanalEur }

    /**
     * Candidatos disponíveis para um cargo.
     *
     * A qualidade do que aparece depende da reputação do clube — clube
     * pequeno não atrai referência mundial, e é isso que faz subir de clube
     * significar algo além de orçamento maior.
     */
    fun candidatos(
        cargo: Cargo,
        reputacaoDoClube: Int,
        semente: Long,
    ): List<MembroComissao> {
        val rng = Random(semente + cargo.ordinal * 977L)
        val teto = (4 + (reputacaoDoClube / 100f) * 16).toInt().coerceIn(5, 20)

        return (1..4).map { i ->
            val competencia = rng.nextInt(
                (teto - 7).coerceAtLeast(1), (teto + 1).coerceAtMost(21))
            MembroComissao(
                nome = nomeSorteado(rng),
                cargo = cargo,
                competencia = competencia,
                // O salário sobe muito mais rápido que a competência.
                salarioSemanalEur =
                    ((competencia / 10.0).pow(2.6) * 2_400).toLong()
                        .coerceAtLeast(600),
            )
        }.sortedByDescending { it.competencia }
    }

    private val PRENOMES = listOf(
        "Aldo", "Bruno", "Caio", "Décio", "Elias", "Fábio", "Gilmar",
        "Hélio", "Ivan", "Jorge", "Lauro", "Márcio", "Nélson", "Otávio",
        "Paulo", "Renan", "Sérgio", "Tarcísio", "Ubirajara", "Valter",
    )
    private val SOBRENOMES = listOf(
        "Amaral", "Bastos", "Cordeiro", "Duarte", "Esteves", "Fontes",
        "Guedes", "Horta", "Ibrahim", "Junqueira", "Lacerda", "Moraes",
        "Nogueira", "Oliveira", "Pacheco", "Queirós", "Rangel", "Siqueira",
        "Teixeira", "Vasques",
    )

    private fun nomeSorteado(rng: Random) =
        "${PRENOMES.random(rng)} ${SOBRENOMES.random(rng)}"
}
