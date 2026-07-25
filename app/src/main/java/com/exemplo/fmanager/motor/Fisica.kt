package com.exemplo.fmanager.motor

import com.exemplo.fmanager.formacao.Fase
import com.exemplo.fmanager.formacao.Papel
import kotlin.math.abs
import kotlin.math.hypot

/*
 * FÍSICA DO CAMPO — POSIÇÕES CONTÍNUAS.
 *
 * O problema que isto resolve: até agora cada jogador tinha exatamente
 * TRÊS posições possíveis — a do slot em cada fase — e saltava entre elas.
 * Daí a sensação de travado. Não havia movimento, havia teletransporte
 * entre três pontos.
 *
 * Agora cada jogador tem uma posição contínua e uma velocidade, e caminha
 * para um alvo que muda a cada instante. O alvo não é o slot: é o slot
 * DESLOCADO por onde a bola está, pela altura da linha e pela compactação.
 * É por isso que o bloco inteiro desliza junto quando a bola muda de lado,
 * como num campo de verdade.
 *
 * Coordenadas: sempre na perspectiva do mandante.
 *   x = 0 lateral esquerda · 1 lateral direita
 *   y = 0 gol do mandante  · 1 gol do visitante
 */

data class Ponto(val x: Float, val y: Float) {
    operator fun minus(o: Ponto) = Ponto(x - o.x, y - o.y)
    operator fun plus(o: Ponto) = Ponto(x + o.x, y + o.y)
    operator fun times(k: Float) = Ponto(x * k, y * k)
    val comprimento get() = hypot(x, y)
}

/** Um jogador como a tela o desenha: posição real, velocidade, estado. */
data class PecaFisica(
    val jogadorId: Int,
    val nome: String,
    val sigla: String,
    val numero: Int,
    val doMandante: Boolean,
    val x: Float,
    val y: Float,
    /** 0 = parado · 1 = sprint. Define o visual de caminhar/correr. */
    val esforco: Float,
    val comABola: Boolean,
    val gas: Int,
    /** Marcando de perto alguém neste instante. */
    val emDuelo: Boolean = false,
)

/** Retrato físico do campo num instante. */
data class Quadro(
    val pecas: List<PecaFisica>,
    val bola: Ponto,
    /** Linha de impedimento de cada lado, em y. */
    val impedimentoParaMandante: Float,
    val impedimentoParaVisitante: Float,
    /** Quem está em posição irregular agora. */
    val impedidos: Set<Int>,
    val linhaDefensivaCasa: Float,
    val linhaDefensivaFora: Float,
)

