package com.exemplo.fmanager.sistemas

import androidx.room.Entity
import androidx.room.Index
import com.exemplo.fmanager.dados.Jogador
import kotlin.math.absoluteValue

/*
 * NEVOEIRO DE OBSERVAÇÃO — ideia do PyScoutFM.
 *
 * No PyScoutFM o autor lida com "attribute masking": jogador não
 * observado aparece com faixa (7-11) ou com traço, e a ferramenta usa o
 * pior valor. Isso me fez perceber uma lacuna grande aqui: no meu jogo
 * você vê os 29 atributos exatos de todos os 16 mil jogadores do mundo,
 * de graça. Não existe trabalho de olheiro nenhum.
 *
 * Agora existe. Jogador desconhecido mostra FAIXA, não número. Observar
 * custa tempo e dinheiro e vai estreitando a faixa até o valor exato.
 *
 * A faixa é DETERMINÍSTICA: derivada do id do jogador e do atributo, não
 * sorteada a cada abertura de tela. Sem isso o número dançaria cada vez
 * que você olhasse, o que quebraria a ilusão e a confiança na informação.
 */

@Entity(
    tableName = "observacoes",
    primaryKeys = ["jogadorId"],
    indices = [Index("nivel")],
)
data class Observacao(
    val jogadorId: Int,
    /** 0 = nunca observado · 4 = totalmente conhecido. */
    val nivel: Int = 0,
    /** Semanas de observação acumuladas. */
    val semanas: Int = 0,
)

enum class NivelObservacao(
    val rotulo: String,
    val margem: Int,
    val custoSemanal: Long,
) {
    DESCONHECIDO("Não observado", 14, 0),
    SUPERFICIAL("Observação inicial", 9, 8_000),
    PARCIAL("Relatório parcial", 5, 14_000),
    BOM("Relatório detalhado", 2, 22_000),
    COMPLETO("Totalmente conhecido", 0, 0);

    companion object {
        fun de(nivel: Int) = entries.getOrElse(nivel.coerceIn(0, 4)) { DESCONHECIDO }
    }
}

/** Um atributo como o olheiro o conhece: exato, ou uma faixa. */
data class AtributoObservado(
    val minimo: Int,
    val maximo: Int,
) {
    val exato: Boolean get() = minimo == maximo
    val estimado: Int get() = (minimo + maximo) / 2

    override fun toString() =
        if (exato) "$minimo" else "$minimo-$maximo"
}

object Olheiro {

    /**
     * Faixa em que o valor real cai, dado o nível de conhecimento.
     *
     * O deslocamento vem de um hash do (id, atributo), então a faixa é
     * sempre a mesma para o mesmo jogador — e nunca é centrada de
     * propósito, porque um olheiro que erra sempre para o mesmo lado
     * seria mais previsível do que a realidade.
     */
    fun observar(jogador: Jogador, atributo: String, nivel: Int): AtributoObservado {
        val real = Atributos.ler(jogador, atributo)
        val margem = NivelObservacao.de(nivel).margem
        if (margem == 0) return AtributoObservado(real, real)

        val semente = (jogador.id * 31 + atributo.hashCode()).absoluteValue
        val desloca = (semente % (margem + 1)) - margem / 2

        val centro = (real + desloca).coerceIn(1, 99)
        return AtributoObservado(
            minimo = (centro - margem / 2).coerceIn(1, 99),
            maximo = (centro + margem / 2).coerceIn(1, 99),
        )
    }

    /** Overall como o olheiro estima. Fora do seu clube, é aproximado. */
    fun geralObservado(jogador: Jogador, nivel: Int): AtributoObservado {
        val margem = NivelObservacao.de(nivel).margem / 2
        if (margem == 0) return AtributoObservado(jogador.geral, jogador.geral)
        return AtributoObservado(
            (jogador.geral - margem).coerceIn(1, 99),
            (jogador.geral + margem).coerceIn(1, 99),
        )
    }

    /**
     * Potencial é o dado mais difícil de saber, e no jogo é o mais
     * valioso. Só aparece de verdade no relatório completo.
     */
    fun potencialObservado(jogador: Jogador, nivel: Int): String = when (nivel) {
        0 -> "?"
        1 -> if (jogador.potencial - jogador.geral >= 8) "pode crescer" else "?"
        2 -> if (jogador.potencial - jogador.geral >= 8)
            "margem interessante" else "perto do teto"
        3 -> {
            val faixa = (jogador.potencial / 5) * 5
            "$faixa-${faixa + 5}"
        }
        else -> "${jogador.potencial}"
    }

    /**
     * Uma semana de observação. Níveis altos custam mais e demoram mais,
     * então observar o mundo inteiro não é viável — é preciso escolher.
     */
    fun avancarUmaSemana(atual: Observacao): Observacao {
        val semanas = atual.semanas + 1
        val novoNivel = when {
            semanas >= 10 -> 4
            semanas >= 6 -> 3
            semanas >= 3 -> 2
            semanas >= 1 -> 1
            else -> 0
        }
        return atual.copy(nivel = maxOf(atual.nivel, novoNivel), semanas = semanas)
    }

    fun custoDe(observacoes: List<Observacao>): Long =
        observacoes.sumOf { NivelObservacao.de(it.nivel).custoSemanal }

    /**
     * Confiança do relatório, de 0 a 100. É isso que a tela mostra em
     * vez de fingir precisão que o olheiro não tem.
     */
    fun confianca(nivel: Int): Int = when (nivel) {
        0 -> 10
        1 -> 35
        2 -> 60
        3 -> 82
        else -> 100
    }
}
