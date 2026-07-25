package com.exemplo.fmanager.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Estilos
import com.exemplo.fmanager.formacao.InstrucaoEquipe
import com.exemplo.fmanager.formacao.Tatica
import com.exemplo.fmanager.motor.*
import kotlinx.coroutines.delay

/*
 * TELA DA PARTIDA — DEITADA, CAMPO GRANDE.
 *
 * Três mudanças estruturais em relação à versão anterior:
 *
 * 1. ORIENTAÇÃO DEITADA. Um campo é 105x68 metros. Em pé, ele fica
 *    estreito e as peças se empilham. Deitado cabe na proporção certa e
 *    o campo ocupa a tela inteira.
 *
 * 2. RELÓGIO CONTÍNUO. Antes o ritmo era "um lance a cada X ms", então o
 *    relógio de jogo disparava e tudo acontecia junto. Agora o relógio de
 *    JOGO avança suave, e o motor só é chamado quando o relógio alcança o
 *    próximo lance. Os eventos se espalham no tempo sozinhos.
 *
 * 3. FÍSICA A 60FPS. As peças caminham para alvos que mudam com a bola,
 *    na velocidade do próprio atributo. Nada de saltar entre slots.
 */

enum class Ritmo(val rotulo: String, val segundosDeJogoPorSegundo: Float) {
    TEMPO_REAL("1x", 1f),
    LENTO("4x", 4f),
    NORMAL("8x", 8f),
    RAPIDO("20x", 20f);

