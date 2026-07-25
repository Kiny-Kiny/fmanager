package com.exemplo.fmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.formacao.Estilos
import com.exemplo.fmanager.formacao.Fase
import com.exemplo.fmanager.formacao.Tatica
import com.exemplo.fmanager.motor.Evento
import com.exemplo.fmanager.motor.Instante
import com.exemplo.fmanager.motor.PartidaAoVivo
import kotlinx.coroutines.delay


/*
 * TELA DA PARTIDA AO VIVO.
 *
 * Duas coisas rodam em paralelo:
 *   - o LOOP DE SIMULAÇÃO, que chama partida.passo() no ritmo escolhido
 *   - o LOOP DE ANIMAÇÃO, que a 60fps desliza as peças para o alvo
 *
 * PERFORMANCE: as posições animadas ficam num HashMap comum, que NÃO é
 * estado do Compose. O redesenho é disparado por um único contador lido
 * dentro do Canvas — então a cada frame roda só a fase de desenho, não
 * a de composição. Sem isso, 22 peças a 60fps recomporiam a tela inteira
 * 60 vezes por segundo.
 */

enum class Velocidade(val rotulo: String, val duracaoSegundos: Int) {
    LENTO("Tempo real reduzido", 240),
    NORMAL("2 minutos", 120),
    RAPIDO("30 segundos", 30),
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
    var instante by remember { mutableStateOf<Instante?>(null) }
    var velocidade by remember { mutableStateOf(Velocidade.NORMAL) }
    var pausado by remember { mutableStateOf(false) }
    var pulou by remember { mutableStateOf(false) }
    var abrirTaticas by remember { mutableStateOf(false) }
    var tatica by remember { mutableStateOf(taticaInicial) }
    val narracao = remember { mutableStateListOf<Evento>() }

    // Posições animadas: mapa comum, fora do sistema de estado.
    val posicoes = remember { HashMap<Int, Offset>() }
    var bola by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val frame = remember { mutableIntStateOf(0) }

    // ---------------------------------------------- LOOP DE SIMULAÇÃO
    LaunchedEffect(velocidade, pausado, pulou) {
        if (pulou) {
            instante = partida.pularParaOFim()
            narracao.clear()
            narracao.addAll(partida.eventosAteAgora.reversed())
            return@LaunchedEffect
        }
        if (pausado) return@LaunchedEffect

        val intervalo = (velocidade.duracaoSegundos * 1000L) / 180L
        while (!partida.acabou) {
            val i = partida.passo()
            instante = i
            i.eventoNovo?.let { narracao.add(0, it) }
            delay(intervalo)
        }
    }

