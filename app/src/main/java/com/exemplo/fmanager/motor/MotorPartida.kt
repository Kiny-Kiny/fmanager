package com.exemplo.fmanager.motor

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.bonus
import com.exemplo.fmanager.dados.rendimentoEm
import com.exemplo.fmanager.dados.tracos
import com.exemplo.fmanager.formacao.*
import com.exemplo.fmanager.sistemas.CalculadoraEntrosamento
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/*
 * MOTOR DE PARTIDA — simulação por eventos discretos.
 *
 * A mudança grande em relação à versão anterior: o time tem UMA FORMA
 * POR FASE. A força defensiva é calculada com as posições de "sem a
 * bola"; a força ofensiva, com as de "com a bola". Então a 4-2-3-1 que
 * vira 3-2-5 realmente defende como 4-2-3-1 e ataca como 3-2-5.
 *
 * Sobre um jogador, quatro camadas se multiplicam:
 *   atributos × familiaridade × entrosamento × estilo
 */

// ------------------------------------------------------------ ENTRADA

data class JogadorEmCampo(
    val jogador: Jogador,
    val slot: Slot,
    /** 0..100, vindo da calculadora de entrosamento. */
    val entrosamento: Int = 50,
) {
    val estilo = slot.estilo
    val mod = estilo.modificador()

    /** Bônus dos PlayStyles da EA. Calculado uma vez por partida,
     *  não por evento — parsear texto 180 vezes seria desperdício. */
    val tracos = jogador.tracos().bonus()

    /** Rendimento nesta fase: o papel muda conforme a fase, então o
     *  aproveitamento do jogador muda junto. */
    fun eficiencia(fase: Fase): Float {
        val papel = slot.em(fase).papel
        val base = jogador.rendimentoEm(papel) / 100f
        return base * CalculadoraEntrosamento.multiplicador(entrosamento)
    }
}

data class TimeEmCampo(
    val clubeId: Int,
    val nome: String,
    val escalacao: List<JogadorEmCampo>,
    val tatica: Tatica,
) {
    init { require(escalacao.size == 11) { "Escale exatamente 11 jogadores" } }
}

// ------------------------------------------------------------- SAÍDA

sealed interface Evento {
    val minuto: Int

    data class Gol(override val minuto: Int, val time: String, val autor: String,
                   val assistencia: String?) : Evento
    data class Chute(override val minuto: Int, val time: String, val autor: String,
                     val noAlvo: Boolean) : Evento
    data class Cartao(override val minuto: Int, val time: String, val autor: String,
                      val vermelho: Boolean) : Evento
    data class Lesao(override val minuto: Int, val time: String, val autor: String,
                     val semanas: Int) : Evento
}

data class Resultado(
    val golsMandante: Int,
    val golsVisitante: Int,
    val posseMandante: Int,
    val chutesMandante: Int,
    val chutesVisitante: Int,
    val eventos: List<Evento>,
    val notas: Map<Int, Float>,
    /** Quanto de gás sobrou em cada jogador (0..100). Estilos exigentes
     *  e formações de muito movimento derrubam isso. */
    val gasFinal: Map<Int, Int>,
)

// ------------------------------------------------------------- MOTOR

class MotorPartida(private val rng: Random = Random.Default) {

    companion object {
        internal const val MOMENTOS = 180
    }