    /** Duração real aproximada da partida neste ritmo. */
    val duracao: String get() = when (this) {
        TEMPO_REAL -> "90 min"
        LENTO -> "22 min"
        NORMAL -> "11 min"
        RAPIDO -> "4 min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPartidaAoVivo(
    partida: PartidaAoVivo,
    nomeMandante: String,
    nomeVisitante: String,
    souMandante: Boolean,
    taticaInicial: Tatica,
    onTerminar: () -> Unit,
) {
    // ------------------------------------------------ ORIENTAÇÃO
    val contexto = LocalContext.current
    DisposableEffect(Unit) {
        val atividade = contexto as? Activity
        val anterior = atividade?.requestedOrientation
        atividade?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            atividade?.requestedOrientation =
                anterior ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var ritmo by remember { mutableStateOf(Ritmo.NORMAL) }
    var pausado by remember { mutableStateOf(false) }
    var painel by remember { mutableStateOf<String?>(null) }
    var tatica by remember { mutableStateOf(taticaInicial) }
    val narracao = remember { mutableStateListOf<Lance>() }

    // Camada física: vive fora do sistema de estado, redesenha por tick.
    val fisica = remember {
        CampoFisico(
            obterCasa = { partida.escalacaoCasa },
            obterFora = { partida.escalacaoFora },
        )
    }
    var quadro by remember { mutableStateOf<Quadro?>(null) }
    val tick = remember { mutableIntStateOf(0) }

    // Relógio de jogo em segundos, com fração.
    var relogio by remember { mutableFloatStateOf(0f) }
    var acabou by remember { mutableStateOf(false) }

    // ------------------------------------- LAÇO ÚNICO A 60FPS
    LaunchedEffect(ritmo, pausado) {
        if (pausado || acabou) return@LaunchedEffect
        var ultimoNano = 0L

        while (!acabou) {
            withFrameNanos { agora ->
                val dt = if (ultimoNano == 0L) 0.016f
                else ((agora - ultimoNano) / 1_000_000_000f).coerceIn(0f, 0.05f)
                ultimoNano = agora

                val dtJogo = dt * ritmo.segundosDeJogoPorSegundo
                relogio += dtJogo

                // O motor só avança quando o relógio de jogo alcança o
                // ponto em que ele parou. É isso que espalha os lances.
                var guarda = 0
                while (partida.relogioDeJogo < relogio && !partida.acabou &&
                    guarda < 6
                ) {
                    val i = partida.passo()
                    i.lanceNovo?.let { l ->
                        narracao.add(0, l)
                        if (narracao.size > 100) narracao.removeAt(narracao.size - 1)
                    }
                    partida.consumirDuelo()?.let { (a, d) ->
                        fisica.registrarDuelo(a, d)
                    }
                    guarda++
                }

                fisica.avancar(
                    dt = dtJogo,
                    mandanteComBola = partida.mandanteComBola,
                    portadorId = partida.portadorId,
                    alvoDaBola = partida.alvoDaBola,
                    alturaLinhaCasa = partida.alturaLinhaCasa,
                    alturaLinhaFora = partida.alturaLinhaFora,
                    compactacaoCasa = partida.compactacaoCasa,
                    compactacaoFora = partida.compactacaoFora,
                )

                quadro = fisica.quadro(partida.mandanteComBola, partida.portadorId)
                tick.intValue++

                if (partida.acabou) acabou = true
            }
        }
    }

    // ------------------------------------------------------ LAYOUT
    Row(Modifier.fillMaxSize().background(Fundo)) {

        // ------------------------------------------- CAMPO (grande)
        Box(Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
            CampoDeitado(
                quadro = quadro,
                tick = tick,
                souMandante = souMandante,
                modifier = Modifier.fillMaxSize(),
            )

            // Placar sobreposto, canto superior
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = .62f),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(nomeMandante.take(14), color = Destaque,
                        style = MaterialTheme.typography.labelLarge)
                    Text(
                        "  ${partida.golsCasaAgora} - ${partida.golsForaAgora}  ",
                        style = EstiloNumeroPequeno, color = Texto, fontSize = 19.sp,
                    )
                    Text(nomeVisitante.take(14), color = Erro,
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    Text("${partida.minuto}'", color = Alerta,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // -------------------------------------- LATERAL DE CONTROLE
        Column(
            Modifier.width(196.dp).fillMaxHeight()
                .background(Superficie)
                .padding(10.dp),
        ) {
            val meu = if (souMandante) partida.statsCasa else partida.statsFora
            val dele = if (souMandante) partida.statsFora else partida.statsCasa

            Text("POSSE  ${meu.posse}%", style = EstiloRotulo, color = Destaque)
            Spacer(Modifier.height(4.dp))
            BarraComparativa("Finalizações", meu.chutes, dele.chutes)
            BarraComparativa("No gol", meu.chutesNoGol, dele.chutesNoGol)
            BarraComparativa("Passe %", meu.precisaoPasse, dele.precisaoPasse)
            BarraComparativa("Faltas", meu.faltas, dele.faltas)
            BarraComparativa("Escanteios", meu.escanteios, dele.escanteios)

            Spacer(Modifier.height(8.dp))

            if (!acabou) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Ritmo.entries.forEach { r ->
                        FilterChip(
                            selected = ritmo == r && !pausado,
                            onClick = { ritmo = r; pausado = false },
                            label = { Text(r.rotulo, fontSize = 10.sp) },
                        )
                    }
                }
                Text(ritmo.duracao, style = MaterialTheme.typography.labelSmall,
                    color = TextoFraco)

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = { pausado = !pausado },
                        label = { Text(if (pausado) "▶" else "❙❙", fontSize = 11.sp) })
                    AssistChip(onClick = { painel = "taticas" },
                        label = { Text("Táticas", fontSize = 10.sp) })
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(onClick = { pausado = true; painel = "subs" },
                        label = { Text("Trocas", fontSize = 10.sp) })
                    AssistChip(
                        onClick = { partida.pularParaOFim(); acabou = true },
                        label = { Text("Pular", fontSize = 10.sp) },
                    )
                }
            } else {
                Button(onClick = onTerminar, Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)) {
                    Text("Encerrar", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(narracao.filter { it.importancia != Importancia.ROTINA }) { l ->
                    Row {
                        Text("${l.minuto}'", Modifier.width(26.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco, fontSize = 9.sp)
                        Text(l.narrar(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (l.importancia) {
                                Importancia.DECISIVO -> Destaque
                                else -> TextoMedio
                            }, fontSize = 9.sp)
                    }
                }
            }
        }
    }

    when (painel) {
        "taticas" -> ModalBottomSheet(
            onDismissRequest = { painel = null }, containerColor = Superficie,
        ) { PainelTaticas(tatica, souMandante, partida) { tatica = it } }

        "subs" -> ModalBottomSheet(
            onDismissRequest = { painel = null; pausado = false },
            containerColor = Superficie,
        ) { PainelSubstituicoes(partida) { painel = null; pausado = false } }
    }
}

// ------------------------------------------------------- CAMPO DEITADO

@Composable
private fun CampoDeitado(
    quadro: Quadro?,
    tick: MutableIntState,
    souMandante: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A1512))) {
        @Suppress("UNUSED_EXPRESSION") tick.intValue

        /*
         * Campo deitado: o y do modelo (0 = gol do mandante) vira o X da
         * tela. O mandante ataca da esquerda para a direita.
         */
        val m = 10.dp.toPx()
        val larg = size.width - 2 * m
        val alt = size.height - 2 * m
        fun px(modeloY: Float) = m + modeloY * larg
        fun py(modeloX: Float) = m + modeloX * alt

        desenharGramado(m, larg, alt)

        val q = quadro ?: return@Canvas

        // ------------------------------------ LINHAS DE IMPEDIMENTO
        // Só a do lado que ataca — duas ao mesmo tempo confundiria.
        val linha = if (q.pecas.any { it.comABola && it.doMandante })
            q.impedimentoParaMandante else q.impedimentoParaVisitante
        val corLinha = if (q.pecas.any { it.comABola && it.doMandante })
            Erro else Destaque

        drawLine(
            color = corLinha.copy(alpha = .55f),
            start = Offset(px(linha), m),
            end = Offset(px(linha), m + alt),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)),
        )

        // ------------------------------------------------- PEÇAS
        val raio = 7.5.dp.toPx()

        q.pecas.forEach { p ->
            val cx = px(p.y)
            val cy = py(p.x)
            val minha = p.doMandante == souMandante
            val cor = if (minha) Destaque else Erro
            val impedido = p.jogadorId in q.impedidos

            // Rastro de esforço: quanto mais corre, mais visível o traço.
            if (p.esforco > 0.35f) {
                drawCircle(
                    cor.copy(alpha = .16f * p.esforco),
                    raio * (1.4f + p.esforco * 0.7f),
                    Offset(cx, cy),
                )
            }

            // Duelo: anel branco pulsante nos dois envolvidos.
            if (p.emDuelo) {
                drawCircle(Color.White.copy(alpha = .75f), raio + 4.dp.toPx(),
                    Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            }

            drawCircle(
                if (impedido) Alerta else cor,
                raio, Offset(cx, cy),
            )
            drawCircle(Color.Black.copy(alpha = .35f), raio,
                Offset(cx, cy), style = Stroke(width = 1f))

            if (p.comABola) {
                drawCircle(Color(0xFFF5F0E6), raio + 3.5.dp.toPx(),
                    Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            }
        }

        // -------------------------------------------------- BOLA
        val bx = px(q.bola.y)
        val by = py(q.bola.x)
        drawCircle(Color.Black.copy(alpha = .30f), 4.dp.toPx(),
            Offset(bx + 1.5f, by + 2f))
        drawCircle(Color(0xFFFDFBF5), 4.dp.toPx(), Offset(bx, by))
    }
}

