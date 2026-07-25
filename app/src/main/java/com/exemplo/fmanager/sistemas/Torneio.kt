package com.exemplo.fmanager.sistemas

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.LinhaTabela
import com.exemplo.fmanager.dados.Partida
import kotlin.math.log2
import kotlin.random.Random

/*
 * TORNEIOS CUSTOMIZADOS.
 *
 * O que faltava: eu tinha liga de pontos corridos e copa de eliminatória
 * pura, mas não o formato mais reconhecível de todos — FASE DE GRUPOS
 * seguida de ELIMINATÓRIA. É o desenho da Champions, da Copa do Mundo e
 * de praticamente todo torneio de eFootball que as pessoas organizam.
 *
 * Junto com ele vem o que dá sabor: o SORTEIO COM POTES. Times fortes não
 * podem cair todos no mesmo grupo, então eles são distribuídos por
 * reputação — pote 1 espalha um cabeça por grupo, e daí para baixo.
 *
 * As rodadas usam faixas próprias para não colidir na tabela de partidas:
 *   2001+ fase de grupos
 *   3001+ eliminatória
 */

@Entity(
    tableName = "torneios",
    indices = [Index("temporada")],
)
data class Torneio(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val temporada: Int,
    val formato: String,
    /** Ids dos clubes participantes, separados por vírgula. */
    val clubes: String,
    /** Composição dos grupos: grupos separados por ';', ids por ','. */
    val grupos: String = "",
    val quantosPassam: Int = 2,
    val campeaoId: Int? = null,
) {
    fun idsClubes(): List<Int> =
        clubes.split(",").mapNotNull { it.trim().toIntOrNull() }

    fun gruposMontados(): List<List<Int>> =
        if (grupos.isBlank()) emptyList()
        else grupos.split(";").map { g ->
            g.split(",").mapNotNull { it.trim().toIntOrNull() }
        }

    /** Faixa de ligaId reservada para este torneio na tabela de partidas. */
    val ligaIdVirtual: Int get() = 9100 + id
}

enum class FormatoTorneio(val rotulo: String, val descricao: String) {
    GRUPOS_E_ELIMINATORIA("Grupos e eliminatória",
        "Fase de grupos, os melhores avançam para o mata-mata."),
    ELIMINATORIA("Eliminatória direta",
        "Jogo único desde a primeira fase. Quem perde, sai."),
    PONTOS_CORRIDOS("Pontos corridos",
        "Todos contra todos, turno único. Quem somar mais pontos leva."),
}

object Torneios {

    const val BASE_GRUPOS = 2000
    const val BASE_ELIMINATORIA = 3000

    // ------------------------------------------------------- SORTEIO

    /**
     * Sorteio com potes.
     *
     * Ordena por reputação, corta em tantos potes quantos grupos existem,
     * e distribui um de cada pote por grupo. É por isso que não sai um
     * grupo com quatro gigantes e outro com quatro fracos — o problema
     * óbvio de um sorteio puramente aleatório.
     */
    fun sortearGrupos(
        clubes: List<Clube>,
        quantidadeDeGrupos: Int,
        semente: Long = System.currentTimeMillis(),
    ): List<List<Clube>> {
        if (clubes.size < quantidadeDeGrupos * 2) return emptyList()
        val rng = Random(semente)

        val porForca = clubes.sortedByDescending { it.reputacao }
        val porGrupo = clubes.size / quantidadeDeGrupos
        val aproveitados = porGrupo * quantidadeDeGrupos

        // Cada pote embaralhado por dentro: a força fica equilibrada
        // entre grupos, mas o adversário exato ainda é surpresa.
        val potes = (0 until porGrupo).map { p ->
            porForca.subList(p * quantidadeDeGrupos, (p + 1) * quantidadeDeGrupos)
                .shuffled(rng)
                .toMutableList()
        }

        return (0 until quantidadeDeGrupos).map { g ->
            potes.map { pote -> pote[g] }
        }.also {
            // Sobras (quando não divide exato) ficam de fora, e é melhor
            // avisar isso na tela do que inventar um grupo desigual.
            if (aproveitados < clubes.size) Unit
        }
    }

    // --------------------------------------------------- CALENDÁRIO

    /** Todos contra todos dentro de cada grupo, turno único. */
    fun jogosDaFaseDeGrupos(
        grupos: List<List<Int>>,
        ligaIdVirtual: Int,
        temporada: Int,
    ): List<Partida> = buildList {
        grupos.forEachIndexed { indiceGrupo, grupo ->
            var rodada = 1
            // Rodízio simples: com N times dá N-1 rodadas.
            for (i in grupo.indices) {
                for (j in i + 1 until grupo.size) {
                    add(Partida(
                        temporada = temporada,
                        // O índice do grupo entra na rodada para as
                        // partidas de grupos diferentes não se misturarem.
                        rodada = BASE_GRUPOS + indiceGrupo * 20 + rodada,
                        ligaId = ligaIdVirtual,
                        mandanteId = grupo[i],
                        visitanteId = grupo[j],
                    ))
                    rodada++
                }
            }
        }
    }

    /** Classificação de um grupo. */
    fun tabelaDoGrupo(
        grupo: List<Clube>,
        partidas: List<Partida>,
    ): List<LinhaTabela> {
        val ids = grupo.map { it.id }.toSet()
        val doGrupo = partidas.filter {
            it.mandanteId in ids && it.visitanteId in ids
        }
        return Temporada.classificacao(grupo, doGrupo)
    }

