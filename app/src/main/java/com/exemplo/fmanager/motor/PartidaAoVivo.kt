package com.exemplo.fmanager.motor

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.bonus
import com.exemplo.fmanager.dados.rendimentoEm
import com.exemplo.fmanager.dados.tracos
import com.exemplo.fmanager.formacao.*
import com.exemplo.fmanager.sistemas.CalculadoraEntrosamento
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.random.Random

/*
 * MOTOR DE PARTIDA — CADEIA DE POSSE.
 *
 * A diferença em relação à versão anterior: a bola SEMPRE pertence a um
 * jogador. Nada acontece por conta própria; todo lance é a decisão de
 * quem está com ela — passar, conduzir, driblar ou chutar. O adversário
 * mais próximo tenta interceptar ou desarmar, e aí pode sair falta.
 *
 * Isso resolve três coisas de uma vez:
 *   - os passes deixam de ser estranhos, porque existem de verdade
 *   - a narração ganha autor em cada linha
 *   - faltas, pênaltis e impedimentos passam a ter onde acontecer
 *
 * O relógio anda por lance, não em passos fixos: um toque leva 3s, uma
 * falta cobrada leva 30s, um gol leva um minuto com a comemoração. Uma
 * partida dá entre 800 e 1200 lances, o que bate com o futebol real.
 */

// ------------------------------------------------------------ ENTRADA

data class JogadorEmCampo(
    val jogador: Jogador,
    val slot: Slot,
    val entrosamento: Int = 50,
) {
    val estilo = slot.estilo
    val mod = estilo.modificador()
    val tracos = jogador.tracos().bonus()

    fun eficiencia(fase: Fase): Float {
        val papel = slot.em(fase).papel
        return (jogador.rendimentoEm(papel) / 100f) *
                CalculadoraEntrosamento.multiplicador(entrosamento)
    }
}

data class TimeEmCampo(
    val clubeId: Int,
    val nome: String,
    val escalacao: List<JogadorEmCampo>,
    val tatica: Tatica,
    val reservas: List<Jogador> = emptyList(),
) {
    init { require(escalacao.size == 11) { "Escale exatamente 11 jogadores" } }
}

// ------------------------------------------------------------- SAÍDA

data class Peca(
    val jogadorId: Int,
    val nome: String,
    val sigla: String,
    val x: Float,
    val y: Float,
    val doMandante: Boolean,
    val comABola: Boolean = false,
    val gas: Int = 100,
)

data class Instante(
    val minuto: Int,
    val golsMandante: Int,
    val golsVisitante: Int,
    val pecas: List<Peca>,
    val bolaX: Float,
    val bolaY: Float,
    /** Origem e destino do passe em curso, para a tela desenhar a linha. */
    val passeDeX: Float? = null,
    val passeDeY: Float? = null,
    val lanceNovo: Lance? = null,
    val statsMandante: Estatisticas = Estatisticas(),
    val statsVisitante: Estatisticas = Estatisticas(),
    val mandanteComABola: Boolean = true,
    val acabou: Boolean = false,
)

data class Resultado(
    val golsMandante: Int,
    val golsVisitante: Int,
    val lances: List<Lance>,
    val statsMandante: Estatisticas,
    val statsVisitante: Estatisticas,
    val notas: Map<Int, Float>,
    val gasFinal: Map<Int, Int>,
    val golsPorJogador: Map<Int, Int> = emptyMap(),
    val assistenciasPorJogador: Map<Int, Int> = emptyMap(),
    val amarelosPorJogador: Set<Int> = emptySet(),
    val vermelhosPorJogador: Set<Int> = emptySet(),
    val titularesMandante: List<Int> = emptyList(),
    val titularesVisitante: List<Int> = emptyList(),
) {
    val posseMandante get() = statsMandante.posse
    val chutesMandante get() = statsMandante.chutes
    val chutesVisitante get() = statsVisitante.chutes
}

// ------------------------------------------------------------- MOTOR

