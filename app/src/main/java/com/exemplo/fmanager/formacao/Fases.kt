package com.exemplo.fmanager.formacao

import androidx.compose.runtime.*

/*
 * FASES DE JOGO E BIBLIOTECA DE FORMAÇÕES.
 *
 * A posição base de cada jogador é a de SEM_POSSE — a organização
 * defensiva. As outras duas fases são CALCULADAS a partir dela mais o
 * comportamento da função:
 *
 *   COM_POSSE = base + deslocamento cheio
 *   TRANSICAO = base + metade do deslocamento
 *
 * Você continua livre para arrastar qualquer jogador em qualquer fase e
 * sobrescrever o cálculo. Mas o padrão nunca é o desenho preferido de
 * ninguém: sai da função que o jogador exerce.
 */

enum class Fase(val rotulo: String, val descricao: String) {
    SEM_POSSE("Sem a bola", "Como o time se organiza para defender"),
    TRANSICAO("Transição", "Os primeiros segundos após ganhar ou perder a bola"),
    COM_POSSE("Com a bola", "Como o time se estrutura para atacar"),
}

@Stable
class PosicaoFase(x: Float, y: Float, papel: Papel) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var papel by mutableStateOf(papel)
    var papelTravado by mutableStateOf(false)
}

@Stable
class Slot(
    val id: Int,
    papelBase: Papel,
    x: Float,
    y: Float,
    nome: String = "",
    comportamento: Comportamento = Comportamento.padraoDe(papelBase),
) {
    var nome by mutableStateOf(nome)
    var jogadorId by mutableStateOf<Int?>(null)
    var instrucoes by mutableStateOf(Instrucoes())
    var estilo by mutableStateOf<EstiloJogador?>(null)

    private val posicoes = mutableStateMapOf<Fase, PosicaoFase>().apply {
        Fase.entries.forEach { put(it, PosicaoFase(x, y, papelBase)) }
    }

    /** Muda o comportamento e recalcula as fases ofensivas na hora. */
    var comportamento by mutableStateOf(comportamento)
        private set

    init { recalcularFases() }

    fun em(fase: Fase): PosicaoFase = posicoes.getValue(fase)

    fun definirComportamento(novo: Comportamento) {
        comportamento = novo
        recalcularFases()
    }

    /** Recalcula transição e posse a partir da base defensiva. */
    fun recalcularFases() {
        val base = em(Fase.SEM_POSSE)
        listOf(Fase.TRANSICAO to 0.5f, Fase.COM_POSSE to 1f).forEach { (f, i) ->
            val (nx, ny) = deslocar(base.x, base.y, comportamento, i)
            val destino = em(f)
            destino.x = nx
            destino.y = ny
            if (!destino.papelTravado) destino.papel = papelPorZona(nx, ny)
        }
    }

    /**
     * Move na fase pedida. Mexer na base defensiva reposiciona as outras
     * duas junto, mantendo o movimento coerente; mexer numa fase
     * ofensiva ajusta só ela, que é a sobrescrita manual.
     */
    fun mover(fase: Fase, novoX: Float, novoY: Float) {
        val p = em(fase)
        p.x = novoX.coerceIn(.05f, .95f)
        p.y = novoY.coerceIn(.03f, .97f)
        if (!p.papelTravado) {
            val novo = papelPorZona(p.x, p.y)
            if (novo != p.papel) p.papel = novo
        }
        if (fase == Fase.SEM_POSSE) recalcularFases()
    }

    fun copiarFase(de: Fase, para: Fase) {
        val origem = em(de)
        em(para).apply { x = origem.x; y = origem.y; papel = origem.papel }
    }

    val papelPrincipal: Papel get() = em(Fase.SEM_POSSE).papel

    val amplitudeDeMovimento: Float
        get() {
            val d = em(Fase.SEM_POSSE)
            val a = em(Fase.COM_POSSE)
            return kotlin.math.hypot(a.x - d.x, a.y - d.y)
        }
}

/** Deduz o desenho tático de uma fase a partir das coordenadas. */
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

// --------------------------------------------------------- BIBLIOTECA