    /**
     * Quem avança. Pega os N primeiros de cada grupo e ordena por
     * desempenho para o chaveamento cruzar primeiro contra segundo.
     */
    fun classificados(
        grupos: List<List<Clube>>,
        partidas: List<Partida>,
        quantosPassam: Int,
    ): List<Int> {
        val primeiros = mutableListOf<Int>()
        val segundos = mutableListOf<Int>()

        grupos.forEach { grupo ->
            val tabela = tabelaDoGrupo(grupo, partidas)
            tabela.take(quantosPassam).forEachIndexed { pos, linha ->
                if (pos == 0) primeiros += linha.clubeId else segundos += linha.clubeId
            }
        }

        // Primeiro de grupo encara segundo de outro: os líderes ficam nas
        // pontas do chaveamento e só se encontram mais tarde.
        return buildList {
            primeiros.forEachIndexed { i, p ->
                add(p)
                segundos.getOrNull(segundos.size - 1 - i)?.let { add(it) }
            }
        }.distinct()
    }

    /** Primeira fase eliminatória a partir dos classificados. */
    fun primeiraEliminatoria(
        classificados: List<Int>,
        ligaIdVirtual: Int,
        temporada: Int,
    ): List<Partida> {
        val cabem = potenciaDeDoisAbaixo(classificados.size)
        if (cabem < 2) return emptyList()
        val vaoJogar = classificados.take(cabem)

        return (0 until cabem / 2).map { i ->
            Partida(
                temporada = temporada,
                rodada = BASE_ELIMINATORIA + 1,
                ligaId = ligaIdVirtual,
                mandanteId = vaoJogar[i],
                visitanteId = vaoJogar[cabem - 1 - i],
            )
        }
    }

    /** Fase seguinte a partir dos vencedores. Reusa a lógica da copa. */
    fun proximaEliminatoria(
        daFase: List<Partida>,
        ligaIdVirtual: Int,
        temporada: Int,
    ): List<Partida> {
        if (daFase.isEmpty() || daFase.any { it.golsMandante == null })
            return emptyList()
        val vencedores = daFase.map { Copa.vencedorDe(it) }
        if (vencedores.size < 2) return emptyList()

        return vencedores.chunked(2).mapNotNull { par ->
            if (par.size < 2) null
            else Partida(
                temporada = temporada,
                rodada = daFase.first().rodada + 1,
                ligaId = ligaIdVirtual,
                mandanteId = par[0],
                visitanteId = par[1],
            )
        }
    }

    /** Campeão, quando a final já foi decidida. */
    fun campeao(partidas: List<Partida>): Int? {
        val eliminatorias = partidas.filter { it.rodada > BASE_ELIMINATORIA }
        if (eliminatorias.isEmpty()) return null
        val ultimaFase = eliminatorias.filter {
            it.rodada == eliminatorias.maxOf { p -> p.rodada }
        }
        if (ultimaFase.size != 1) return null
        val final = ultimaFase.first()
        if (final.golsMandante == null) return null
        return Copa.vencedorDe(final)
    }

    fun nomeDaFase(confrontos: Int): String = Copa.nomeDaFase(confrontos)

    private fun potenciaDeDoisAbaixo(n: Int): Int {
        if (n < 2) return 0
        var p = 2
        while (p * 2 <= n) p *= 2
        return p
    }

    /** Quantos grupos fazem sentido para uma quantidade de clubes. */
    fun gruposSugeridos(quantidade: Int): List<Int> = when {
        quantidade >= 32 -> listOf(8, 4)
        quantidade >= 24 -> listOf(6, 4)
        quantidade >= 16 -> listOf(4, 8)
        quantidade >= 8 -> listOf(2, 4)
        else -> listOf(1)
    }
}

// ------------------------------------------------------------ PALMARÉS

/*
 * PALMARÉS.
 *
 * Torneio ganho tem que ficar registrado em algum lugar, senão vencer não
 * significa nada na temporada seguinte. Guardo o mínimo: o que, quando e
 * com quem.
 */
@Entity(
    tableName = "titulos",
    indices = [Index("clubeId"), Index("temporada")],
)
data class Titulo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clubeId: Int,
    val nomeDaCompeticao: String,
    val temporada: Int,
    /** "liga", "copa" ou "torneio". */
    val tipo: String,
)


/*
 * INSCRIÇÃO DE ELENCO — mecânica real da UEFA e da CBF.
 *
 * Antes o clube entrava no torneio com o elenco inteiro. Na prática existe
 * uma LISTA FECHADA: você inscreve N jogadores para aquela competição, e
 * quem ficou de fora não joga nem em caso de emergência.
 *
 * Isso muda decisões de verdade: contratar no meio do torneio não resolve
 * nada se a lista já está cheia, e deixar o garoto de fora para inscrever
 * o veterano é uma escolha com consequência.
 */
@Entity(
    tableName = "inscricoes",
    primaryKeys = ["torneioId", "jogadorId"],
    indices = [Index("torneioId"), Index("clubeId")],
)
data class Inscricao(
    val torneioId: Int,
    val clubeId: Int,
    val jogadorId: Int,
)

object Inscricoes {
    /** Limite padrão, no espírito das listas continentais. */
    const val VAGAS = 23

    /** Mínimo para o time ser escalável de fato. */
    const val MINIMO = 14
}
