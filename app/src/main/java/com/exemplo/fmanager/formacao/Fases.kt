package com.exemplo.fmanager.formacao

import androidx.compose.runtime.*

/*
 * FORMAÇÃO POR FASE DE JOGO.
 *
 * Um jogador não ocupa uma posição — ele ocupa UMA POSIÇÃO POR FASE.
 * O mesmo homem pode ser terceiro zagueiro sem a bola e ala com a bola.
 *
 * É isso que permite montar o que você descreveu: sair de uma 4-2-3-1,
 * e com a posse o primeiro volante cair entre os zagueiros, os laterais
 * subirem para alas, o camisa 10 descer para a dupla de volantes e os
 * pontas estreitarem para perto do centroavante.
 *
 * O desenho tático de cada fase é DEDUZIDO das coordenadas, nunca
 * cadastrado. Você arrasta, o app nomeia. Se der 6-4-0 ou 5-5-0,
 * é porque foi isso que você desenhou.
 */

enum class Fase(val rotulo: String, val descricao: String) {
    SEM_POSSE("Sem a bola", "Como o time se organiza para defender"),
    TRANSICAO("Transição", "Os primeiros segundos após ganhar ou perder a bola"),
    COM_POSSE("Com a bola", "Como o time se estrutura para atacar"),
}

/** Posição de um jogador numa fase específica. */
@Stable
class PosicaoFase(x: Float, y: Float, papel: Papel) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var papel by mutableStateOf(papel)

    /** Trava o papel para a automação por zona não sobrescrever. */
    var papelTravado by mutableStateOf(false)

    fun copiar() = PosicaoFase(x, y, papel)
}

/**
 * Um dos 11 lugares do time, agora com uma posição por fase.
 *
 * Cada campo é observável separadamente, então mexer num jogador numa
 * fase não invalida os outros dez nem as outras fases.
 */
