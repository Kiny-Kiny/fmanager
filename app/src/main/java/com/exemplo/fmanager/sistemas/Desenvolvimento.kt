package com.exemplo.fmanager.sistemas

import androidx.room.Entity
import androidx.room.Index
import com.exemplo.fmanager.dados.Jogador

/*
 * DESENVOLVIMENTO DO ELENCO — a melhor ideia do moneyball-mentality.
 *
 * Aquele projeto tem um recurso que eu não tinha visto em nenhum outro:
 * você tira um retrato do elenco numa temporada, outro na seguinte, e ele
 * mostra quem evoluiu e quanto, atributo por atributo.
 *
 * Aqui isso vira automático. Ao virar a temporada, o jogo guarda um
 * retrato de cada jogador. Depois você consegue responder perguntas que
 * antes eram invisíveis: o treino focado em ritmo funcionou? aquele
 * garoto de 18 anos cresceu ou estagnou? quem começou a cair?
 *
 * É a diferença entre saber o overall de hoje e entender a TRAJETÓRIA.
 */

@Entity(
    tableName = "retratos",
    primaryKeys = ["jogadorId", "temporada"],
    indices = [Index("temporada")],
)
data class RetratoJogador(
    val jogadorId: Int,
    val temporada: Int,
    val idade: Int,
    val geral: Int,
    val potencial: Int,
    /** Atributos serializados na ordem de Atributos.todos. */
    val atributos: String,
) {
    fun valores(): List<Int> =
        atributos.split(",").map { it.toIntOrNull() ?: 0 }

    companion object {
        fun de(j: Jogador, temporada: Int) = RetratoJogador(
            jogadorId = j.id,
            temporada = temporada,
            idade = j.idade,
            geral = j.geral,
            potencial = j.potencial,
            atributos = Atributos.todos
                .map { Atributos.ler(j, it) }
                .joinToString(","),
        )
    }
}

data class VariacaoAtributo(
    val nome: String,
    val antes: Int,
    val agora: Int,
) {
    val delta get() = agora - antes
}

data class Desenvolvimento(
    val jogadorId: Int,
    val nome: String,
    val urlFoto: String?,
    val idadeAntes: Int,
    val idadeAgora: Int,
    val geralAntes: Int,
    val geralAgora: Int,
    val variacoes: List<VariacaoAtributo>,
) {
    val deltaGeral get() = geralAgora - geralAntes

    /** Onde ele mais cresceu. Confirma se o treino focado funcionou. */
    val maioresGanhos: List<VariacaoAtributo>
        get() = variacoes.filter { it.delta > 0 }
            .sortedByDescending { it.delta }.take(4)

    /** Onde ele está caindo. É o sinal de que a idade chegou. */
    val maioresPerdas: List<VariacaoAtributo>
        get() = variacoes.filter { it.delta < 0 }
            .sortedBy { it.delta }.take(4)

    val tendencia: Tendencia get() = when {
        deltaGeral >= 4 -> Tendencia.EXPLODINDO
        deltaGeral >= 1 -> Tendencia.CRESCENDO
        deltaGeral == 0 -> Tendencia.ESTAVEL
        deltaGeral >= -2 -> Tendencia.CAINDO
        else -> Tendencia.DESPENCANDO
    }
}

enum class Tendencia(val rotulo: String, val seta: String) {
    EXPLODINDO("Explodindo", "▲▲"),
    CRESCENDO("Crescendo", "▲"),
    ESTAVEL("Estável", "="),
    CAINDO("Caindo", "▼"),
    DESPENCANDO("Despencando", "▼▼"),
}

object AnaliseDesenvolvimento {

    /**
     * Compara dois retratos do mesmo jogador.
     *
     * Devolve null quando falta um dos dois — na primeira temporada não
     * existe passado com que comparar, e é melhor mostrar nada do que
     * inventar uma linha de base falsa.
     */
    fun comparar(
        jogador: Jogador,
        antes: RetratoJogador?,
        agora: RetratoJogador?,
    ): Desenvolvimento? {
        if (antes == null || agora == null) return null
        val va = antes.valores()
        val vb = agora.valores()

        val variacoes = Atributos.todos.mapIndexedNotNull { i, nome ->
            val a = va.getOrNull(i) ?: return@mapIndexedNotNull null
            val b = vb.getOrNull(i) ?: return@mapIndexedNotNull null
            VariacaoAtributo(nome, a, b)
        }

        return Desenvolvimento(
            jogadorId = jogador.id,
            nome = jogador.nome,
            urlFoto = jogador.urlFoto,
            idadeAntes = antes.idade,
            idadeAgora = agora.idade,
            geralAntes = antes.geral,
            geralAgora = agora.geral,
            variacoes = variacoes,
        )
    }

    /**
     * Resumo em texto do que aconteceu com o elenco.
     *
     * Aparece na caixa de entrada na virada da temporada, porque é
     * exatamente o tipo de coisa que um diretor de futebol reportaria.
     */
    fun resumir(lista: List<Desenvolvimento>): String {
        if (lista.isEmpty()) return "Sem histórico para comparar ainda."
        val subiram = lista.count { it.deltaGeral > 0 }
        val cairam = lista.count { it.deltaGeral < 0 }
        val destaque = lista.maxByOrNull { it.deltaGeral }

        return buildString {
            append("$subiram jogadores evoluíram e $cairam regrediram. ")
            destaque?.takeIf { it.deltaGeral > 0 }?.let {
                append("${it.nome} foi o que mais cresceu: ")
                append("${it.geralAntes} para ${it.geralAgora}")
                it.maioresGanhos.firstOrNull()?.let { g ->
                    append(", puxado por ${g.nome.lowercase()} (+${g.delta})")
                }
                append(".")
            }
        }
    }
}