/** Um lugar na formação: função, posição base e comportamento sugerido. */
data class Posto(
    val papel: Papel,
    val x: Float,
    val y: Float,
    val comportamento: Comportamento = Comportamento.padraoDe(papel),
)

data class Formacao(
    val nome: String,
    val descricao: String,
    val familia: Familia,
    val postos: List<Posto>,
) {
    fun criarSlots(): List<Slot> = postos.mapIndexed { i, p ->
        Slot(i, p.papel, p.x, p.y, "Jogador ${i + 1}", p.comportamento)
    }
}

enum class Familia(val rotulo: String) {
    QUATRO("Linha de 4"),
    TRES("Linha de 3"),
    CINCO("Linha de 5"),
}

/**
 * Biblioteca de formações no espírito do Football Manager: o desenho
 * base, e o comportamento sugerido de cada função. Nenhuma tem fase
 * ofensiva escrita à mão — tudo é calculado.
 */
object Formacoes {

    private fun gol(y: Float = .05f) = Posto(Papel.GOL, .50f, y)

    // ---------------------------------------------------- LINHA DE 4

    val f442 = Formacao("4-4-2", "Duas linhas de quatro. Simples e sólido.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .24f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .24f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ME, .14f, .50f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.MC, .38f, .47f), Posto(Papel.MC, .62f, .47f),
            Posto(Papel.MD, .86f, .50f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.ATA, .41f, .78f), Posto(Papel.ATA, .59f, .78f),
        ))

    val f442Diamante = Formacao("4-4-2 diamante",
        "Meio em losango, um armador atrás dos atacantes.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .26f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .26f, Comportamento.SOBE_ALA),
            Posto(Papel.VOL, .50f, .36f, Comportamento.PIVO),
            Posto(Papel.MC, .28f, .50f), Posto(Papel.MC, .72f, .50f),
            Posto(Papel.MEI, .50f, .64f, Comportamento.APOIA_ATAQUE),
            Posto(Papel.ATA, .41f, .82f), Posto(Papel.ATA, .59f, .82f),
        ))

    val f4231 = Formacao("4-2-3-1",
        "Dupla de volantes e três criadores atrás do camisa 9.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .26f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .26f, Comportamento.SOBE_ALA),
            Posto(Papel.VOL, .37f, .40f, Comportamento.PIVO),
            Posto(Papel.VOL, .63f, .40f, Comportamento.PIVO),
            Posto(Papel.ME, .15f, .61f, Comportamento.ESTREITA),
            Posto(Papel.MEI, .50f, .63f, Comportamento.APOIA_ATAQUE),
            Posto(Papel.MD, .85f, .61f, Comportamento.ESTREITA),
            Posto(Papel.ATA, .50f, .83f),
        ))

    val f433Segurando = Formacao("4-3-3 com volante",
        "Um volante fixo e dois meias por dentro.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .26f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .26f, Comportamento.SOBE_ALA),
            Posto(Papel.VOL, .50f, .39f, Comportamento.PIVO),
            Posto(Papel.MC, .31f, .52f), Posto(Papel.MC, .69f, .52f),
            Posto(Papel.PE, .15f, .74f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.ATA, .50f, .82f),
            Posto(Papel.PD, .85f, .74f, Comportamento.COLA_NA_LINHA),
        ))

    val f433Ofensiva = Formacao("4-3-3 ofensiva",
        "Pontas por dentro, laterais dando a largura.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .13f, .27f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .38f, .22f, Comportamento.SAI_JOGANDO),
            Posto(Papel.ZAG, .62f, .22f, Comportamento.SAI_JOGANDO),
            Posto(Papel.LD, .87f, .27f, Comportamento.SOBE_ALA),
            Posto(Papel.VOL, .50f, .42f, Comportamento.PIVO),
            Posto(Papel.MC, .32f, .56f, Comportamento.CHEGA_NA_AREA),
            Posto(Papel.MC, .68f, .56f, Comportamento.CHEGA_NA_AREA),
            Posto(Papel.PE, .17f, .76f, Comportamento.ESTREITA),
            Posto(Papel.ATA, .50f, .84f, Comportamento.ATACA_PROFUNDIDADE),
            Posto(Papel.PD, .83f, .76f, Comportamento.ESTREITA),
        ))

    val f4141 = Formacao("4-1-4-1",
        "Bloco compacto com um volante isolado à frente da zaga.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .25f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .25f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.VOL, .50f, .37f, Comportamento.PIVO),
            Posto(Papel.ME, .14f, .57f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.MC, .38f, .55f), Posto(Papel.MC, .62f, .55f),
            Posto(Papel.MD, .86f, .57f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.ATA, .50f, .80f),
        ))

    val f4411 = Formacao("4-4-1-1",
        "Um segundo atacante recuado ligando o meio ao ataque.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .25f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .25f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ME, .14f, .48f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.MC, .38f, .45f), Posto(Papel.MC, .62f, .45f),
            Posto(Papel.MD, .86f, .48f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.MEI, .50f, .66f, Comportamento.APOIA_ATAQUE),
            Posto(Papel.ATA, .50f, .84f),
        ))

    val f41212 = Formacao("4-1-2-1-2 estreito",
        "Sem pontas. Tudo pelo miolo, laterais dão a largura.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .12f, .27f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .88f, .27f, Comportamento.SOBE_ALA),
            Posto(Papel.VOL, .50f, .37f, Comportamento.PIVO),
            Posto(Papel.MC, .34f, .52f), Posto(Papel.MC, .66f, .52f),
            Posto(Papel.MEI, .50f, .66f, Comportamento.APOIA_ATAQUE),
            Posto(Papel.ATA, .41f, .82f), Posto(Papel.ATA, .59f, .82f),
        ))

    val f4222 = Formacao("4-2-2-2",
        "Dois volantes, dois meias por fora e dois atacantes.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .26f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .26f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.VOL, .37f, .40f, Comportamento.PIVO),
            Posto(Papel.VOL, .63f, .40f, Comportamento.PIVO),
            Posto(Papel.ME, .18f, .62f, Comportamento.ESTREITA),
            Posto(Papel.MD, .82f, .62f, Comportamento.ESTREITA),
            Posto(Papel.ATA, .41f, .82f), Posto(Papel.ATA, .59f, .82f),
        ))

    val f451 = Formacao("4-5-1",
        "Cinco no meio para dominar a posse e sufocar o adversário.",
        Familia.QUATRO, listOf(
            gol(),
            Posto(Papel.LE, .14f, .25f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ZAG, .38f, .21f), Posto(Papel.ZAG, .62f, .21f),
            Posto(Papel.LD, .86f, .25f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ME, .13f, .52f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.VOL, .34f, .44f, Comportamento.PIVO),
            Posto(Papel.MC, .50f, .50f),
            Posto(Papel.MC, .66f, .52f, Comportamento.CHEGA_NA_AREA),
            Posto(Papel.MD, .87f, .52f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.ATA, .50f, .79f),
        ))

    // ---------------------------------------------------- LINHA DE 3

    val f352 = Formacao("3-5-2",
        "Três zagueiros e alas cobrindo os corredores inteiros.",
        Familia.TRES, listOf(
            gol(),
            Posto(Papel.ZAG, .28f, .20f), Posto(Papel.ZAG, .50f, .18f),
            Posto(Papel.ZAG, .72f, .20f),
            Posto(Papel.ME, .10f, .48f, Comportamento.SOBE_ALA),
            Posto(Papel.MC, .34f, .46f), Posto(Papel.VOL, .50f, .38f,
                Comportamento.PIVO),
            Posto(Papel.MC, .66f, .46f, Comportamento.CHEGA_NA_AREA),
            Posto(Papel.MD, .90f, .48f, Comportamento.SOBE_ALA),
            Posto(Papel.ATA, .41f, .80f), Posto(Papel.ATA, .59f, .80f),
        ))

    val f343 = Formacao("3-4-3",
        "Ousada: três atrás, quatro no meio, três na frente.",
        Familia.TRES, listOf(
            gol(),
            Posto(Papel.ZAG, .28f, .22f, Comportamento.SAI_JOGANDO),
            Posto(Papel.ZAG, .50f, .19f), Posto(Papel.ZAG, .72f, .22f,
                Comportamento.SAI_JOGANDO),
            Posto(Papel.ME, .11f, .52f, Comportamento.SOBE_ALA),
            Posto(Papel.MC, .37f, .48f), Posto(Papel.MC, .63f, .48f),
            Posto(Papel.MD, .89f, .52f, Comportamento.SOBE_ALA),
            Posto(Papel.PE, .22f, .78f, Comportamento.ESTREITA),
            Posto(Papel.ATA, .50f, .84f),
            Posto(Papel.PD, .78f, .78f, Comportamento.ESTREITA),
        ))

    val f3142 = Formacao("3-1-4-2",
        "Um cão de guarda entre a zaga de três e o meio.",
        Familia.TRES, listOf(
            gol(),
            Posto(Papel.ZAG, .28f, .20f), Posto(Papel.ZAG, .50f, .18f),
            Posto(Papel.ZAG, .72f, .20f),
            Posto(Papel.VOL, .50f, .34f, Comportamento.SEGURA),
            Posto(Papel.ME, .12f, .54f, Comportamento.SOBE_ALA),
            Posto(Papel.MC, .38f, .52f), Posto(Papel.MC, .62f, .52f),
            Posto(Papel.MD, .88f, .54f, Comportamento.SOBE_ALA),
            Posto(Papel.ATA, .41f, .81f), Posto(Papel.ATA, .59f, .81f),
        ))

    // ---------------------------------------------------- LINHA DE 5

    val f532 = Formacao("5-3-2",
        "Bloco baixo com cinco atrás, saindo no contra-ataque.",
        Familia.CINCO, listOf(
            gol(.04f),
            Posto(Papel.LE, .10f, .20f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .30f, .16f), Posto(Papel.ZAG, .50f, .14f),
            Posto(Papel.ZAG, .70f, .16f),
            Posto(Papel.LD, .90f, .20f, Comportamento.SOBE_ALA),
            Posto(Papel.MC, .34f, .42f), Posto(Papel.VOL, .50f, .38f,
                Comportamento.PIVO),
            Posto(Papel.MC, .66f, .42f),
            Posto(Papel.ATA, .41f, .72f), Posto(Papel.ATA, .59f, .72f),
        ))

    val f541 = Formacao("5-4-1",
        "Retranca de verdade: nove atrás da linha da bola.",
        Familia.CINCO, listOf(
            gol(.04f),
            Posto(Papel.LE, .10f, .17f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ZAG, .30f, .13f), Posto(Papel.ZAG, .50f, .12f),
            Posto(Papel.ZAG, .70f, .13f),
            Posto(Papel.LD, .90f, .17f, Comportamento.LATERAL_CONTIDO),
            Posto(Papel.ME, .18f, .35f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.VOL, .40f, .32f, Comportamento.SEGURA),
            Posto(Papel.VOL, .60f, .32f, Comportamento.SEGURA),
            Posto(Papel.MD, .82f, .35f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.ATA, .50f, .62f, Comportamento.ATACA_PROFUNDIDADE),
        ))

    val f523 = Formacao("5-2-3",
        "Cinco atrás mas com três na frente para a pressão alta.",
        Familia.CINCO, listOf(
            gol(),
            Posto(Papel.LE, .10f, .22f, Comportamento.SOBE_ALA),
            Posto(Papel.ZAG, .30f, .18f), Posto(Papel.ZAG, .50f, .16f),
            Posto(Papel.ZAG, .70f, .18f),
            Posto(Papel.LD, .90f, .22f, Comportamento.SOBE_ALA),
            Posto(Papel.VOL, .38f, .44f, Comportamento.PIVO),
            Posto(Papel.VOL, .62f, .44f, Comportamento.PIVO),
            Posto(Papel.PE, .20f, .72f, Comportamento.COLA_NA_LINHA),
            Posto(Papel.ATA, .50f, .78f),
            Posto(Papel.PD, .80f, .72f, Comportamento.COLA_NA_LINHA),
        ))

    val todas = listOf(
        f442, f4231, f433Segurando, f433Ofensiva, f4141, f442Diamante,
        f4411, f41212, f4222, f451,
        f352, f343, f3142,
        f532, f541, f523,
    )

    fun porFamilia(f: Familia) = todas.filter { it.familia == f }

    val padrao = f4231
}
