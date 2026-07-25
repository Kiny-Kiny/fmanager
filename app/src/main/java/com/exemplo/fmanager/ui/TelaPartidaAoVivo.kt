package com.exemplo.fmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Estilos
import com.exemplo.fmanager.formacao.Tatica
import com.exemplo.fmanager.motor.*
import kotlinx.coroutines.delay

/*
 * TELA DA PARTIDA AO VIVO.
 *
 * A bola está sempre nos pés de alguém — quem tem a posse aparece com um
 * anel. Cada passe desenha uma linha da origem ao destino, então dá para
 * acompanhar a construção da jogada em vez de ver uma bola à deriva.
 *
 * PERFORMANCE: as posições animadas ficam num HashMap comum, fora do
 * sistema de estado. O redesenho é disparado por um contador lido dentro
 * do Canvas, então cada frame roda só a fase de desenho. Sem isso, 22
 * peças a 60fps recomporiam a tela inteira 60 vezes por segundo.
 */

enum class Velocidade(val rotulo: String, val msPorLance: Long) {
    DETALHADO("Lance a lance", 320),
    NORMAL("Normal", 130),
    RAPIDO("Rápido", 45),
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
    var painel by remember { mutableStateOf<String?>(null) }
    var tatica by remember { mutableStateOf(taticaInicial) }
    var soDestaques by remember { mutableStateOf(false) }
    val narracao = remember { mutableStateListOf<Lance>() }

    val posicoes = remember { HashMap<Int, Offset>() }
    var bola by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var passe by remember { mutableStateOf<Offset?>(null) }
    val frame = remember { mutableIntStateOf(0) }

    // ---------------------------------------------- LOOP DA PARTIDA
    LaunchedEffect(velocidade, pausado, pulou) {
        if (pulou) {
            instante = partida.pularParaOFim()
            narracao.clear()
            narracao.addAll(partida.lancesAteAgora.reversed())
            return@LaunchedEffect
        }
        if (pausado) return@LaunchedEffect

        while (!partida.acabou) {
            val i = partida.passo()
            instante = i
            i.lanceNovo?.let { narracao.add(0, it) }
            while (narracao.size > 120) narracao.removeAt(narracao.size - 1)
            delay(velocidade.msPorLance)
        }
    }

    // ---------------------------------------------- LOOP DE ANIMAÇÃO
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val i = instante ?: return@withFrameNanos
                i.pecas.forEach { p ->
                    val alvo = Offset(p.x, p.y)
                    val atual = posicoes[p.jogadorId] ?: alvo
                    posicoes[p.jogadorId] = Offset(
                        atual.x + (alvo.x - atual.x) * 0.13f,
                        atual.y + (alvo.y - atual.y) * 0.13f,
                    )
                }
                bola = Offset(
                    bola.x + (i.bolaX - bola.x) * 0.30f,
                    bola.y + (i.bolaY - bola.y) * 0.30f,
                )
                passe = if (i.passeDeX != null && i.passeDeY != null)
                    Offset(i.passeDeX, i.passeDeY) else null
                frame.intValue++
            }
        }
    }

    val i = instante

    Column(Modifier.fillMaxSize().background(Fundo)) {

        // -------------------------------------------------- PLACAR
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(nomeMandante, Modifier.weight(1f), maxLines = 1,
                style = MaterialTheme.typography.bodyMedium, color = Texto)
            Text("${i?.golsMandante ?: 0} - ${i?.golsVisitante ?: 0}",
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Destaque)
            Text(nomeVisitante, Modifier.weight(1f), maxLines = 1,
                style = MaterialTheme.typography.bodyMedium, color = Texto)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${i?.minuto ?: 0}'",
                style = MaterialTheme.typography.labelLarge, color = Alerta)
            i?.let {
                val meu = if (souMandante) it.statsMandante else it.statsVisitante
                val dele = if (souMandante) it.statsVisitante else it.statsMandante
                Text("Posse ${meu.posse}%  ·  ${meu.chutes}x${dele.chutes} chutes  ·  " +
                        "passe ${meu.precisaoPasse}%",
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            }
        }

        // --------------------------------------------------- CAMPO
        CampoAoVivo(
            posicoes = posicoes,
            pecas = i?.pecas ?: emptyList(),
            bola = bola,
            passeDe = passe,
            frame = frame,
            souMandante = souMandante,
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 12.dp),
        )

        // ----------------------------------------------- CONTROLES
        if (!partida.acabou) {
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Velocidade.entries.forEach { v ->
                    FilterChip(
                        selected = velocidade == v && !pausado,
                        onClick = { velocidade = v; pausado = false },
                        label = { Text(v.rotulo, fontSize = 11.sp) },
                    )
                }
                AssistChip(onClick = { pausado = !pausado },
                    label = { Text(if (pausado) "Retomar" else "Pausar",
                        fontSize = 11.sp) })
                AssistChip(onClick = { painel = "taticas" },
                    label = { Text("Táticas", fontSize = 11.sp) })
                AssistChip(onClick = { pausado = true; painel = "subs" },
                    label = { Text("Substituir", fontSize = 11.sp) })
                AssistChip(onClick = { pulou = true },
                    label = { Text("Pular", fontSize = 11.sp) })
            }
        } else {
            Button(onClick = onTerminar,
                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Encerrar partida")
            }
        }

        // ------------------------------------------------ NARRAÇÃO
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NARRAÇÃO", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            TextButton(onClick = { soDestaques = !soDestaques }) {
                Text(if (soDestaques) "Ver tudo" else "Só destaques",
                    fontSize = 11.sp)
            }
        }

        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 180.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val lista = if (soDestaques)
                narracao.filter { it.importancia != Importancia.ROTINA }
            else narracao
            items(lista) { l -> LinhaNarracao(l) }
        }
        Spacer(Modifier.height(8.dp))
    }

    // ---------------------------------------------------- PAINÉIS
    when (painel) {
        "taticas" -> ModalBottomSheet(
            onDismissRequest = { painel = null },
            containerColor = Superficie,
        ) {
            PainelTaticas(tatica, souMandante, partida) { tatica = it }
        }

        "subs" -> ModalBottomSheet(
            onDismissRequest = { painel = null; pausado = false },
            containerColor = Superficie,
        ) {
            PainelSubstituicoes(partida, i) { painel = null; pausado = false }
        }
    }
}

