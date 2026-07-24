package com.exemplo.fmanager.formacao

/*
 * EDITOR DE FORMAÇÃO POR FASE.
 *
 * As abas no topo trocam a fase que você está editando. Cada fase guarda
 * as próprias coordenadas, então arrastar o volante para trás na aba
 * "Com a bola" não mexe na organização defensiva.
 *
 * O fantasma tracejado mostra onde o jogador fica nas OUTRAS fases, com
 * uma linha ligando os dois pontos. É assim que você enxerga o movimento.
 *
 * A cor do token conta a história: claro se ele joga naquela posição,
 * laranja se é adaptação, vermelho se é gambiarra.
 *
 * PERFORMANCE: a posição é lida dentro do lambda de offset { }, que roda
 * na fase de layout. Arrastar não recompõe a tela — só reposiciona.
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.familiaridade
import com.exemplo.fmanager.dados.geralEm
import kotlin.math.roundToInt

private val Fundo = Color(0xFF0B1416)
private val Gramado = Color(0xFF0D1B1E)
private val GramadoClaro = Color(0xFF122326)
private val Linhas = Color(0xFF4FD1C5)
private val Destaque = Color(0xFF4FD1C5)
private val TokenLinha = Color(0xFFE8E3D9)
private val TokenAviso = Color(0xFFF2A65A)
private val Texto = Color(0xFFE8E3D9)
private val TextoFraco = Color(0xFF7C9296)
private val Erro = Color(0xFFE05C5C)

private val TAMANHO_TOKEN = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorFormacaoScreen(
    slots: List<Slot>,
    elenco: Map<Int, Jogador> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    var fase by remember { mutableStateOf(Fase.SEM_POSSE) }
    var selecionado by remember { mutableStateOf<Slot?>(null) }
    var mostrarFantasmas by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize().background(Fundo)) {

        TabRow(
            selectedTabIndex = fase.ordinal,
            containerColor = Fundo,
            contentColor = Destaque,
        ) {
            Fase.entries.forEach { f ->
                Tab(
                    selected = fase == f,
                    onClick = { fase = f },
                    text = { Text(f.rotulo, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    desenhoTatico(slots, fase),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Texto,
                )
                Text(fase.descricao,
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
            TextButton(onClick = { mostrarFantasmas = !mostrarFantasmas }) {
                Text(
                    if (mostrarFantasmas) "Ocultar movimento" else "Ver movimento",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Campo(
            slots = slots,
            fase = fase,
            elenco = elenco,
            mostrarFantasmas = mostrarFantasmas,
            selecionadoId = selecionado?.id,
            onSelecionar = { selecionado = it },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { slots.forEach { it.copiarFase(Fase.SEM_POSSE, fase) } },
                label = { Text("Copiar da defesa") },
            )
            AssistChip(
                onClick = { slots.forEach { it.copiarFase(Fase.COM_POSSE, Fase.TRANSICAO) } },
                label = { Text("Transição = ataque") },
            )
            Formacoes.comFases.forEach { p ->
                AssistChip(onClick = { aplicar(p, slots) }, label = { Text(p.nome) })
            }
        }
    }

    selecionado?.let { slot ->
        ModalBottomSheet(
            onDismissRequest = { selecionado = null },
            containerColor = Gramado,
        ) {
            PainelJogador(slot, fase, slot.jogadorId?.let { elenco[it] })
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Aplica uma pré-definida sem trocar os jogadores já escalados. */
private fun aplicar(p: Predefinida, slots: List<Slot>) {
    val modelo = p.criarSlots()
    slots.forEachIndexed { i, slot ->
        val m = modelo.getOrNull(i) ?: return@forEachIndexed
        Fase.entries.forEach { f ->
            val origem = m.em(f)
            slot.em(f).apply { x = origem.x; y = origem.y; papel = origem.papel }
        }
    }
}

// ------------------------------------------------------------- CAMPO

@Composable
private fun Campo(
    slots: List<Slot>,
    fase: Fase,
    elenco: Map<Int, Jogador>,
    mostrarFantasmas: Boolean,
    selecionadoId: Int?,
    onSelecionar: (Slot) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tamanho by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Gramado)
            .onSizeChanged { tamanho = it }
    ) {
        DesenhoDoCampo(Modifier.fillMaxSize())

        if (mostrarFantasmas) RastroDeMovimento(slots, fase, Modifier.fillMaxSize())

        slots.forEach { slot ->
            TokenJogador(
                slot = slot,
                fase = fase,
                jogador = slot.jogadorId?.let { elenco[it] },
                selecionado = slot.id == selecionadoId,
                tamanhoCampo = tamanho,
                onSelecionar = { onSelecionar(slot) },
            )
        }
    }
}