private fun DrawScope.desenharGramado(m: Float, larg: Float, alt: Float) {
    val traco = Stroke(width = 1.6.dp.toPx())
    val cor = Color(0xFF4FD1C5).copy(alpha = .26f)

    // Faixas verticais de corte de grama.
    repeat(12) { k ->
        if (k % 2 == 0) drawRect(
            Color(0xFF10201C),
            Offset(m + larg / 12 * k, m),
            Size(larg / 12, alt),
        )
    }

    drawRect(cor, Offset(m, m), Size(larg, alt), style = traco)
    drawLine(cor, Offset(m + larg / 2, m), Offset(m + larg / 2, m + alt),
        strokeWidth = traco.width)
    drawCircle(cor, alt * 0.155f, Offset(m + larg / 2, m + alt / 2), style = traco)
    drawCircle(cor, 2.5.dp.toPx(), Offset(m + larg / 2, m + alt / 2))

    // Grandes áreas, pequenas áreas e gols nos dois extremos.
    val largGA = larg * 0.165f
    val altGA = alt * 0.58f
    val largPA = larg * 0.055f
    val altPA = alt * 0.27f
    val altGol = alt * 0.13f

    listOf(true, false).forEach { esquerda ->
        val x0GA = if (esquerda) m else m + larg - largGA
        drawRect(cor, Offset(x0GA, m + (alt - altGA) / 2),
            Size(largGA, altGA), style = traco)

        val x0PA = if (esquerda) m else m + larg - largPA
        drawRect(cor, Offset(x0PA, m + (alt - altPA) / 2),
            Size(largPA, altPA), style = traco)

        val x0Gol = if (esquerda) m - 4.dp.toPx() else m + larg
        drawRect(cor.copy(alpha = .5f), Offset(x0Gol, m + (alt - altGol) / 2),
            Size(4.dp.toPx(), altGol))

        // Marca do pênalti.
        val xPen = if (esquerda) m + larg * 0.11f else m + larg * 0.89f
        drawCircle(cor, 2.dp.toPx(), Offset(xPen, m + alt / 2))
    }
}

