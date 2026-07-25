package com.exemplo.fmanager.motor

import com.exemplo.fmanager.formacao.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/*
 * PARTIDA AO VIVO.
 *
 * O motor instantâneo resolve os 180 momentos de uma vez. Este resolve
 * UM POR VEZ, e devolve, a cada passo, onde está cada uma das 22 peças
 * e a bola. É isso que permite assistir.
 *
 * O truque das posições: elas NÃO são inventadas. Vêm direto do seu
 * editor de fases. Quando seu time tem a bola, as peças caminham para
 * as coordenadas de COM_POSSE; quando defende, para SEM_POSSE. Você vê
 * a sua 4-2-3-1 virar 3-2-5 em tempo real, porque é literalmente a
 * mesma estrutura de dados.
 *
 * As táticas podem ser trocadas no meio do jogo: chamar
 * atualizarTatica() recalcula as forças e o resto da partida responde.
 */

/** Uma peça no campo, já com a posição desta fase. */
data class Peca(
    val jogadorId: Int,
    val nome: String,
    val sigla: String,
    val x: Float,          // 0..1 sempre na perspectiva do mandante
    val y: Float,
    val doMandante: Boolean,
    val comABola: Boolean = false,
    val gas: Int = 100,
)

/** Retrato do jogo num instante. A tela desenha isto. */
data class Instante(
    val minuto: Int,
    val golsMandante: Int,
    val golsVisitante: Int,
    val posseMandante: Int,
    val chutesMandante: Int,
    val chutesVisitante: Int,
    val pecas: List<Peca>,
    val bolaX: Float,
    val bolaY: Float,
    val eventoNovo: Evento?,
    val faseMandante: Fase,
    val acabou: Boolean,
)