/** Canvas puro: não lê estado de jogador, nunca redesenha no arrasto. */
@Composable
private fun DesenhoDoCampo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = size.width
        val a = size.height
        val traco = Stroke(width = 1.5.dp.toPx())

        repeat(8) { i ->
            if (i % 2 == 0) drawRect(GramadoClaro, Offset(0f, a / 8 * i), Size(l, a / 8))
        }

        val cor = Linhas.copy(alpha = .30f)
        val m = 10.dp.toPx()

        drawRect(cor, Offset(m, m), Size(l - 2 * m, a - 2 * m), style = traco)
        drawLine(cor, Offset(m, a / 2), Offset(l - m, a / 2), strokeWidth = traco.width)
        drawCircle(cor, l * .14f, Offset(l / 2, a / 2), style = traco)
        drawCircle(cor, 2.5.dp.toPx(), Offset(l / 2, a / 2))

        val largGA = l * .58f; val altGA = a * .16f
        drawRect(cor, Offset((l - largGA) / 2, a - m - altGA), Size(largGA, altGA), style = traco)
        drawRect(cor, Offset((l - largGA) / 2, m), Size(largGA, altGA), style = traco)

        val largPA = l * .28f; val altPA = a * .06f
        drawRect(cor, Offset((l - largPA) / 2, a - m - altPA), Size(largPA, altPA), style = traco)
        drawRect(cor, Offset((l - largPA) / 2, m), Size(largPA, altPA), style = traco)
    }
}