    // ----------------------------------------------- LOOP DE ANIMAÇÃO
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val i = instante ?: return@withFrameNanos
                i.pecas.forEach { p ->
                    val alvo = Offset(p.x, p.y)
                    val atual = posicoes[p.jogadorId] ?: alvo
                    posicoes[p.jogadorId] = Offset(
                        atual.x + (alvo.x - atual.x) * 0.10f,
                        atual.y + (alvo.y - atual.y) * 0.10f,
                    )
                }
                bola = Offset(
                    bola.x + (i.bolaX - bola.x) * 0.22f,
                    bola.y + (i.bolaY - bola.y) * 0.22f,
                )
                frame.intValue++
            }
        }
    }

    val i = instante

    Column(Modifier.fillMaxSize().background(Fundo)) {

        // ----------------------------------------------- PLACAR
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(nomeMandante, Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = Texto,
                maxLines = 1)
            Text(
                "${i?.golsMandante ?: 0} - ${i?.golsVisitante ?: 0}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Destaque,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text(nomeVisitante, Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = Texto,
                maxLines = 1)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${i?.minuto ?: 0}'",
                style = MaterialTheme.typography.labelLarge, color = Alerta)
            Text("Posse ${i?.posseMandante ?: 50}%  ·  " +
                    "${i?.chutesMandante ?: 0} x ${i?.chutesVisitante ?: 0} chutes",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)
        }

        // Mostra a fase atual do seu time — é aqui que você vê a
        // formação mudando conforme desenhou no editor.
        i?.let {
            val minhaFase = if (souMandante) it.faseMandante
            else if (it.faseMandante == Fase.COM_POSSE) Fase.SEM_POSSE
            else Fase.COM_POSSE
            Text(
                "Seu time: ${minhaFase.rotulo.lowercase()}",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (minhaFase == Fase.COM_POSSE) Destaque else TextoFraco,
            )
        }

        // ------------------------------------------------- CAMPO
        CampoAoVivo(
            posicoes = posicoes,
            pecas = i?.pecas ?: emptyList(),
            bola = bola,
            frame = frame,
            souMandante = souMandante,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 12.dp),
        )

        // -------------------------------------------- CONTROLES
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!partida.acabou) {
                Velocidade.entries.forEach { v ->
                    FilterChip(
                        selected = velocidade == v && !pausado,
                        onClick = { velocidade = v; pausado = false },
                        label = { Text(v.rotulo, fontSize = 12.sp) },
                    )
                }
                AssistChip(
                    onClick = { pausado = !pausado },
                    label = { Text(if (pausado) "Retomar" else "Pausar") },
                )
                AssistChip(onClick = { pulou = true }, label = { Text("Pular") })
                AssistChip(
                    onClick = { abrirTaticas = true },
                    label = { Text("Táticas") },
                )
            }
        }

        if (partida.acabou) {
            Button(
                onClick = onTerminar,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Encerrar partida") }
        }

        // ------------------------------------------------ NARRAÇÃO
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 160.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(narracao) { ev -> LinhaNarracao(ev) }
        }
        Spacer(Modifier.height(8.dp))
    }

    // -------------------------------- TÁTICAS NO MEIO DA PARTIDA
    if (abrirTaticas) {
        ModalBottomSheet(
            onDismissRequest = { abrirTaticas = false },
            containerColor = Superficie,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Ajustar no intervalo da jogada",
                    style = MaterialTheme.typography.titleMedium, color = Texto)
                Text("As mudanças valem do próximo lance em diante.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                Spacer(Modifier.height(16.dp))

                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Estilos.todos.forEach { (nome, preset) ->
                        FilterChip(
                            selected = tatica == preset,
                            onClick = {
                                tatica = preset
                                partida.atualizarTatica(souMandante, preset)
                            },
                            label = { Text(nome) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                AjusteRapido("Altura da linha", tatica.alturaLinha) {
                    tatica = tatica.copy(alturaLinha = it)
                    partida.atualizarTatica(souMandante, tatica)
                }
                AjusteRapido("Intensidade de pressão", tatica.intensidadePressao) {
                    tatica = tatica.copy(intensidadePressao = it)
                    partida.atualizarTatica(souMandante, tatica)
                }
                AjusteRapido("Velocidade de construção", tatica.velocidadeConstrucao) {
                    tatica = tatica.copy(velocidadeConstrucao = it)
                    partida.atualizarTatica(souMandante, tatica)
                }
                AjusteRapido("Risco no passe", tatica.riscoNoPasse) {
                    tatica = tatica.copy(riscoNoPasse = it)
                    partida.atualizarTatica(souMandante, tatica)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ------------------------------------------------------------- CAMPO

@Composable
private fun CampoAoVivo(
    posicoes: HashMap<Int, Offset>,
    pecas: List<com.exemplo.fmanager.motor.Peca>,
    bola: Offset,
    frame: MutableIntState,
    souMandante: Boolean,
    modifier: Modifier = Modifier,
) {
    val corMinha = Destaque
    val corDeles = Color(0xFFE05C5C)

    Canvas(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF0D1B1E))
    ) {
        // Lê o contador aqui dentro: invalida só o desenho, não a
        // composição. É o que mantém 60fps sem custo.
        @Suppress("UNUSED_EXPRESSION") frame.intValue

        val l = size.width
        val a = size.height
        val traco = Stroke(width = 1.5.dp.toPx())
        val linha = Color(0xFF4FD1C5).copy(alpha = .22f)
        val m = 8.dp.toPx()

        // Gramado listrado
        repeat(10) { k ->
            if (k % 2 == 0) drawRect(
                Color(0xFF122326), Offset(0f, a / 10 * k), Size(l, a / 10),
            )
        }

        drawRect(linha, Offset(m, m), Size(l - 2 * m, a - 2 * m), style = traco)
        drawLine(linha, Offset(m, a / 2), Offset(l - m, a / 2), strokeWidth = traco.width)
        drawCircle(linha, l * .13f, Offset(l / 2, a / 2), style = traco)

        val largGA = l * .52f; val altGA = a * .13f
        drawRect(linha, Offset((l - largGA) / 2, a - m - altGA),
            Size(largGA, altGA), style = traco)
        drawRect(linha, Offset((l - largGA) / 2, m), Size(largGA, altGA), style = traco)

        val raio = 9.dp.toPx()

        // Peças
        pecas.forEach { p ->
            val pos = posicoes[p.jogadorId] ?: Offset(p.x, p.y)
            // y=1 é o gol adversário do mandante, que fica em cima.
            val cx = pos.x * l
            val cy = (1f - pos.y) * a

            val minha = p.doMandante == souMandante
            val cor = if (minha) corMinha else corDeles

            // Anel de gás: quem está acabando fica translúcido.
            val alpha = (0.45f + (p.gas / 100f) * 0.55f).coerceIn(0.4f, 1f)

            drawCircle(cor.copy(alpha = alpha), raio, Offset(cx, cy))
            if (minha) {
                drawCircle(Color.White.copy(alpha = .35f), raio,
                    Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
            }
        }

        // Bola
        drawCircle(Color(0xFFF5F0E6), 5.dp.toPx(),
            Offset(bola.x * l, (1f - bola.y) * a))
        drawCircle(Color.Black.copy(alpha = .35f), 5.dp.toPx(),
            Offset(bola.x * l, (1f - bola.y) * a), style = Stroke(1.dp.toPx()))
    }
}

// --------------------------------------------------------- AUXILIARES

@Composable
private fun LinhaNarracao(ev: Evento) {
    val (texto, cor) = when (ev) {
        is Evento.Gol -> "⚽ GOL! ${ev.autor}" +
                (ev.assistencia?.let { " (assist. $it)" } ?: "") to Destaque
        is Evento.Chute -> (if (ev.noAlvo) "◎ " else "○ ") +
                "Chute de ${ev.autor}" to TextoFraco
        is Evento.Cartao -> (if (ev.vermelho) "🟥 " else "🟨 ") +
                ev.autor to (if (ev.vermelho) Erro else Alerta)
        is Evento.Lesao -> "✚ ${ev.autor} sentiu (${ev.semanas} sem.)" to Erro
    }
    Row(Modifier.fillMaxWidth()) {
        Text("${ev.minuto}'", Modifier.width(34.dp),
            style = MaterialTheme.typography.labelSmall, color = TextoFraco)
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor)
    }
}

@Composable
private fun AjusteRapido(titulo: String, valor: Int, aoMudar: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(titulo, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall, color = TextoFraco)
        Text("$valor", style = MaterialTheme.typography.bodySmall, color = Destaque)
    }
    Slider(valor.toFloat(), { aoMudar(it.toInt()) }, valueRange = 0f..100f)
}