class PartidaAoVivo(
    mandante: TimeEmCampo,
    visitante: TimeEmCampo,
    private val rng: Random = Random.Default,
) {
    private var timeCasa = mandante
    private var timeFora = visitante
    private var forcaCasa = Forcas(timeCasa, mandoDeCampo = true)
    private var forcaFora = Forcas(timeFora, mandoDeCampo = false)

    private var momento = 0
    private var golsCasa = 0
    private var golsFora = 0
    private var chutesCasa = 0
    private var chutesFora = 0
    private var momentosComPosseCasa = 0

    private val eventos = mutableListOf<Evento>()
    private val gas = (timeCasa.escalacao + timeFora.escalacao)
        .associate { it.jogador.id to 100f }.toMutableMap()
    private val contribuicao = mutableMapOf<Int, Float>()

    /** Quem tem a bola agora. Define a fase das duas equipes. */
    private var casaComBola = true
    private var bolaX = 0.5f
    private var bolaY = 0.5f

    val acabou: Boolean get() = momento >= MotorPartida.MOMENTOS
    val eventosAteAgora: List<Evento> get() = eventos.toList()

    /** Troca a tática no meio da partida e recalcula as forças. */
    fun atualizarTatica(doMandante: Boolean, nova: Tatica) {
        if (doMandante) {
            timeCasa = timeCasa.copy(tatica = nova)
            forcaCasa = Forcas(timeCasa, mandoDeCampo = true)
        } else {
            timeFora = timeFora.copy(tatica = nova)
            forcaFora = Forcas(timeFora, mandoDeCampo = false)
        }
    }

    /** Avança um momento (meio minuto de jogo) e devolve o retrato. */
    fun passo(): Instante {
        if (acabou) return retrato(null)

        val minuto = (momento * 90) / MotorPartida.MOMENTOS
        var eventoNovo: Evento? = null

        desgastar()

        // A posse oscila conforme o domínio do meio-campo.
        val dominio = forcaCasa.meio / (forcaCasa.meio + forcaFora.meio)
        casaComBola = rng.nextFloat() < dominio
        if (casaComBola) momentosComPosseCasa++

        val atq = if (casaComBola) forcaCasa else forcaFora
        val def = if (casaComBola) forcaFora else forcaCasa
        val timeAtq = if (casaComBola) timeCasa else timeFora

        // A bola caminha para o campo de quem ataca.
        val alvoY = if (casaComBola) 0.62f else 0.38f
        bolaY += (alvoY - bolaY) * 0.35f + (rng.nextFloat() - 0.5f) * 0.08f
        bolaX += (rng.nextFloat() - 0.5f) * 0.22f
        bolaX = bolaX.coerceIn(0.08f, 0.92f)
        bolaY = bolaY.coerceIn(0.06f, 0.94f)

        val chance = (atq.ataque / (atq.ataque + def.defesa * 1.35f)) * 0.30f +
                (if (def.tatica.alturaLinha > 60) atq.tatica.contraAtaque / 500f else 0f)

        if (rng.nextFloat() < chance.coerceIn(0.01f, 0.35f)) {
            val finalizador = sortearFinalizador(timeAtq)
            val criador = sortearCriador(timeAtq, finalizador)

            if (casaComBola) chutesCasa++ else chutesFora++
            contribuicao.merge(finalizador.jogador.id, 0.3f, Float::plus)

            // A bola vai para os pés de quem finaliza.
            val pos = finalizador.slot.em(Fase.COM_POSSE)
            bolaX = if (casaComBola) pos.x else 1f - pos.x
            bolaY = if (casaComBola) 0.06f + pos.y * 0.88f else 0.94f - pos.y * 0.88f

            val fatorGas = gas.getValue(finalizador.jogador.id) / 100f
            val qualidade = qualidadeChute(finalizador, atq) * fatorGas
            val defesaGol = qualidadeGoleiro(def)
            val probGol = (qualidade / (qualidade + defesaGol)).coerceIn(0.02f, 0.55f)

            if (rng.nextFloat() < probGol) {
                if (casaComBola) golsCasa++ else golsFora++
                eventoNovo = Evento.Gol(minuto, timeAtq.nome,
                    finalizador.jogador.nome, criador?.jogador?.nome)
                contribuicao.merge(finalizador.jogador.id, 1.6f, Float::plus)
                criador?.let { contribuicao.merge(it.jogador.id, 1.0f, Float::plus) }
                // Recomeça do meio.
                bolaX = 0.5f; bolaY = 0.5f
            } else {
                eventoNovo = Evento.Chute(minuto, timeAtq.nome,
                    finalizador.jogador.nome, noAlvo = rng.nextFloat() < 0.45f)
            }
        }

        // Cartões e lesões pontuais, distribuídos pelo jogo.
        if (eventoNovo == null) eventoNovo = sortearIncidente(minuto)

        eventoNovo?.let { eventos += it }
        momento++
        return retrato(eventoNovo)
    }

    /** Roda o resto da partida de uma vez. Usado pelo botão "Pular". */
    fun pularParaOFim(): Instante {
        var ultimo = retrato(null)
        while (!acabou) ultimo = passo()
        return ultimo
    }

    fun resultado(): Resultado = Resultado(
        golsMandante = golsCasa,
        golsVisitante = golsFora,
        posseMandante = if (momento == 0) 50
        else (momentosComPosseCasa * 100) / momento,
        chutesMandante = chutesCasa,
        chutesVisitante = chutesFora,
        eventos = eventos.sortedBy { it.minuto },
        notas = calcularNotas(),
        gasFinal = gas.mapValues { it.value.toInt() },
    )

    // ------------------------------------------------------- RETRATO

    private fun retrato(evento: Evento?): Instante {
        // A fase de cada time depende de quem tem a bola.
        val faseCasa = if (casaComBola) Fase.COM_POSSE else Fase.SEM_POSSE
        val faseFora = if (casaComBola) Fase.SEM_POSSE else Fase.COM_POSSE

        val pecas = buildList {
            timeCasa.escalacao.forEach { jc ->
                val p = jc.slot.em(faseCasa)
                add(Peca(
                    jogadorId = jc.jogador.id,
                    nome = jc.jogador.nome,
                    sigla = p.papel.sigla,
                    // Mandante ataca para cima: y do editor já é isso.
                    x = p.x,
                    y = 0.06f + p.y * 0.88f,
                    doMandante = true,
                    gas = gas.getValue(jc.jogador.id).toInt(),
                ))
            }
            timeFora.escalacao.forEach { jc ->
                val p = jc.slot.em(faseFora)
                add(Peca(
                    jogadorId = jc.jogador.id,
                    nome = jc.jogador.nome,
                    sigla = p.papel.sigla,
                    // Visitante ataca para baixo: espelha os dois eixos.
                    x = 1f - p.x,
                    y = 0.94f - p.y * 0.88f,
                    doMandante = false,
                    gas = gas.getValue(jc.jogador.id).toInt(),
                ))
            }
        }

        return Instante(
            minuto = ((momento * 90) / MotorPartida.MOMENTOS).coerceAtMost(90),
            golsMandante = golsCasa,
            golsVisitante = golsFora,
            posseMandante = if (momento == 0) 50
            else (momentosComPosseCasa * 100) / momento,
            chutesMandante = chutesCasa,
            chutesVisitante = chutesFora,
            pecas = pecas,
            bolaX = bolaX,
            bolaY = bolaY,
            eventoNovo = evento,
            faseMandante = faseCasa,
            acabou = acabou,
        )
    }

    // ------------------------------------------------------ INTERNOS

    private fun desgastar() {
        listOf(timeCasa, timeFora).forEach { time ->
            time.escalacao.forEach { jc ->
                val custo = jc.mod.desgaste *
                        (1f + jc.slot.amplitudeDeMovimento * 1.2f) *
                        (1f + time.tatica.intensidadePressao / 200f) *
                        (1.9f - jc.jogador.resistencia / 100f) * 0.055f /
                        jc.tracos.resistencia
                gas[jc.jogador.id] =
                    (gas.getValue(jc.jogador.id) - custo).coerceAtLeast(35f)
            }
        }
    }

    private fun sortearFinalizador(time: TimeEmCampo): JogadorEmCampo {
        val candidatos = time.escalacao
            .filter { it.slot.em(Fase.COM_POSSE).papel != Papel.GOL }
        val pesos = candidatos.map { jc ->
            val pos = jc.slot.em(Fase.COM_POSSE)
            val centralidade = 1f - abs(pos.x - 0.5f) * (1f - jc.mod.estreitamento)
            val avanco = (pos.y + jc.mod.avancoComPosse).coerceIn(0f, 1f).pow(2f)
            val instrucao = when (jc.slot.instrucoes.movimentacao) {
                Movimentacao.ATACA_ESPACO -> 1.4f
                Movimentacao.CORTA_PRA_DENTRO -> 1.25f
                Movimentacao.FICA_NA_POSICAO -> 0.7f
                Movimentacao.EQUILIBRADO -> 1f
            }
            (jc.jogador.finalizacao / 100f) * avanco * centralidade * instrucao *
                    jc.mod.pesoFinalizacao * (gas.getValue(jc.jogador.id) / 100f) + 0.01f
        }
        return sortear(candidatos, pesos)
    }

    private fun sortearCriador(
        time: TimeEmCampo, exceto: JogadorEmCampo,
    ): JogadorEmCampo? {
        if (rng.nextFloat() > 0.65f) return null
        val candidatos = time.escalacao.filter {
            it.slot.em(Fase.COM_POSSE).papel != Papel.GOL && it !== exceto
        }
        if (candidatos.isEmpty()) return null
        val pesos = candidatos.map { jc ->
            val j = jc.jogador
            (j.visao * 0.5f + j.passeBaixo * 0.3f + j.cruzamento * 0.2f) / 100f *
                    jc.mod.pesoCriacao * (gas.getValue(j.id) / 100f) + 0.01f
        }
        return sortear(candidatos, pesos)
    }

    private fun <T> sortear(itens: List<T>, pesos: List<Float>): T {
        var alvo = rng.nextFloat() * pesos.sum()
        itens.forEachIndexed { i, item ->
            alvo -= pesos[i]
            if (alvo <= 0f) return item
        }
        return itens.last()
    }

    private fun qualidadeChute(jc: JogadorEmCampo, f: Forcas): Float {
        val j = jc.jogador
        return ((j.finalizacao * 0.4f + j.forcaChute * 0.2f +
                j.sangueFrio * 0.2f + j.posicionamento * 0.2f) *
                jc.eficiencia(Fase.COM_POSSE)) *
                (1f + f.tatica.liberdadeCriativa / 400f) * jc.tracos.finalizacao
    }

    private fun qualidadeGoleiro(f: Forcas): Float {
        val g = f.goleiro ?: return 55f
        val j = g.jogador
        return (j.golReflexo * 0.4f + j.golMergulho * 0.3f +
                j.golPosicionamento * 0.3f) * 1.15f * g.mod.contribuicaoDefensiva
    }

    private fun sortearIncidente(minuto: Int): Evento? {
        val time = if (rng.nextBoolean()) timeCasa else timeFora
        val jc = time.escalacao.random(rng)

        val riscoCartao = (jc.jogador.agressividade / 100f) *
                (jc.slot.instrucoes.pressao / 100f) * 0.008f
        if (rng.nextFloat() < riscoCartao) {
            return Evento.Cartao(minuto, time.nome, jc.jogador.nome,
                vermelho = rng.nextFloat() < 0.06f)
        }

        val exaustao = 1f - (gas.getValue(jc.jogador.id) / 100f)
        val riscoLesao = exaustao * (1.5f - jc.jogador.resistencia / 100f) * 0.004f
        if (rng.nextFloat() < riscoLesao) {
            return Evento.Lesao(minuto, time.nome, jc.jogador.nome,
                semanas = rng.nextInt(1, 9))
        }
        return null
    }

    private fun calcularNotas(): Map<Int, Float> {
        val notas = mutableMapOf<Int, Float>()
        listOf(timeCasa to (golsCasa - golsFora), timeFora to (golsFora - golsCasa))
            .forEach { (time, saldo) ->
                time.escalacao.forEach { jc ->
                    val ef = (jc.eficiencia(Fase.SEM_POSSE) +
                            jc.eficiencia(Fase.COM_POSSE)) / 2f
                    notas[jc.jogador.id] = (
                            6.0f + saldo.coerceIn(-3, 3) * 0.18f +
                            (contribuicao[jc.jogador.id] ?: 0f) +
                            (ef - 0.75f) * 2f
                            ).coerceIn(3f, 10f)
                }
            }
        return notas
    }
}