    fun simular(mandante: TimeEmCampo, visitante: TimeEmCampo): Resultado {
        val casa = Forcas(mandante, mandoDeCampo = true)
        val fora = Forcas(visitante, mandoDeCampo = false)

        var golsCasa = 0; var golsFora = 0
        var chutesCasa = 0; var chutesFora = 0
        var posseCasa = 0
        val eventos = mutableListOf<Evento>()
        val contribuicao = mutableMapOf<Int, Float>()

        // Gás de cada jogador ao longo da partida.
        val gas = (mandante.escalacao + visitante.escalacao)
            .associate { it.jogador.id to 100f }.toMutableMap()

        val dominioMeio = casa.meio / (casa.meio + fora.meio)

        repeat(MOMENTOS) { m ->
            val minuto = (m * 90) / MOMENTOS

            // Desgaste: estilo exigente e muito deslocamento entre fases
            // cansam mais. Resistência alta segura a queda.
            listOf(mandante, visitante).forEach { time ->
                time.escalacao.forEach { jc ->
                    val custo = jc.mod.desgaste *
                            (1f + jc.slot.amplitudeDeMovimento * 1.2f) *
                            (1f + time.tatica.intensidadePressao / 200f) *
                            (1.9f - jc.jogador.resistencia / 100f) * 0.055f /
                            jc.tracos.resistencia
                    gas[jc.jogador.id] = (gas[jc.jogador.id]!! - custo).coerceAtLeast(35f)
                }
            }

            val casaComBola = rng.nextFloat() < dominioMeio
            if (casaComBola) posseCasa++

            val atq = if (casaComBola) casa else fora
            val def = if (casaComBola) fora else casa
            val timeAtq = if (casaComBola) mandante else visitante

            if (rng.nextFloat() >= probabilidadeDeChance(atq, def)) return@repeat

            val finalizador = escolherFinalizador(timeAtq, gas)
            val criador = escolherCriador(timeAtq, finalizador, gas)

            if (casaComBola) chutesCasa++ else chutesFora++
            contribuicao.merge(finalizador.jogador.id, 0.3f, Float::plus)

            val fatorGas = (gas[finalizador.jogador.id]!! / 100f)
            val qualidade = qualidadeDaFinalizacao(finalizador, atq) * fatorGas
            val defesa = qualidadeDoGoleiro(def)
            val probGol = (qualidade / (qualidade + defesa)).coerceIn(0.02f, 0.55f)

            if (rng.nextFloat() < probGol) {
                if (casaComBola) golsCasa++ else golsFora++
                eventos += Evento.Gol(minuto, timeAtq.nome,
                    finalizador.jogador.nome, criador?.jogador?.nome)
                contribuicao.merge(finalizador.jogador.id, 1.6f, Float::plus)
                criador?.let { contribuicao.merge(it.jogador.id, 1.0f, Float::plus) }
            } else {
                eventos += Evento.Chute(minuto, timeAtq.nome,
                    finalizador.jogador.nome, noAlvo = rng.nextFloat() < 0.45f)
            }
        }

        eventos += faltasECartoes(mandante, visitante)
        eventos += lesoes(mandante, visitante, gas)

        return Resultado(
            golsMandante = golsCasa,
            golsVisitante = golsFora,
            posseMandante = (posseCasa * 100) / MOMENTOS,
            chutesMandante = chutesCasa,
            chutesVisitante = chutesFora,
            eventos = eventos.sortedBy { it.minuto },
            notas = calcularNotas(mandante, visitante, contribuicao, golsCasa, golsFora),
            gasFinal = gas.mapValues { it.value.toInt() },
        )
    }

    /**
     * Agrega o time em três setores. O detalhe que importa: cada setor
     * é calculado com as posições da FASE correspondente.
     */
    private fun probabilidadeDeChance(atq: Forcas, def: Forcas): Float {
        val base = atq.ataque / (atq.ataque + def.defesa * 1.35f)
        val bonusContra = if (def.tatica.alturaLinha > 60)
            atq.tatica.contraAtaque / 500f else 0f
        return (base * 0.30f + bonusContra).coerceIn(0.01f, 0.35f)
    }

    private fun qualidadeDaFinalizacao(jc: JogadorEmCampo, f: Forcas): Float {
        val j = jc.jogador
        return ((j.finalizacao * 0.4f + j.forcaChute * 0.2f +
                j.sangueFrio * 0.2f + j.posicionamento * 0.2f) *
                jc.eficiencia(Fase.COM_POSSE)) *
                (1f + f.tatica.liberdadeCriativa / 400f) *
                jc.tracos.finalizacao
    }

    private fun qualidadeDoGoleiro(f: Forcas): Float {
        val g = f.goleiro ?: return 55f
        val j = g.jogador
        return (j.golReflexo * 0.4f + j.golMergulho * 0.3f +
                j.golPosicionamento * 0.3f) * 1.15f * g.mod.contribuicaoDefensiva
    }

    // -------------------------------------------------- SORTEIOS