class CampoFisico(
    private val obterCasa: () -> List<JogadorEmCampo>,
    private val obterFora: () -> List<JogadorEmCampo>,
) {
    private val pos = HashMap<Int, Ponto>()
    private val esforcoAtual = HashMap<Int, Float>()
    private val numeros = HashMap<Int, Int>()

    private var bola = Ponto(0.5f, 0.5f)
    private var bolaAlvo = Ponto(0.5f, 0.5f)
    private var duelo: Pair<Int, Int>? = null
    private var duracaoDuelo = 0f

    /** Velocidade máxima em fração de campo por segundo. */
    private fun velocidadeDe(jc: JogadorEmCampo): Float {
        val fisico = (jc.jogador.velocidade * 0.6f + jc.jogador.aceleracao * 0.4f)
        val cansaco = 0.72f + (jc.jogador.resistencia / 100f) * 0.28f
        // Um jogador rápido cruza o campo em ~11s; um lento em ~16s.
        return (0.055f + (fisico / 100f) * 0.038f) * cansaco
    }

    fun garantirInicio(mandanteComBola: Boolean) {
        atribuirNumeros()
        listOf(obterCasa() to true, obterFora() to false).forEach { (time, casa) ->
            time.forEach { jc ->
                if (pos.containsKey(jc.jogador.id)) return@forEach
                pos[jc.jogador.id] = ancora(jc, casa, Fase.SEM_POSSE)
                esforcoAtual[jc.jogador.id] = 0f
            }
        }
    }

    private fun atribuirNumeros() {
        if (numeros.isNotEmpty()) return
        listOf(obterCasa(), obterFora()).forEach { time ->
            time.forEachIndexed { i, jc ->
                numeros[jc.jogador.id] = when (jc.slot.em(Fase.SEM_POSSE).papel) {
                    Papel.GOL -> 1
                    else -> i + 1
                }
            }
        }
    }

    /** Posição do slot convertida para coordenada absoluta de campo. */
    private fun ancora(jc: JogadorEmCampo, doMandante: Boolean, fase: Fase): Ponto {
        val p = jc.slot.em(fase)
        return if (doMandante) Ponto(p.x, 0.05f + p.y * 0.90f)
        else Ponto(1f - p.x, 0.95f - p.y * 0.90f)
    }

    /**
     * O alvo de cada jogador neste instante.
     *
     * Três forças se somam à âncora da formação:
     *
     *   ATRAÇÃO PELA BOLA — todo mundo desliza para o lado onde a bola
     *     está. É o que faz o bloco compactar de verdade em vez de ficar
     *     esticado como um desenho técnico.
     *
     *   LINHA DEFENSIVA — os defensores compartilham uma altura comum que
     *     sobe e desce com a bola. Sem isso não existe linha, existem
     *     quatro jogadores parados em pontos fixos.
     *
     *   MARCAÇÃO — quem está mais perto do portador vai atrás dele.
     */
    private fun alvo(
        jc: JogadorEmCampo,
        doMandante: Boolean,
        fase: Fase,
        alturaLinha: Float,
        compactacao: Float,
        marcandoOPortador: Boolean,
        portadorPos: Ponto?,
    ): Ponto {
        val base = ancora(jc, doMandante, fase)
        val papel = jc.slot.em(fase).papel

        if (papel == Papel.GOL) {
            // Goleiro acompanha o lado da bola, sem sair da área.
            val gy = if (doMandante) 0.035f + (bola.y * 0.05f)
            else 0.965f - ((1f - bola.y) * 0.05f)
            return Ponto(0.5f + (bola.x - 0.5f) * 0.30f, gy)
        }

        if (marcandoOPortador && portadorPos != null) {
            // Vai para entre o portador e o próprio gol, não sobre ele.
            val golY = if (doMandante) 0f else 1f
            return Ponto(
                portadorPos.x + (0.5f - portadorPos.x) * 0.10f,
                portadorPos.y + (golY - portadorPos.y) * 0.09f,
            )
        }

        // Desliza para o lado da bola. Quem está perto se move mais.
        val distanciaDaBola = (base - bola).comprimento
        val forcaAtracao = (1f - distanciaDaBola * 1.5f).coerceIn(0f, 1f) *
                (0.14f + compactacao * 0.18f)
        val puxadoX = base.x + (bola.x - base.x) * forcaAtracao
        var puxadoY = base.y + (bola.y - base.y) * forcaAtracao * 0.55f

        // A linha defensiva sobe e desce como um bloco só.
        val ehDefensor = papel in setOf(Papel.ZAG, Papel.LE, Papel.LD)
        if (ehDefensor) {
            val alvoLinha = if (doMandante)
                (0.10f + alturaLinha * 0.34f) + (bola.y - 0.5f) * 0.22f
            else
                (0.90f - alturaLinha * 0.34f) + (bola.y - 0.5f) * 0.22f
            puxadoY = puxadoY * 0.35f + alvoLinha * 0.65f
        }

        return Ponto(puxadoX.coerceIn(0.02f, 0.98f), puxadoY.coerceIn(0.02f, 0.98f))
    }

    /**
     * Avança a física por dt segundos.
     *
     * Roda a 60fps independente do ritmo tático — é essa separação que
     * faz o movimento parecer contínuo mesmo quando os lances são raros.
     */
    fun avancar(
        dt: Float,
        mandanteComBola: Boolean,
        portadorId: Int?,
        alvoDaBola: Ponto,
        alturaLinhaCasa: Float,
        alturaLinhaFora: Float,
        compactacaoCasa: Float,
        compactacaoFora: Float,
    ) {
        garantirInicio(mandanteComBola)
        bolaAlvo = alvoDaBola

        val casa = obterCasa()
        val fora = obterFora()

        val faseCasa = if (mandanteComBola) Fase.COM_POSSE else Fase.SEM_POSSE
        val faseFora = if (mandanteComBola) Fase.SEM_POSSE else Fase.COM_POSSE

        val portadorPos = portadorId?.let { pos[it] }

        // Quem marca o portador: o adversário de campo mais próximo dele.
        val defensores = if (mandanteComBola) fora else casa
        val marcadorId = portadorPos?.let { pp ->
            defensores
                .filter { it.slot.em(Fase.SEM_POSSE).papel != Papel.GOL }
                .minByOrNull { (pos[it.jogador.id] ?: Ponto(.5f, .5f) ).let { p -> (p - pp).comprimento } }
                ?.jogador?.id
        }

        listOf(
            Triple(casa, true, faseCasa),
            Triple(fora, false, faseFora),
        ).forEach { (time, ehCasa, fase) ->
            val altura = if (ehCasa) alturaLinhaCasa else alturaLinhaFora
            val compacta = if (ehCasa) compactacaoCasa else compactacaoFora

            time.forEach { jc ->
                val id = jc.jogador.id
                val atual = pos[id] ?: ancora(jc, ehCasa, fase).also { pos[id] = it }

                val destino = alvo(
                    jc, ehCasa, fase, altura, compacta,
                    marcandoOPortador = id == marcadorId,
                    portadorPos = portadorPos,
                )

                val delta = destino - atual
                val distancia = delta.comprimento
                val vMax = velocidadeDe(jc)

                // Esforço: perto do alvo caminha, longe corre. É isso que
                // dá a leitura de "caminhando no campo".
                val esforcoDesejado = (distancia / 0.22f).coerceIn(0f, 1f)
                val e = esforcoAtual.getOrDefault(id, 0f)
                val novoEsforco = e + (esforcoDesejado - e) * (dt * 4f).coerceAtMost(1f)
                esforcoAtual[id] = novoEsforco

                if (distancia > 0.001f) {
                    val passo = (vMax * novoEsforco * dt).coerceAtMost(distancia)
                    pos[id] = atual + delta * (passo / distancia)
                }
            }
        }

        // A bola vai para o alvo mais rápido que gente, mas não instantâneo.
        val dBola = bolaAlvo - bola
        val distBola = dBola.comprimento
        if (distBola > 0.001f) {
            val vBola = 0.85f + distBola * 1.6f
            val passo = (vBola * dt).coerceAtMost(distBola)
            bola = bola + dBola * (passo / distBola)
        }

        duracaoDuelo = (duracaoDuelo - dt).coerceAtLeast(0f)
        if (duracaoDuelo <= 0f) duelo = null
    }

    /** Marca um duelo visível por um instante: desarme, drible, falta. */
    fun registrarDuelo(atacanteId: Int, defensorId: Int, segundos: Float = 0.9f) {
        duelo = atacanteId to defensorId
        duracaoDuelo = segundos
    }

    /**
     * LINHA DE IMPEDIMENTO.
     *
     * Regra real: a linha fica no penúltimo defensor. Como o goleiro é
     * quase sempre o último, na prática é o defensor de campo mais
     * recuado. Calculada da posição REAL, não do slot — então ela se move
     * de verdade quando a defesa sobe.
     */
    private fun linhaImpedimento(defensores: List<JogadorEmCampo>, defendeEmCima: Boolean): Float {
        val ys = defensores
            .filter { it.slot.em(Fase.SEM_POSSE).papel != Papel.GOL }
            .mapNotNull { pos[it.jogador.id]?.y }
        if (ys.isEmpty()) return if (defendeEmCima) 0.92f else 0.08f
        // Defendendo o gol em y=1, o mais recuado é o de maior y.
        return if (defendeEmCima) ys.max() else ys.min()
    }

    fun quadro(mandanteComBola: Boolean, portadorId: Int?): Quadro {
        val casa = obterCasa()
        val fora = obterFora()

        val impMandante = linhaImpedimento(fora, defendeEmCima = true)
        val impVisitante = linhaImpedimento(casa, defendeEmCima = false)

        // Quem está em posição irregular: só do lado que ataca, e só à
        // frente da bola — atrás da bola não existe impedimento.
        val impedidos = buildSet {
            if (mandanteComBola) {
                casa.forEach { jc ->
                    val p = pos[jc.jogador.id] ?: return@forEach
                    if (p.y > impMandante && p.y > bola.y &&
                        jc.slot.em(Fase.COM_POSSE).papel != Papel.GOL
                    ) add(jc.jogador.id)
                }
            } else {
                fora.forEach { jc ->
                    val p = pos[jc.jogador.id] ?: return@forEach
                    if (p.y < impVisitante && p.y < bola.y &&
                        jc.slot.em(Fase.COM_POSSE).papel != Papel.GOL
                    ) add(jc.jogador.id)
                }
            }
        }

        val pecas = buildList {
            listOf(casa to true, fora to false).forEach { (time, ehCasa) ->
                val fase = if (ehCasa == mandanteComBola) Fase.COM_POSSE
                else Fase.SEM_POSSE
                time.forEach { jc ->
                    val id = jc.jogador.id
                    val p = pos[id] ?: return@forEach
                    add(PecaFisica(
                        jogadorId = id,
                        nome = jc.jogador.nome,
                        sigla = jc.slot.em(fase).papel.sigla,
                        numero = numeros[id] ?: 0,
                        doMandante = ehCasa,
                        x = p.x, y = p.y,
                        esforco = esforcoAtual[id] ?: 0f,
                        comABola = id == portadorId,
                        gas = 100,
                        emDuelo = duelo?.let { id == it.first || id == it.second } == true,
                    ))
                }
            }
        }

        return Quadro(
            pecas = pecas,
            bola = bola,
            impedimentoParaMandante = impMandante,
            impedimentoParaVisitante = impVisitante,
            impedidos = impedidos,
            linhaDefensivaCasa = linhaImpedimento(casa, defendeEmCima = false),
            linhaDefensivaFora = linhaImpedimento(fora, defendeEmCima = true),
        )
    }

    /** Onde está a bola agora, para o motor tático consultar. */
    val posicaoDaBola: Ponto get() = bola

    fun posicaoDe(id: Int): Ponto? = pos[id]
}