class PartidaAoVivo(
    mandante: TimeEmCampo,
    visitante: TimeEmCampo,
    private val rng: Random = Random.Default,
) {
    companion object {
        private const val DURACAO_SEGUNDOS = 90 * 60
        const val MAX_SUBSTITUICOES = 5
    }

    /** Estado mutável de uma equipe durante a partida. */
    private inner class Equipe(inicial: TimeEmCampo, val mandante: Boolean) {
        var time = inicial
        var forcas = Forcas(inicial, mandante)
        val emCampo = inicial.escalacao.toMutableList()
        val banco = inicial.reservas.toMutableList()
        var substituicoes = 0
        val amarelados = mutableSetOf<Int>()
        val expulsos = mutableSetOf<Int>()
        var stats = Estatisticas()
        var momentosComBola = 0

        fun recalcular() {
            time = time.copy(escalacao = emCampo.toList())
            forcas = Forcas(time, mandante)
        }

        val ativos: List<JogadorEmCampo>
            get() = emCampo.filter { it.jogador.id !in expulsos }
    }

    private val casa = Equipe(mandante, true)
    private val fora = Equipe(visitante, false)

    private var segundos = 0
    private var golsCasa = 0
    private var golsFora = 0
    private var lances = 0

    private val historico = mutableListOf<Lance>()
    private val gas = (casa.emCampo + fora.emCampo)
        .associate { it.jogador.id to 100f }.toMutableMap()
    private val contribuicao = mutableMapOf<Int, Float>()
    private val golsPor = mutableMapOf<Int, Int>()
    private val assistPor = mutableMapOf<Int, Int>()
    private val participaram = mutableSetOf<Int>()

    /** Quem está com a bola e quem tocou por último (para assistência). */
    private var atacante: Equipe = if (rng.nextBoolean()) casa else fora
    private var portador: JogadorEmCampo = atacante.ativos.random(rng)
    private var ultimoPassador: JogadorEmCampo? = null

    /** Deslocamento do portador em relação ao slot: ele conduz a bola. */
    private var avancoConducao = 0f

    private var passeDe: Pair<Float, Float>? = null

    val acabou: Boolean get() = segundos >= DURACAO_SEGUNDOS
    val minuto: Int get() = (segundos / 60).coerceAtMost(90)
    val lancesAteAgora: List<Lance> get() = historico.toList()
    val podeSubstituir: Boolean
        get() = casa.substituicoes < MAX_SUBSTITUICOES
    val elencoEmCampo: List<Jogador> get() = casa.emCampo.map { it.jogador }
    val bancoDisponivel: List<Jogador> get() = casa.banco.toList()

    // ---------------------------------------------------- CONTROLES

    fun atualizarTatica(doMandante: Boolean, nova: Tatica) {
        val e = if (doMandante) casa else fora
        e.time = e.time.copy(tatica = nova)
        e.forcas = Forcas(e.time, e.mandante)
    }

    /** Substitui um jogador do time do usuário (sempre o mandante lógico). */
    fun substituir(sai: Int, entra: Jogador): Lance? {
        if (casa.substituicoes >= MAX_SUBSTITUICOES) return null
        val indice = casa.emCampo.indexOfFirst { it.jogador.id == sai }
        if (indice < 0) return null
        if (casa.banco.none { it.id == entra.id }) return null

        val saindo = casa.emCampo[indice]
        // O reserva herda o slot, as instruções e o estilo de quem saiu.
        casa.emCampo[indice] = saindo.copy(jogador = entra)
        casa.banco.removeAll { it.id == entra.id }
        casa.banco.add(saindo.jogador)
        casa.substituicoes++
        gas[entra.id] = 100f
        participaram += entra.id
        casa.recalcular()

        if (portador.jogador.id == sai) portador = casa.emCampo[indice]

        val l = Lance.Substituicao(minuto, casa.time.nome,
            saindo.jogador.nome, entra.nome)
        historico += l
        return l
    }

    // --------------------------------------------------------- LANCE

    fun passo(): Instante {
        if (acabou) return retrato(null)

        passeDe = null
        desgastar()

        val defensor = if (atacante === casa) fora else casa
        atacante.momentosComBola++

        val lance = resolverLance(defensor)
        lance?.let { historico += it }
        lances++

        return retrato(lance)
    }

    fun pularParaOFim(): Instante {
        var ultimo = retrato(null)
        while (!acabou) ultimo = passo()
        return ultimo
    }

    private fun resolverLance(defensor: Equipe): Lance? {
        val avanco = (posicaoDeAtaque(portador) + avancoConducao).coerceIn(0f, 1f)
        val marcador = marcadorMaisProximo(defensor, portador)
        val pressao = pressaoSobre(portador, defensor)

        // Decide o que o portador faz. Os pesos saem dos atributos dele,
        // de onde está no campo e das instruções que você deu.
        val j = portador.jogador
        val pesoChute = if (avanco > 0.60f) {
            (j.finalizacao / 100f) * (avanco - 0.55f) * 4.2f *
                    portador.mod.pesoFinalizacao *
                    (1f + atacante.forcas.tatica.liberdadeCriativa / 300f)
        } else 0.01f

        val pesoDrible = (j.drible / 100f) * (j.agilidade / 100f) *
                (1f + atacante.forcas.tatica.liberdadeCriativa / 150f) *
                portador.tracos.drible * 0.55f

        val pesoPasse = (j.passeBaixo / 100f) * 1.9f *
                (1f + (100 - atacante.forcas.tatica.velocidadeConstrucao) / 260f)

        val pesoConducao = if (pressao < 0.5f) 0.45f else 0.12f

        return when (sortearAcao(pesoChute, pesoDrible, pesoPasse, pesoConducao)) {
            0 -> finalizar(defensor, avanco)
            1 -> driblar(defensor, marcador, pressao)
            2 -> passar(defensor, avanco)
            else -> conduzir(defensor, pressao)
        }
    }

    private fun sortearAcao(vararg pesos: Float): Int {
        var alvo = rng.nextFloat() * pesos.sum()
        pesos.forEachIndexed { i, p ->
            alvo -= p
            if (alvo <= 0f) return i
        }
        return pesos.lastIndex
    }

    // ------------------------------------------------------- PASSE

    private fun passar(defensor: Equipe, avanco: Float): Lance {
        val alvo = escolherReceptor(avanco) ?: return perderBola(defensor, null)
        val longo = abs(posicaoDeAtaque(alvo) - posicaoDeAtaque(portador)) > 0.28f

        passeDe = posicaoAbsoluta(portador, atacante)
        atacante.stats = atacante.stats.copy(passes = atacante.stats.passes + 1)
        avancarRelogio(if (longo) 5 else 3)

        // Precisão do passe: atributo do passador contra a pressão e o
        // risco que a tática pede. Passe longo é mais difícil.
        val habilidade = (if (longo) portador.jogador.passeAlto
        else portador.jogador.passeBaixo) / 100f
        val risco = atacante.forcas.tatica.riscoNoPasse / 100f
        val chanceAcerto = (0.55f + habilidade * 0.42f -
                risco * 0.14f - (if (longo) 0.16f else 0f) +
                portador.tracos.criacao * 0.06f).coerceIn(0.35f, 0.97f)

        if (rng.nextFloat() > chanceAcerto) {
            val interceptador = marcadorMaisProximo(defensor, alvo)
            return errarPasse(defensor, interceptador)
        }

        // Impedimento: passe vertical para quem está muito adiantado.
        if (longo && posicaoDeAtaque(alvo) > 0.80f && rng.nextFloat() < 0.11f) {
            atacante.stats = atacante.stats.copy(
                impedimentos = atacante.stats.impedimentos + 1)
            val autor = alvo.jogador.nome
            trocarPosse(defensor)
            avancarRelogio(20)
            return Lance.Impedimento(minuto, atacante.time.nome, autor)
        }

        atacante.stats = atacante.stats.copy(
            passesCertos = atacante.stats.passesCertos + 1)
        contribuicao.merge(portador.jogador.id, 0.02f, Float::plus)

        val de = portador.jogador.nome
        ultimoPassador = portador
        portador = alvo
        avancoConducao = 0f

        return Lance.Passe(minuto, atacante.time.nome, de, alvo.jogador.nome, longo)
    }

    /** Escolhe para quem passar: prioriza quem está livre e adiantado. */
    private fun escolherReceptor(avanco: Float): JogadorEmCampo? {
        val defensor = if (atacante === casa) fora else casa
        val opcoes = atacante.ativos.filter { it !== portador }
        if (opcoes.isEmpty()) return null

        val vertical = atacante.forcas.tatica.velocidadeConstrucao / 100f
        val pesos = opcoes.map { alvo ->
            val avancoAlvo = posicaoDeAtaque(alvo)
            val ganho = (avancoAlvo - avanco).coerceIn(-1f, 1f)
            // Tática vertical procura quem está à frente; posse aceita
            // o passe lateral e para trás.
            val direcao = 1f + ganho * (0.4f + vertical * 1.4f)
            val distancia = distanciaEntre(portador, alvo)
            val proximidade = (1f - distancia).coerceAtLeast(0.12f)
            val livre = 1f - pressaoSobre(alvo, defensor) * 0.7f
            (direcao.coerceAtLeast(0.08f) * proximidade * livre *
                    (alvo.jogador.controleBola / 100f) + 0.02f)
        }
        var t = rng.nextFloat() * pesos.sum()
        opcoes.forEachIndexed { i, o ->
            t -= pesos[i]
            if (t <= 0f) return o
        }
        return opcoes.last()
    }

    private fun errarPasse(defensor: Equipe, interceptador: JogadorEmCampo?): Lance {
        val de = portador.jogador.nome
        interceptador?.let {
            defensor.stats = defensor.stats.copy(
                desarmes = defensor.stats.desarmes + 1)
            contribuicao.merge(it.jogador.id, 0.05f, Float::plus)
        }
        trocarPosse(defensor, interceptador)
        avancarRelogio(4)
        return Lance.PasseErrado(minuto, atacante.time.nome, de,
            interceptador?.jogador?.nome)
    }

    // ------------------------------------------------------ DRIBLE

    private fun driblar(
        defensor: Equipe, marcador: JogadorEmCampo?, pressao: Float,
    ): Lance {
        avancarRelogio(3)
        if (marcador == null) return conduzir(defensor, pressao)

        val ataque = (portador.jogador.drible * 0.5f +
                portador.jogador.agilidade * 0.3f +
                portador.jogador.equilibrio * 0.2f) / 100f * portador.tracos.drible
        val defesa = (marcador.jogador.rouboBola * 0.5f +
                marcador.jogador.consciencaDef * 0.3f +
                marcador.jogador.velocidade * 0.2f) / 100f * marcador.tracos.defesa

        val sucesso = rng.nextFloat() < (ataque / (ataque + defesa))
        val autor = portador.jogador.nome
        val nomeMarcador = marcador.jogador.nome

        if (sucesso) {
            avancoConducao = (avancoConducao + 0.09f).coerceAtMost(0.30f)
            contribuicao.merge(portador.jogador.id, 0.06f, Float::plus)
            return Lance.Drible(minuto, atacante.time.nome, autor, nomeMarcador, true)
        }

        // O desarme falhou: pode virar falta, e na área é pênalti.
        val faltaProvavel = (marcador.jogador.agressividade / 100f) * 0.42f
        if (rng.nextFloat() < faltaProvavel) {
            return cometerFalta(defensor, marcador, portador)
        }

        defensor.stats = defensor.stats.copy(desarmes = defensor.stats.desarmes + 1)
        trocarPosse(defensor, marcador)
        return Lance.Drible(minuto, atacante.time.nome, autor, nomeMarcador, false)
    }

    private fun conduzir(defensor: Equipe, pressao: Float): Lance {
        avancarRelogio(4)
        val autor = portador.jogador.nome

        // Sob pressão alta, conduzir pode custar a bola.
        if (rng.nextFloat() < pressao * 0.35f) {
            val ladrao = marcadorMaisProximo(defensor, portador)
            defensor.stats = defensor.stats.copy(
                desarmes = defensor.stats.desarmes + 1)
            val vitima = autor
            trocarPosse(defensor, ladrao)
            return ladrao?.let {
                Lance.Desarme(minuto, defensor.time.nome, it.jogador.nome, vitima)
            } ?: Lance.PasseErrado(minuto, atacante.time.nome, vitima, null)
        }

        avancoConducao = (avancoConducao + 0.07f).coerceAtMost(0.30f)
        return Lance.Conducao(minuto, atacante.time.nome, autor)
    }

    // ------------------------------------------------------- FALTA

    private fun cometerFalta(
        defensor: Equipe, infrator: JogadorEmCampo, vitima: JogadorEmCampo,
    ): Lance {
        defensor.stats = defensor.stats.copy(faltas = defensor.stats.faltas + 1)
        val avanco = (posicaoDeAtaque(vitima) + avancoConducao).coerceIn(0f, 1f)
        val central = abs(posicaoDoLado(vitima) - 0.5f) < 0.22f

        // Dentro da área e no miolo: pênalti.
        if (avanco > 0.88f && central) {
            val l = Lance.Penalti(minuto, defensor.time.nome,
                infrator.jogador.nome, vitima.jogador.nome)
            historico += l
            cartaoSeNecessario(defensor, infrator, gravidade = 0.5f)
            baterPenalti(defensor)
            return l
        }

        cartaoSeNecessario(defensor, infrator, gravidade = if (avanco > 0.7f) 0.3f else 0.16f)
        avancarRelogio(25)

        val perigosa = avanco > 0.70f
        val l = Lance.Falta(minuto, defensor.time.nome,
            infrator.jogador.nome, vitima.jogador.nome, perigosa)

        // Falta perigosa vira cobrança direta.
        if (perigosa && rng.nextFloat() < 0.55f) {
            historico += l
            return cobrarFalta(defensor)
        }
        return l
    }

    private fun cobrarFalta(defensor: Equipe): Lance {
        val cobrador = atacante.ativos.maxBy { it.jogador.cobrancaFalta }
        atacante.stats = atacante.stats.copy(chutes = atacante.stats.chutes + 1)
        avancarRelogio(8)

        val qualidade = (cobrador.jogador.cobrancaFalta * 0.6f +
                cobrador.jogador.curva * 0.25f +
                cobrador.jogador.forcaChute * 0.15f) / 100f

        val naBarreira = rng.nextFloat() < 0.28f
        if (naBarreira) {
            trocarPosse(defensor)
            return Lance.CobrancaFalta(minuto, atacante.time.nome,
                cobrador.jogador.nome, noGol = false, naBarreira = true)
        }

        val goleiro = qualidadeGoleiro(defensor)
        val probGol = (qualidade * 90f / (qualidade * 90f + goleiro * 1.9f))
            .coerceIn(0.03f, 0.28f)

        if (rng.nextFloat() < probGol) {
            marcarGol(cobrador, null, defensor, dePenalti = false)
            return Lance.Gol(minuto, atacante.time.nome,
                cobrador.jogador.nome, null, dePenalti = false)
        }

        val noGol = rng.nextFloat() < 0.5f
        if (noGol) atacante.stats = atacante.stats.copy(
            chutesNoGol = atacante.stats.chutesNoGol + 1)
        trocarPosse(defensor)
        return Lance.CobrancaFalta(minuto, atacante.time.nome,
            cobrador.jogador.nome, noGol = noGol, naBarreira = false)
    }

    private fun baterPenalti(defensor: Equipe) {
        val batedor = atacante.ativos.maxBy { it.jogador.penaltis }
        atacante.stats = atacante.stats.copy(
            chutes = atacante.stats.chutes + 1,
            chutesNoGol = atacante.stats.chutesNoGol + 1,
        )
        avancarRelogio(35)

        val qualidade = (batedor.jogador.penaltis * 0.7f +
                batedor.jogador.sangueFrio * 0.3f) / 100f
        val probGol = (0.62f + qualidade * 0.28f).coerceIn(0.55f, 0.93f)

        if (rng.nextFloat() < probGol) {
            marcarGol(batedor, null, defensor, dePenalti = true)
            historico += Lance.Gol(minuto, atacante.time.nome,
                batedor.jogador.nome, null, dePenalti = true)
        } else {
            historico += Lance.Chute(minuto, atacante.time.nome,
                batedor.jogador.nome, Lance.Desfecho.DEFENDIDO)
            trocarPosse(defensor)
        }
    }

    private fun cartaoSeNecessario(
        equipe: Equipe, jc: JogadorEmCampo, gravidade: Float,
    ) {
        if (rng.nextFloat() >= gravidade) return
        val id = jc.jogador.id
        val segundoAmarelo = id in equipe.amarelados

        if (segundoAmarelo || rng.nextFloat() < 0.05f) {
            equipe.expulsos += id
            equipe.stats = equipe.stats.copy(vermelhos = equipe.stats.vermelhos + 1)
            equipe.recalcular()
            historico += Lance.Cartao(minuto, equipe.time.nome, jc.jogador.nome, true)
        } else {
            equipe.amarelados += id
            equipe.stats = equipe.stats.copy(amarelos = equipe.stats.amarelos + 1)
            historico += Lance.Cartao(minuto, equipe.time.nome, jc.jogador.nome, false)
        }
    }

    // -------------------------------------------------- FINALIZAÇÃO

    private fun finalizar(defensor: Equipe, avanco: Float): Lance {
        atacante.stats = atacante.stats.copy(chutes = atacante.stats.chutes + 1)
        contribuicao.merge(portador.jogador.id, 0.25f, Float::plus)
        avancarRelogio(5)

        val j = portador.jogador
        val fatorGas = gas.getValue(j.id) / 100f
        val qualidade = ((j.finalizacao * 0.42f + j.forcaChute * 0.18f +
                j.sangueFrio * 0.2f + j.posicionamento * 0.2f) *
                portador.eficiencia(Fase.COM_POSSE) * fatorGas *
                portador.tracos.finalizacao) * (0.55f + avanco * 0.7f)

        // Bloqueio da defesa antes de chegar ao goleiro.
        val marcador = marcadorMaisProximo(defensor, portador)
        if (marcador != null && rng.nextFloat() < 0.20f) {
            trocarPosse(defensor)
            avancarRelogio(6)
            return Lance.Chute(minuto, atacante.time.nome, j.nome,
                Lance.Desfecho.BLOQUEADO)
        }

        val goleiro = qualidadeGoleiro(defensor)
        val probGol = (qualidade / (qualidade + goleiro * 1.55f)).coerceIn(0.03f, 0.52f)

        if (rng.nextFloat() < probGol) {
            val assistente = ultimoPassador?.takeIf { it !== portador }
            marcarGol(portador, assistente, defensor, dePenalti = false)
            return Lance.Gol(minuto, atacante.time.nome, j.nome,
                assistente?.jogador?.nome, dePenalti = false)
        }

        val sorteio = rng.nextFloat()
        val desfecho = when {
            sorteio < 0.46f -> Lance.Desfecho.DEFENDIDO
            sorteio < 0.86f -> Lance.Desfecho.PARA_FORA
            else -> Lance.Desfecho.NA_TRAVE
        }
        if (desfecho == Lance.Desfecho.DEFENDIDO) {
            atacante.stats = atacante.stats.copy(
                chutesNoGol = atacante.stats.chutesNoGol + 1)
        }
        trocarPosse(defensor)
        avancarRelogio(14)
        return Lance.Chute(minuto, atacante.time.nome, j.nome, desfecho)
    }

    private fun marcarGol(
        autor: JogadorEmCampo, assistente: JogadorEmCampo?,
        defensor: Equipe, dePenalti: Boolean,
    ) {
        if (atacante === casa) golsCasa++ else golsFora++
        atacante.stats = atacante.stats.copy(
            chutesNoGol = atacante.stats.chutesNoGol + 1)
        contribuicao.merge(autor.jogador.id, 1.7f, Float::plus)
        golsPor.merge(autor.jogador.id, 1, Int::plus)
        assistente?.let {
            contribuicao.merge(it.jogador.id, 1.0f, Float::plus)
            assistPor.merge(it.jogador.id, 1, Int::plus)
        }
        avancarRelogio(55)
        trocarPosse(defensor)
    }

    // ------------------------------------------------------ APOIO

    private fun trocarPosse(novoAtacante: Equipe, novoPortador: JogadorEmCampo? = null) {
        atacante = novoAtacante
        portador = novoPortador ?: novoAtacante.ativos.random(rng)
        ultimoPassador = null
        avancoConducao = 0f
    }

    private fun perderBola(defensor: Equipe, ladrao: JogadorEmCampo?): Lance {
        val vitima = portador.jogador.nome
        trocarPosse(defensor, ladrao)
        avancarRelogio(4)
        return Lance.PasseErrado(minuto, atacante.time.nome, vitima, null)
    }

    /** Onde o jogador está no eixo do ataque do próprio time (0..1). */
    private fun posicaoDeAtaque(jc: JogadorEmCampo): Float {
        val fase = if (atacante.ativos.contains(jc)) Fase.COM_POSSE else Fase.SEM_POSSE
        return jc.slot.em(fase).y
    }

    private fun posicaoDoLado(jc: JogadorEmCampo): Float =
        jc.slot.em(Fase.COM_POSSE).x

    private fun distanciaEntre(a: JogadorEmCampo, b: JogadorEmCampo): Float {
        val pa = a.slot.em(Fase.COM_POSSE)
        val pb = b.slot.em(Fase.COM_POSSE)
        return hypot(pa.x - pb.x, pa.y - pb.y)
    }

    /**
     * O marcador é o adversário cuja posição defensiva fica mais perto
     * de onde o portador está atacando. Como os dois times usam eixos
     * espelhados, converte antes de comparar.
     */
    private fun marcadorMaisProximo(
        defensor: Equipe, alvo: JogadorEmCampo,
    ): JogadorEmCampo? {
        val pAlvo = alvo.slot.em(Fase.COM_POSSE)
        return defensor.ativos
            .filter { it.slot.em(Fase.SEM_POSSE).papel != Papel.GOL }
            .minByOrNull {
                val d = it.slot.em(Fase.SEM_POSSE)
                // O defensor a 0.2 do próprio gol cobre o atacante a 0.8.
                hypot(d.x - (1f - pAlvo.x), (1f - d.y) - pAlvo.y)
            }
    }

    private fun pressaoSobre(jc: JogadorEmCampo, defensor: Equipe): Float {
        val marcador = marcadorMaisProximo(defensor, jc) ?: return 0f
        val pAlvo = jc.slot.em(Fase.COM_POSSE)
        val d = marcador.slot.em(Fase.SEM_POSSE)
        val dist = hypot(d.x - (1f - pAlvo.x), (1f - d.y) - pAlvo.y)
        val intensidade = defensor.forcas.tatica.intensidadePressao / 100f
        return ((1f - dist * 2.2f).coerceIn(0f, 1f)) * (0.5f + intensidade * 0.9f)
    }

    private fun qualidadeGoleiro(equipe: Equipe): Float {
        val g = equipe.forcas.goleiro ?: return 55f
        val j = g.jogador
        return (j.golReflexo * 0.4f + j.golMergulho * 0.3f +
                j.golPosicionamento * 0.3f) * g.mod.contribuicaoDefensiva
    }

    private fun avancarRelogio(s: Int) {
        segundos = (segundos + s).coerceAtMost(DURACAO_SEGUNDOS)
    }

    private fun desgastar() {
        listOf(casa, fora).forEach { e ->
            e.emCampo.forEach { jc ->
                val custo = jc.mod.desgaste *
                        (1f + jc.slot.amplitudeDeMovimento * 1.2f) *
                        (1f + e.forcas.tatica.intensidadePressao / 200f) *
                        (1.9f - jc.jogador.resistencia / 100f) * 0.011f /
                        jc.tracos.resistencia
                gas[jc.jogador.id] =
                    (gas.getValue(jc.jogador.id) - custo).coerceAtLeast(30f)
            }
        }
    }

    // ------------------------------------------------------ RETRATO

    private fun posicaoAbsoluta(jc: JogadorEmCampo, dono: Equipe): Pair<Float, Float> {
        val fase = if (dono === atacante) Fase.COM_POSSE else Fase.SEM_POSSE
        val p = jc.slot.em(fase)
        val avanco = if (jc === portador) avancoConducao else 0f
        return if (dono.mandante) {
            p.x to (0.06f + (p.y + avanco).coerceIn(0f, 1f) * 0.88f)
        } else {
            (1f - p.x) to (0.94f - (p.y + avanco).coerceIn(0f, 1f) * 0.88f)
        }
    }

    private fun retrato(lance: Lance?): Instante {
        val pecas = buildList {
            listOf(casa, fora).forEach { e ->
                e.emCampo.forEach { jc ->
                    if (jc.jogador.id in e.expulsos) return@forEach
                    val fase = if (e === atacante) Fase.COM_POSSE else Fase.SEM_POSSE
                    val (x, y) = posicaoAbsoluta(jc, e)
                    add(Peca(
                        jogadorId = jc.jogador.id,
                        nome = jc.jogador.nome,
                        sigla = jc.slot.em(fase).papel.sigla,
                        x = x, y = y,
                        doMandante = e.mandante,
                        comABola = jc === portador,
                        gas = gas.getValue(jc.jogador.id).toInt(),
                    ))
                }
            }
        }

        // A bola está nos pés de quem a tem. Sempre.
        val (bx, by) = posicaoAbsoluta(portador, atacante)
        val total = (casa.momentosComBola + fora.momentosComBola).coerceAtLeast(1)

        casa.stats = casa.stats.copy(posse = (casa.momentosComBola * 100) / total)
        fora.stats = fora.stats.copy(posse = 100 - casa.stats.posse)

        return Instante(
            minuto = minuto,
            golsMandante = golsCasa,
            golsVisitante = golsFora,
            pecas = pecas,
            bolaX = bx, bolaY = by,
            passeDeX = passeDe?.first,
            passeDeY = passeDe?.second,
            lanceNovo = lance,
            statsMandante = casa.stats,
            statsVisitante = fora.stats,
            mandanteComABola = atacante === casa,
            acabou = acabou,
        )
    }

    fun resultado(): Resultado {
        val notas = mutableMapOf<Int, Float>()
        listOf(casa to (golsCasa - golsFora), fora to (golsFora - golsCasa))
            .forEach { (e, saldo) ->
                e.emCampo.forEach { jc ->
                    val ef = (jc.eficiencia(Fase.SEM_POSSE) +
                            jc.eficiencia(Fase.COM_POSSE)) / 2f
                    notas[jc.jogador.id] = (
                            6.0f + saldo.coerceIn(-3, 3) * 0.18f +
                            (contribuicao[jc.jogador.id] ?: 0f) +
                            (ef - 0.75f) * 2f -
                            (if (jc.jogador.id in e.expulsos) 2f else 0f)
                            ).coerceIn(3f, 10f)
                }
            }

        return Resultado(
            golsMandante = golsCasa,
            golsVisitante = golsFora,
            lances = historico.toList(),
            statsMandante = casa.stats,
            statsVisitante = fora.stats,
            notas = notas,
            gasFinal = gas.mapValues { it.value.toInt() },
            golsPorJogador = golsPor.toMap(),
            assistenciasPorJogador = assistPor.toMap(),
            amarelosPorJogador = (casa.amarelados + fora.amarelados).toSet(),
            vermelhosPorJogador = (casa.expulsos + fora.expulsos).toSet(),
            titularesMandante = (casa.emCampo.map { it.jogador.id } +
                    participaram.filter { id ->
                        casa.emCampo.any { it.jogador.id == id }
                    }).distinct(),
            titularesVisitante = fora.emCampo.map { it.jogador.id },
        )
    }
}

/**
 * Simulação instantânea. É o MESMO motor rodado até o fim — então uma
 * partida assistida e uma simulada seguem exatamente as mesmas regras.
 */
class MotorPartida(private val rng: Random = Random.Default) {
    fun simular(mandante: TimeEmCampo, visitante: TimeEmCampo): Resultado {
        val p = PartidaAoVivo(mandante, visitante, rng)
        p.pularParaOFim()
        return p.resultado()
    }
}