/** Fantasma das outras fases com a linha do deslocamento. */
@Composable
private fun RastroDeMovimento(
    slots: List<Slot>,
    fase: Fase,
    modifier: Modifier = Modifier,
) {
    val outras = Fase.entries.filter { it != fase }

    Canvas(modifier) {
        val l = size.width
        val a = size.height
        val tracejado = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))

        slots.forEach { slot ->
            val atual = slot.em(fase)
            val pAtual = Offset(atual.x * l, (1f - atual.y) * a)

            outras.forEach { outraFase ->
                val outra = slot.em(outraFase)
                if (outra.x == atual.x && outra.y == atual.y) return@forEach

                val pOutra = Offset(outra.x * l, (1f - outra.y) * a)
                val alpha = if (outraFase == Fase.COM_POSSE) .40f else .18f

                drawLine(
                    color = Destaque.copy(alpha = alpha),
                    start = pAtual, end = pOutra,
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = tracejado,
                )
                drawCircle(
                    color = TokenLinha.copy(alpha = alpha * .7f),
                    radius = 8.dp.toPx(),
                    center = pOutra,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun TokenJogador(
    slot: Slot,
    fase: Fase,
    jogador: Jogador?,
    selecionado: Boolean,
    tamanhoCampo: IntSize,
    onSelecionar: () -> Unit,
) {
    val pos = slot.em(fase)

    val corFundo = when {
        selecionado -> Destaque
        jogador == null -> TokenLinha
        else -> {
            val f = jogador.familiaridade(pos.papel).fator
            when {
                f >= .94f -> TokenLinha
                f >= .85f -> TokenAviso
                else -> Erro
            }
        }
    }

    val escala by animateFloatAsState(if (selecionado) 1.12f else 1f, label = "escala")

    Box(
        Modifier
            .offset {
                val meio = TAMANHO_TOKEN.toPx() / 2
                IntOffset(
                    (pos.x * tamanhoCampo.width - meio).roundToInt(),
                    ((1f - pos.y) * tamanhoCampo.height - meio).roundToInt(),
                )
            }
            .size(TAMANHO_TOKEN * escala)
            .pointerInput(tamanhoCampo, fase) {
                detectDragGestures(
                    onDragStart = { onSelecionar() },
                    onDrag = { evento, delta ->
                        evento.consume()
                        if (tamanhoCampo.width == 0) return@detectDragGestures
                        slot.mover(
                            fase,
                            pos.x + delta.x / tamanhoCampo.width,
                            pos.y - delta.y / tamanhoCampo.height,
                        )
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures { onSelecionar() } }
            .clip(CircleShape)
            .background(corFundo),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                pos.papel.sigla,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Gramado,
            )
            jogador?.let {
                Text(
                    "${it.geralEm(pos.papel)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Gramado.copy(alpha = .75f),
                )
            }
        }
    }
}

// ------------------------------------------------- PAINEL DO JOGADOR

@Composable
private fun PainelJogador(slot: Slot, fase: Fase, jogador: Jogador?) {
    val pos = slot.em(fase)
    var aba by remember { mutableIntStateOf(0) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(jogador?.nome ?: slot.nome,
            style = MaterialTheme.typography.titleLarge, color = Texto)

        jogador?.let { j ->
            val fam = j.familiaridade(pos.papel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pos.papel.sigla} · ${fam.rotulo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        fam.fator >= .94f -> Destaque
                        fam.fator >= .85f -> TokenAviso
                        else -> Erro
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text("overall ${j.geral} → ${j.geralEm(pos.papel)}",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
        }

        Spacer(Modifier.height(16.dp))
        TabRow(aba, containerColor = Gramado, contentColor = Destaque) {
            listOf("Instruções", "Estilo").forEachIndexed { i, t ->
                Tab(aba == i, { aba = i }, text = { Text(t) })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (aba == 0) InstrucoesIndividuais(slot, pos)
        else EstiloDoJogador(slot, pos, jogador)
    }
}

@Composable
private fun InstrucoesIndividuais(slot: Slot, pos: PosicaoFase) {
    val i = slot.instrucoes

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Travar função", style = MaterialTheme.typography.bodyMedium, color = Texto)
            Text("Impede que a posição mude ao arrastar nesta fase",
                style = MaterialTheme.typography.bodySmall, color = TextoFraco)
        }
        Switch(pos.papelTravado, { pos.papelTravado = it })
    }

    Spacer(Modifier.height(16.dp))

    Seletor("Movimentação ofensiva", Movimentacao.entries, i.movimentacao, {
        when (it) {
            Movimentacao.FICA_NA_POSICAO -> "Fica na posição"
            Movimentacao.EQUILIBRADO -> "Equilibrado"
            Movimentacao.ATACA_ESPACO -> "Ataca o espaço"
            Movimentacao.CORTA_PRA_DENTRO -> "Corta pra dentro"
        }
    }) { slot.instrucoes = i.copy(movimentacao = it) }

    Seletor("Apoio defensivo", ApoioDefensivo.entries, i.apoio, {
        when (it) {
            ApoioDefensivo.NAO_RECUA -> "Não recua"
            ApoioDefensivo.EQUILIBRADO -> "Equilibrado"
            ApoioDefensivo.RECUA_SEMPRE -> "Recua sempre"
        }
    }) { slot.instrucoes = i.copy(apoio = it) }

    Seletor("Marcação", Marcacao.entries, i.marcacao, {
        if (it == Marcacao.POR_ZONA) "Por zona" else "Por homem"
    }) { slot.instrucoes = i.copy(marcacao = it) }

    Deslizante("Intensidade de pressão", i.pressao) {
        slot.instrucoes = i.copy(pressao = it)
    }
    Deslizante("Amplitude", i.amplitude) {
        slot.instrucoes = i.copy(amplitude = it)
    }
}

@Composable
private fun EstiloDoJogador(slot: Slot, pos: PosicaoFase, jogador: Jogador?) {
    if (jogador == null) {
        Text("Escale um jogador neste slot para definir o estilo.",
            style = MaterialTheme.typography.bodySmall, color = TextoFraco)
        return
    }

    val doPapel = EstiloJogador.entries.filter { pos.papel in it.papeisNaturais }
    val disponiveis = doPapel.filter { it.disponivelPara(jogador) }
    val bloqueados = doPapel - disponiveis.toSet()

    Column {
        disponiveis.forEach { estilo ->
            val ativo = slot.estilo == estilo
            Surface(
                onClick = { slot.estilo = if (ativo) null else estilo },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (ativo) Destaque.copy(alpha = .16f) else GramadoClaro,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(estilo.rotulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ativo) Destaque else Texto)
                    Text(estilo.descricao,
                        style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                }
            }
        }

        if (bloqueados.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("BLOQUEADOS — falta atributo",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            bloqueados.forEach { estilo ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(estilo.rotulo,
                        style = MaterialTheme.typography.bodyMedium, color = TextoFraco)
                    Text(
                        estilo.faltaPara(jogador)
                            .joinToString(" · ") { (attr, dif) -> "$attr +$dif" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Erro.copy(alpha = .85f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------- AUXILIAR

@Composable
private fun <T> Seletor(
    titulo: String,
    opcoes: List<T>,
    atual: T,
    rotulo: (T) -> String,
    aoEscolher: (T) -> Unit,
) {
    Text(titulo, style = MaterialTheme.typography.labelMedium, color = TextoFraco)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opcoes.forEach { opcao ->
            FilterChip(opcao == atual, { aoEscolher(opcao) }, { Text(rotulo(opcao)) })
        }
    }
}

@Composable
private fun Deslizante(titulo: String, valor: Int, aoMudar: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(titulo, style = MaterialTheme.typography.labelMedium,
            color = TextoFraco, modifier = Modifier.weight(1f))
        Text("$valor", style = MaterialTheme.typography.labelMedium, color = Destaque)
    }
    Slider(valor.toFloat(), { aoMudar(it.roundToInt()) },
        valueRange = 0f..100f, modifier = Modifier.padding(bottom = 8.dp))
}