@Stable
class Slot(
    val id: Int,
    papelBase: Papel,
    x: Float,
    y: Float,
    nome: String = "",
) {
    var nome by mutableStateOf(nome)
    var jogadorId by mutableStateOf<Int?>(null)
    var instrucoes by mutableStateOf(Instrucoes())
    var estilo by mutableStateOf<EstiloJogador?>(null)

    private val posicoes = mutableStateMapOf<Fase, PosicaoFase>().apply {
        Fase.entries.forEach { put(it, PosicaoFase(x, y, papelBase)) }
    }

    fun em(fase: Fase): PosicaoFase = posicoes.getValue(fase)

    /** Move o jogador nesta fase, redefinindo o papel pela zona. */
    fun mover(fase: Fase, novoX: Float, novoY: Float) {
        val p = em(fase)
        p.x = novoX.coerceIn(.05f, .95f)
        p.y = novoY.coerceIn(.03f, .97f)
        if (!p.papelTravado) {
            val novo = papelPorZona(p.x, p.y)
            if (novo != p.papel) p.papel = novo
        }
    }

    /** Copia a posição de uma fase para outra — atalho útil quando você
     *  quer que a transição seja igual à fase com a bola, por exemplo. */
    fun copiarFase(de: Fase, para: Fase) {
        val origem = em(de)
        val destino = em(para)
        destino.x = origem.x
        destino.y = origem.y
        destino.papel = origem.papel
    }

    /** O papel principal do jogador — o da fase sem a bola, que é
     *  onde o time passa mais tempo organizado. */
    val papelPrincipal: Papel get() = em(Fase.SEM_POSSE).papel

    /** Quanto o jogador se desloca entre as duas fases extremas.
     *  Valor alto significa função muito exigente fisicamente. */
    val amplitudeDeMovimento: Float
        get() {
            val d = em(Fase.SEM_POSSE)
            val a = em(Fase.COM_POSSE)
            val dx = a.x - d.x
            val dy = a.y - d.y
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
}

/**
 * Deduz o desenho tático de uma fase a partir das coordenadas.
 *
 * A tolerância define o que conta como "mesma linha". Mais alta agrupa
 * mais, mais baixa separa mais. 0.12 dá nomes que batem com o que um
 * comentarista diria olhando o campo.
 */
fun desenhoTatico(
    slots: List<Slot>,
    fase: Fase,
    tolerancia: Float = 0.12f,
): String {
    val ys = slots
        .filter { it.em(fase).papel != Papel.GOL }
        .map { it.em(fase).y }
        .sorted()
    if (ys.isEmpty()) return "—"

    val linhas = mutableListOf<MutableList<Float>>()
    ys.forEach { y ->
        val ultima = linhas.lastOrNull()
        if (ultima != null && y - ultima.first() < tolerancia) ultima.add(y)
        else linhas.add(mutableListOf(y))
    }
    return linhas.joinToString("-") { it.size.toString() }
}

// ------------------------------------------------------- PRÉ-DEFINIDAS

data class Predefinida(
    val nome: String,
    val descricao: String,
    /** Por fase: lista de (papel, x, y) na ordem dos 11 slots. */
    val porFase: Map<Fase, List<Triple<Papel, Float, Float>>>,
) {
    fun criarSlots(): List<Slot> {
        val base = porFase.getValue(Fase.SEM_POSSE)
        return base.mapIndexed { i, (papel, x, y) ->
            Slot(i, papel, x, y, nome = "Jogador ${i + 1}").also { slot ->
                Fase.entries.forEach { fase ->
                    val lista = porFase[fase] ?: base
                    val (p, px, py) = lista.getOrElse(i) { base[i] }
                    slot.em(fase).apply { this.x = px; this.y = py; this.papel = p }
                }
            }
        }
    }
}

private fun mesmaEmTodasAsFases(
    lista: List<Triple<Papel, Float, Float>>,
): Map<Fase, List<Triple<Papel, Float, Float>>> =
    Fase.entries.associateWith { lista }

object Formacoes {

    private val base442 = listOf(
        Triple(Papel.GOL, .50f, .05f),
        Triple(Papel.LE, .15f, .25f), Triple(Papel.ZAG, .38f, .22f),
        Triple(Papel.ZAG, .62f, .22f), Triple(Papel.LD, .85f, .25f),
        Triple(Papel.ME, .15f, .50f), Triple(Papel.MC, .38f, .48f),
        Triple(Papel.MC, .62f, .48f), Triple(Papel.MD, .85f, .50f),
        Triple(Papel.ATA, .40f, .78f), Triple(Papel.ATA, .60f, .78f),
    )

    private val base4231 = listOf(
        Triple(Papel.GOL, .50f, .05f),
        Triple(Papel.LE, .14f, .26f), Triple(Papel.ZAG, .38f, .21f),
        Triple(Papel.ZAG, .62f, .21f), Triple(Papel.LD, .86f, .26f),
        Triple(Papel.VOL, .36f, .40f), Triple(Papel.VOL, .64f, .40f),
        Triple(Papel.ME, .16f, .62f), Triple(Papel.MEI, .50f, .63f),
        Triple(Papel.MD, .84f, .62f),
        Triple(Papel.ATA, .50f, .84f),
    )

    private val base433 = listOf(
        Triple(Papel.GOL, .50f, .05f),
        Triple(Papel.LE, .14f, .26f), Triple(Papel.ZAG, .38f, .21f),
        Triple(Papel.ZAG, .62f, .21f), Triple(Papel.LD, .86f, .26f),
        Triple(Papel.VOL, .50f, .40f), Triple(Papel.MC, .30f, .52f),
        Triple(Papel.MC, .70f, .52f),
        Triple(Papel.PE, .16f, .75f), Triple(Papel.ATA, .50f, .82f),
        Triple(Papel.PD, .84f, .75f),
    )

    val estaticas = listOf(
        Predefinida("4-4-2", "Duas linhas de quatro, clássico",
            mesmaEmTodasAsFases(base442)),
        Predefinida("4-2-3-1", "Dupla de volantes e um meia livre",
            mesmaEmTodasAsFases(base4231)),
        Predefinida("4-3-3", "Trio de meio e três na frente",
            mesmaEmTodasAsFases(base433)),
    )

    /**
     * EXATAMENTE O QUE VOCÊ DESCREVEU.
     *
     * Sem a bola é uma 4-2-3-1 normal. Com a bola:
     *   - o primeiro volante cai entre os zagueiros (vira o terceiro)
     *   - os dois laterais sobem para a linha dos alas
     *   - o camisa 10 desce e forma dupla com o segundo volante
     *   - os dois pontas estreitam para perto do centroavante
     *
     * Resultado: uma 3-2-5 com a bola, saindo de uma 4-2-3-1 sem ela.
     */
    val volanteQueVira = Predefinida(
        nome = "4-2-3-1 → 3-2-5",
        descricao = "Volante cai entre os zagueiros, laterais viram alas, " +
                "camisa 10 desce, pontas estreitam",
        porFase = mapOf(
            Fase.SEM_POSSE to base4231,

            Fase.TRANSICAO to listOf(
                Triple(Papel.GOL, .50f, .07f),
                Triple(Papel.LE, .14f, .38f), Triple(Papel.ZAG, .36f, .22f),
                Triple(Papel.ZAG, .64f, .22f), Triple(Papel.LD, .86f, .38f),
                Triple(Papel.ZAG, .50f, .24f), Triple(Papel.VOL, .50f, .44f),
                Triple(Papel.ME, .20f, .66f), Triple(Papel.VOL, .40f, .46f),
                Triple(Papel.MD, .80f, .66f),
                Triple(Papel.ATA, .50f, .86f),
            ),

            Fase.COM_POSSE to listOf(
                Triple(Papel.GOL, .50f, .10f),
                // laterais viram alas, colados na linha
                Triple(Papel.ME, .07f, .60f),
                Triple(Papel.ZAG, .30f, .24f),
                Triple(Papel.ZAG, .70f, .24f),
                Triple(Papel.MD, .93f, .60f),
                // primeiro volante cai e vira o terceiro zagueiro
                Triple(Papel.ZAG, .50f, .20f),
                // segundo volante segura o meio
                Triple(Papel.VOL, .60f, .46f),
                // ponta esquerda estreita
                Triple(Papel.PE, .28f, .80f),
                // camisa 10 desce e forma a dupla de volantes
                Triple(Papel.VOL, .40f, .46f),
                // ponta direita estreita
                Triple(Papel.PD, .72f, .80f),
                Triple(Papel.ATA, .50f, .88f),
            ),
        ),
    )

    /** Bloco baixíssimo sem a bola, explosão no contra-ataque. */
    val blocoBaixo = Predefinida(
        nome = "5-4-1 → 3-4-3",
        descricao = "Seis atrás da linha da bola sem posse, três na frente com ela",
        porFase = mapOf(
            Fase.SEM_POSSE to listOf(
                Triple(Papel.GOL, .50f, .04f),
                Triple(Papel.LE, .10f, .16f), Triple(Papel.ZAG, .30f, .13f),
                Triple(Papel.ZAG, .50f, .12f), Triple(Papel.ZAG, .70f, .13f),
                Triple(Papel.LD, .90f, .16f),
                Triple(Papel.ME, .18f, .34f), Triple(Papel.VOL, .40f, .32f),
                Triple(Papel.VOL, .60f, .32f), Triple(Papel.MD, .82f, .34f),
                Triple(Papel.ATA, .50f, .60f),
            ),
            Fase.TRANSICAO to listOf(
                Triple(Papel.GOL, .50f, .06f),
                Triple(Papel.LE, .12f, .34f), Triple(Papel.ZAG, .32f, .20f),
                Triple(Papel.ZAG, .50f, .18f), Triple(Papel.ZAG, .68f, .20f),
                Triple(Papel.LD, .88f, .34f),
                Triple(Papel.ME, .22f, .58f), Triple(Papel.VOL, .42f, .44f),
                Triple(Papel.VOL, .58f, .44f), Triple(Papel.MD, .78f, .58f),
                Triple(Papel.ATA, .50f, .82f),
            ),
            Fase.COM_POSSE to listOf(
                Triple(Papel.GOL, .50f, .10f),
                Triple(Papel.ME, .08f, .62f), Triple(Papel.ZAG, .30f, .26f),
                Triple(Papel.ZAG, .50f, .24f), Triple(Papel.ZAG, .70f, .26f),
                Triple(Papel.MD, .92f, .62f),
                Triple(Papel.PE, .26f, .80f), Triple(Papel.VOL, .40f, .48f),
                Triple(Papel.VOL, .60f, .48f), Triple(Papel.PD, .74f, .80f),
                Triple(Papel.ATA, .50f, .88f),
            ),
        ),
    )

    val comFases = listOf(volanteQueVira, blocoBaixo)

    val todas = estaticas + comFases
}