// ----------------------------------------------------------- PAINÉIS

@Composable
private fun PainelTaticas(
    tatica: Tatica,
    souMandante: Boolean,
    partida: PartidaAoVivo,
    onMudar: (Tatica) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)
        .padding(horizontal = 18.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                Text("Mentalidade — ${tatica.rotuloMentalidade}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = Texto)
                Text("${tatica.mentalidade}", style = EstiloNumeroPequeno,
                    color = Destaque)
            }
            Slider(
                value = tatica.mentalidade.toFloat(),
                onValueChange = {
                    val nova = tatica.copy(mentalidade = it.toInt())
                    onMudar(nova)
                    partida.atualizarTatica(souMandante, nova)
                },
                valueRange = 0f..100f,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Estilos.todos.forEach { (nome, preset) ->
                    FilterChip(tatica == preset, {
                        onMudar(preset)
                        partida.atualizarTatica(souMandante, preset)
                    }, { Text(nome, fontSize = 10.sp) })
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        items(InstrucaoEquipe.entries.toList()) { i ->
            val ativa = tatica.tem(i)
            Row(
                Modifier.fillMaxWidth().clickable {
                    val nova = tatica.alternar(i)
                    onMudar(nova)
                    partida.atualizarTatica(souMandante, nova)
                }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(i.rotulo, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ativa) Destaque else TextoMedio)
                Switch(ativa, {
                    val nova = tatica.alternar(i)
                    onMudar(nova)
                    partida.atualizarTatica(souMandante, nova)
                })
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun PainelSubstituicoes(partida: PartidaAoVivo, onFechar: () -> Unit) {
    var saindo by remember { mutableStateOf<Jogador?>(null) }
    var versao by remember { mutableIntStateOf(0) }

    Column(Modifier.padding(horizontal = 18.dp)) {
        @Suppress("UNUSED_EXPRESSION") versao
        Text(
            if (partida.podeSubstituir) "Toque em quem sai, depois em quem entra"
            else "Substituições esgotadas",
            style = MaterialTheme.typography.bodySmall,
            color = if (partida.podeSubstituir) TextoMedio else Erro,
        )
    }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)
        .padding(horizontal = 14.dp)) {
        item { Text("EM CAMPO", style = EstiloRotulo, color = TextoFraco) }
        items(partida.elencoEmCampo, key = { "c${it.id}" }) { j ->
            LinhaElenco(
                jogador = j, detalhe = j.posicao, valorDireita = "${j.geral}",
                corValor = if (saindo?.id == j.id) Destaque else Texto,
                onClicar = { saindo = j },
            )
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text("NO BANCO", style = EstiloRotulo, color = TextoFraco)
        }
        items(partida.bancoDisponivel, key = { "b${it.id}" }) { j ->
            LinhaElenco(
                jogador = j,
                detalhe = "${j.posicao} · descansado",
                valorDireita = "${j.geral}",
                corDetalhe = Destaque,
                onClicar = {
                    saindo?.let { s ->
                        partida.substituir(s.id, j)
                        versao++
                        onFechar()
                    }
                },
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