    private fun escolherFinalizador(
        time: TimeEmCampo, gas: Map<Int, Float>,
    ): JogadorEmCampo {
        val candidatos = time.escalacao
            .filter { it.slot.em(Fase.COM_POSSE).papel != Papel.GOL }
        val pesos = candidatos.map { jc ->
            val pos = jc.slot.em(Fase.COM_POSSE)
            // Estreitamento do estilo aproxima o jogador do gol.
            val centralidade = 1f - abs(pos.x - 0.5f) * (1f - jc.mod.estreitamento)
            val avanco = (pos.y + jc.mod.avancoComPosse).coerceIn(0f, 1f).pow(2f)
            val instrucao = when (jc.slot.instrucoes.movimentacao) {
                Movimentacao.ATACA_ESPACO -> 1.4f
                Movimentacao.CORTA_PRA_DENTRO -> 1.25f
                Movimentacao.FICA_NA_POSICAO -> 0.7f
                Movimentacao.EQUILIBRADO -> 1f
            }
            (jc.jogador.finalizacao / 100f) * avanco * centralidade *
                    instrucao * jc.mod.pesoFinalizacao *
                    (gas[jc.jogador.id]!! / 100f) + 0.01f
        }
        return sortearPorPeso(candidatos, pesos)
    }

    private fun escolherCriador(
        time: TimeEmCampo, exceto: JogadorEmCampo, gas: Map<Int, Float>,
    ): JogadorEmCampo? {
        if (rng.nextFloat() > 0.65f) return null
        val candidatos = time.escalacao.filter {
            it.slot.em(Fase.COM_POSSE).papel != Papel.GOL && it !== exceto
        }
        if (candidatos.isEmpty()) return null
        val pesos = candidatos.map { jc ->
            val j = jc.jogador
            (j.visao * 0.5f + j.passeBaixo * 0.3f + j.cruzamento * 0.2f) / 100f *
                    jc.mod.pesoCriacao * jc.tracos.criacao *
                    (gas[j.id]!! / 100f) + 0.01f
        }
        return sortearPorPeso(candidatos, pesos)
    }

    private fun <T> sortearPorPeso(itens: List<T>, pesos: List<Float>): T {
        val total = pesos.sum()
        var alvo = rng.nextFloat() * total
        itens.forEachIndexed { i, item ->
            alvo -= pesos[i]
            if (alvo <= 0f) return item
        }
        return itens.last()
    }

    // ------------------------------------------- CARTÕES E LESÕES

    private fun faltasECartoes(casa: TimeEmCampo, fora: TimeEmCampo): List<Evento> {
        val eventos = mutableListOf<Evento>()
        listOf(casa, fora).forEach { time ->
            time.escalacao.forEach { jc ->
                val risco = (jc.jogador.agressividade / 100f) *
                        (jc.slot.instrucoes.pressao / 100f) * 0.14f
                if (rng.nextFloat() < risco) {
                    eventos += Evento.Cartao(
                        rng.nextInt(1, 91), time.nome, jc.jogador.nome,
                        vermelho = rng.nextFloat() < 0.06f,
                    )
                }
            }
        }
        return eventos
    }

    private fun lesoes(
        casa: TimeEmCampo, fora: TimeEmCampo, gas: Map<Int, Float>,
    ): List<Evento> {
        val eventos = mutableListOf<Evento>()
        listOf(casa, fora).forEach { time ->
            time.escalacao.forEach { jc ->
                // Quem chegou ao fim sem gás se machuca muito mais.
                val exaustao = 1f - (gas[jc.jogador.id]!! / 100f)
                val risco = exaustao * (1.5f - jc.jogador.resistencia / 100f) * 0.05f
                if (rng.nextFloat() < risco) {
                    eventos += Evento.Lesao(
                        rng.nextInt(45, 91), time.nome, jc.jogador.nome,
                        semanas = rng.nextInt(1, 9),
                    )
                }
            }
        }
        return eventos
    }

    // ------------------------------------------------------ NOTAS

    private fun calcularNotas(
        casa: TimeEmCampo, fora: TimeEmCampo,
        contribuicao: Map<Int, Float>, golsCasa: Int, golsFora: Int,
    ): Map<Int, Float> {
        val notas = mutableMapOf<Int, Float>()
        listOf(casa to (golsCasa - golsFora), fora to (golsFora - golsCasa))
            .forEach { (time, saldo) ->
                time.escalacao.forEach { jc ->
                    val ef = (jc.eficiencia(Fase.SEM_POSSE) +
                            jc.eficiencia(Fase.COM_POSSE)) / 2f
                    notas[jc.jogador.id] = (
                            6.0f +
                            saldo.coerceIn(-3, 3) * 0.18f +
                            (contribuicao[jc.jogador.id] ?: 0f) +
                            (ef - 0.75f) * 2f
                            ).coerceIn(3f, 10f)
                }
            }
        return notas
    }
}