// ------------------------------------------------------------- CAMPO

@Composable
private fun CampoAoVivo(
    posicoes: HashMap<Int, Offset>,
    pecas: List<Peca>,
    bola: Offset,
    passeDe: Offset?,
    frame: MutableIntState,
    souMandante: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.clip(MaterialTheme.shapes.large).background(Color(0xFF0D1B1E))
    ) {
        @Suppress("UNUSED_EXPRESSION") frame.intValue

        val l = size.width
        val a = size.height
        val traco = Stroke(width = 1.5.dp.toPx())
        val linha = Color(0xFF4FD1C5).copy(alpha = .20f)
        val m = 8.dp.toPx()

        repeat(10) { k ->
            if (k % 2 == 0) drawRect(
                Color(0xFF122326), Offset(0f, a / 10 * k), Size(l, a / 10))
        }
        drawRect(linha, Offset(m, m), Size(l - 2 * m, a - 2 * m), style = traco)
        drawLine(linha, Offset(m, a / 2), Offset(l - m, a / 2),
            strokeWidth = traco.width)
        drawCircle(linha, l * .13f, Offset(l / 2, a / 2), style = traco)

        val largGA = l * .52f; val altGA = a * .13f
        drawRect(linha, Offset((l - largGA) / 2, a - m - altGA),
            Size(largGA, altGA), style = traco)
        drawRect(linha, Offset((l - largGA) / 2, m),
            Size(largGA, altGA), style = traco)

        val bolaPx = Offset(bola.x * l, (1f - bola.y) * a)

        // Linha do passe em curso: mostra a construção da jogada.
        passeDe?.let { de ->
            drawLine(
                color = Color(0xFFF5F0E6).copy(alpha = .55f),
                start = Offset(de.x * l, (1f - de.y) * a),
                end = bolaPx,
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        val raio = 9.dp.toPx()

        pecas.forEach { p ->
            val pos = posicoes[p.jogadorId] ?: Offset(p.x, p.y)
            val cx = pos.x * l
            val cy = (1f - pos.y) * a

            val minha = p.doMandante == souMandante
            val cor = if (minha) Destaque else Color(0xFFE05C5C)
            val alpha = (0.45f + (p.gas / 100f) * 0.55f).coerceIn(0.4f, 1f)

            drawCircle(cor.copy(alpha = alpha), raio, Offset(cx, cy))

            // Quem tem a bola ganha um anel — dá pra seguir a jogada.
            if (p.comABola) {
                drawCircle(Color(0xFFF5F0E6), raio + 4.dp.toPx(),
                    Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            } else if (minha) {
                drawCircle(Color.White.copy(alpha = .30f), raio,
                    Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
            }
        }

        drawCircle(Color(0xFFF5F0E6), 4.5.dp.toPx(), bolaPx)
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
    Column(Modifier.padding(20.dp)) {
        Text("Ajustar durante a partida",
            style = MaterialTheme.typography.titleMedium, color = Texto)
        Text("Vale do próximo lance em diante.",
            style = MaterialTheme.typography.bodySmall, color = TextoFraco)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Estilos.todos.forEach { (nome, preset) ->
                FilterChip(
                    selected = tatica == preset,
                    onClick = {
                        onMudar(preset)
                        partida.atualizarTatica(souMandante, preset)
                    },
                    label = { Text(nome, fontSize = 11.sp) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        listOf<Triple<String, Int, (Int) -> Tatica>>(
            Triple("Altura da linha", tatica.alturaLinha)
            { tatica.copy(alturaLinha = it) },
            Triple("Intensidade de pressão", tatica.intensidadePressao)
            { tatica.copy(intensidadePressao = it) },
            Triple("Velocidade de construção", tatica.velocidadeConstrucao)
            { tatica.copy(velocidadeConstrucao = it) },
            Triple("Risco no passe", tatica.riscoNoPasse)
            { tatica.copy(riscoNoPasse = it) },
            Triple("Liberdade criativa", tatica.liberdadeCriativa)
            { tatica.copy(liberdadeCriativa = it) },
        ).forEach { (titulo, valor, gerar) ->
            Row(Modifier.fillMaxWidth()) {
                Text(titulo, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                Text("$valor", style = MaterialTheme.typography.bodySmall,
                    color = Destaque)
            }
            Slider(
                value = valor.toFloat(),
                onValueChange = {
                    val nova = gerar(it.toInt())
                    onMudar(nova)
                    partida.atualizarTatica(souMandante, nova)
                },
                valueRange = 0f..100f,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PainelSubstituicoes(
    partida: PartidaAoVivo,
    instante: Instante?,
    onFechar: () -> Unit,
) {
    var saindo by remember { mutableStateOf<Jogador?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var versao by remember { mutableIntStateOf(0) }

    val gasPorId = remember(instante) {
        instante?.pecas?.associate { it.jogadorId to it.gas } ?: emptyMap()
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Substituições",
            style = MaterialTheme.typography.titleMedium, color = Texto)
        @Suppress("UNUSED_EXPRESSION") versao
        Text(
            if (partida.podeSubstituir)
                "Toque em quem sai, depois em quem entra"
            else "Você já usou todas as substituições",
            style = MaterialTheme.typography.bodySmall,
            color = if (partida.podeSubstituir) TextoFraco else Erro,
        )
        aviso?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Alerta)
        }
        Spacer(Modifier.height(14.dp))
    }

    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            Text("EM CAMPO", style = MaterialTheme.typography.labelSmall,
                color = TextoFraco)
        }
        items(partida.elencoEmCampo, key = { "campo${it.id}" }) { j ->
            val g = gasPorId[j.id] ?: 100
            LinhaSubstituicao(
                jogador = j,
                detalhe = "gás $g%",
                corDetalhe = when {
                    g >= 70 -> TextoFraco
                    g >= 50 -> Alerta
                    else -> Erro
                },
                selecionado = saindo?.id == j.id,
            ) { saindo = j; aviso = null }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("NO BANCO", style = MaterialTheme.typography.labelSmall,
                color = TextoFraco)
        }
        items(partida.bancoDisponivel, key = { "banco${it.id}" }) { j ->
            LinhaSubstituicao(
                jogador = j,
                detalhe = "${j.posicao} · descansado",
                corDetalhe = Destaque,
                selecionado = false,
            ) {
                val sai = saindo
                if (sai == null) {
                    aviso = "Escolha primeiro quem sai."
                } else {
                    val feito = partida.substituir(sai.id, j)
                    if (feito == null) {
                        aviso = "Não foi possível fazer a troca."
                    } else {
                        saindo = null
                        versao++
                        onFechar()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LinhaSubstituicao(
    jogador: Jogador,
    detalhe: String,
    corDetalhe: Color,
    selecionado: Boolean,
    onClicar: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClicar),
        shape = MaterialTheme.shapes.small,
        color = if (selecionado) Destaque.copy(alpha = .18f) else SuperficieAlta,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(jogador.nome, maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selecionado) Destaque else Texto)
                Text(detalhe, style = MaterialTheme.typography.labelSmall,
                    color = corDetalhe)
            }
            Text("${jogador.geral}", fontWeight = FontWeight.Bold, color = Texto)
        }
    }
}

@Composable
private fun LinhaNarracao(l: Lance) {
    val cor = when (l.importancia) {
        Importancia.DECISIVO -> Destaque
        Importancia.DESTAQUE -> Texto
        Importancia.ROTINA -> TextoFraco.copy(alpha = .75f)
    }
    Row(Modifier.fillMaxWidth()) {
        Text("${l.minuto}'", Modifier.width(32.dp),
            style = MaterialTheme.typography.labelSmall, color = TextoFraco)
        Text(l.narrar(), style = MaterialTheme.typography.bodySmall, color = cor,
            fontWeight = if (l.importancia == Importancia.DECISIVO)
                FontWeight.Bold else FontWeight.Normal)
    }
}
