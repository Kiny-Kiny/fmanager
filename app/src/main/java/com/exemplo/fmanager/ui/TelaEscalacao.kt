package com.exemplo.fmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.exemplo.fmanager.dados.*
import com.exemplo.fmanager.formacao.*
import kotlin.math.roundToInt

/*
 * ESCALAÇÃO COM AS CARTAS.
 *
 * O campo mostra os 11 com a imagem da carta que veio da API. Tocar num
 * slot abre a lista do resto do elenco, ordenada pelo rendimento naquela
 * função específica — então o app já sugere quem serve ali.
 *
 * O selecionador de formação em cima troca a estrutura sem perder os
 * jogadores já escalados.
 */

private val TAMANHO_CARTA = 52.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaEscalacao(
    slots: List<Slot>,
    elenco: List<Jogador>,
    modifier: Modifier = Modifier,
) {
    var slotAberto by remember { mutableStateOf<Slot?>(null) }
    var formacaoAtiva by remember { mutableIntStateOf(-1) }
    var recomposicao by remember { mutableIntStateOf(0) }

    val porId = remember(elenco) { elenco.associateBy { it.id } }
    val escalados = remember(recomposicao) { slots.mapNotNull { it.jogadorId }.toSet() }

    Column(modifier.fillMaxSize().background(Fundo)) {

        // ------------------------------------------ FORMAÇÕES
        Text("FORMAÇÃO", Modifier.padding(start = 16.dp, top = 14.dp),
            style = MaterialTheme.typography.labelSmall, color = TextoFraco)

        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Formacoes.todas.forEachIndexed { idx, p ->
                FilterChip(
                    selected = formacaoAtiva == idx,
                    onClick = {
                        formacaoAtiva = idx
                        aplicarFormacao(p, slots)
                        recomposicao++
                    },
                    label = { Text(p.nome, fontSize = 12.sp) },
                )
            }
        }

        @Suppress("UNUSED_EXPRESSION") recomposicao
        Text(
            desenhoTatico(slots, Fase.SEM_POSSE),
            Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = Texto,
        )
        Text("Arraste para mover · toque para substituir",
            Modifier.padding(start = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = TextoFraco)

        // ---------------------------------------------- CAMPO
        CampoComCartas(
            slots = slots,
            porId = porId,
            onTocarSlot = { slotAberto = it },
            onMover = { recomposicao++ },
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // ------------------------------------------- RESERVAS
        val reservas = elenco.filter { it.id !in escalados }
        Text("RESERVAS (${reservas.size})",
            Modifier.padding(start = 16.dp, top = 10.dp),
            style = MaterialTheme.typography.labelSmall, color = TextoFraco)

        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reservas.take(20).forEach { j ->
                Column(
                    Modifier.width(58.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CartaJogador(j, 46.dp)
                    Text(j.nome.split(" ").last(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextoFraco, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // ------------------------------------------ TROCA DE JOGADOR
    slotAberto?.let { slot ->
        val papel = slot.papelPrincipal
        ModalBottomSheet(
            onDismissRequest = { slotAberto = null },
            containerColor = Superficie,
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Quem joga de ${papel.sigla}?",
                    style = MaterialTheme.typography.titleMedium, color = Texto)
                Text("Ordenado pelo rendimento nesta função",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val candidatos = elenco
                    .sortedByDescending { it.rendimentoEm(papel) }
                items(candidatos, key = { it.id }) { j ->
                    val jaEscalado = j.id in escalados && j.id != slot.jogadorId
                    OpcaoJogador(
                        jogador = j,
                        papel = papel,
                        atual = j.id == slot.jogadorId,
                        jaEscalado = jaEscalado,
                    ) {
                        // Se o escolhido já está no time, troca de lugar
                        // com o ocupante deste slot.
                        val outro = slots.firstOrNull { it.jogadorId == j.id }
                        if (outro != null && outro.id != slot.id) {
                            val antigo = slot.jogadorId
                            val nomeAntigo = slot.nome
                            outro.jogadorId = antigo
                            outro.nome = nomeAntigo
                        }
                        slot.jogadorId = j.id
                        slot.nome = j.nome
                        recomposicao++
                        slotAberto = null
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun aplicarFormacao(p: Predefinida, slots: List<Slot>) {
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
private fun CampoComCartas(
    slots: List<Slot>,
    porId: Map<Int, Jogador>,
    onTocarSlot: (Slot) -> Unit,
    onMover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tamanho by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF0D1B1E))
            .onSizeChanged { tamanho = it }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val l = size.width; val a = size.height
            val traco = Stroke(width = 1.5.dp.toPx())
            val linha = Color(0xFF4FD1C5).copy(alpha = .22f)
            val m = 8.dp.toPx()

            repeat(8) { k ->
                if (k % 2 == 0) drawRect(
                    Color(0xFF122326), Offset(0f, a / 8 * k), Size(l, a / 8))
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
        }

        slots.forEach { slot ->
            val pos = slot.em(Fase.SEM_POSSE)
            val jogador = slot.jogadorId?.let { porId[it] }

            Column(
                Modifier
                    .offset {
                        val meio = TAMANHO_CARTA.toPx() / 2
                        IntOffset(
                            (pos.x * tamanho.width - meio).roundToInt(),
                            ((1f - pos.y) * tamanho.height - meio).roundToInt(),
                        )
                    }
                    .width(TAMANHO_CARTA)
                    // ARRASTAR move o jogador no campo.
                    .pointerInput(tamanho) {
                        detectDragGestures(
                            onDrag = { evento, delta ->
                                evento.consume()
                                if (tamanho.width == 0) return@detectDragGestures
                                slot.mover(
                                    Fase.SEM_POSSE,
                                    pos.x + delta.x / tamanho.width,
                                    pos.y - delta.y / tamanho.height,
                                )
                                onMover()
                            },
                        )
                    }
                    // TOCAR abre a substituição. Gestos separados: segurar
                    // e arrastar não dispara mais a folha.
                    .pointerInput(slot.id) {
                        detectTapGestures { onTocarSlot(slot) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (jogador != null) {
                    CartaJogador(jogador, TAMANHO_CARTA, papel = pos.papel)
                    Text(jogador.nome.split(" ").last(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Texto, maxLines = 1, fontSize = 9.sp)
                } else {
                    Box(
                        Modifier.size(TAMANHO_CARTA).clip(CircleShape)
                            .background(TextoFraco.copy(alpha = .25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(pos.papel.sigla,
                            style = MaterialTheme.typography.labelSmall,
                            color = Texto)
                    }
                }
            }
        }
    }
}

/** A carta da API, com moldura pela familiaridade na função. */
@Composable
private fun CartaJogador(
    jogador: Jogador,
    tamanho: Dp,
    papel: Papel? = null,
) {
    val cor = papel?.let {
        when {
            jogador.familiaridade(it).fator >= .94f -> Destaque
            jogador.familiaridade(it).fator >= .85f -> Alerta
            else -> Erro
        }
    } ?: TextoFraco

    Box(contentAlignment = Alignment.BottomEnd) {
        if (jogador.urlFoto != null) {
            AsyncImage(
                model = jogador.urlFoto,
                contentDescription = jogador.nome,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(tamanho).clip(CircleShape)
                    .background(SuperficieAlta),
            )
        } else {
            Box(
                Modifier.size(tamanho).clip(CircleShape).background(SuperficieAlta),
                contentAlignment = Alignment.Center,
            ) {
                Text(jogador.nome.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium, color = Texto)
            }
        }

        Surface(shape = CircleShape, color = cor) {
            Text(
                "${papel?.let { jogador.geralEm(it) } ?: jogador.geral}",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Superficie, fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun OpcaoJogador(
    jogador: Jogador,
    papel: Papel,
    atual: Boolean,
    jaEscalado: Boolean,
    onEscolher: () -> Unit,
) {
    val rend = jogador.rendimentoEm(papel)
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onEscolher),
        shape = MaterialTheme.shapes.medium,
        color = if (atual) Destaque.copy(alpha = .14f) else SuperficieAlta,
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CartaJogador(jogador, 42.dp, papel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(jogador.nome, color = Texto,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    "${jogador.posicao} · ${jogador.idade}a · " +
                            jogador.familiaridade(papel).rotulo +
                            if (jaEscalado) " · já escalado" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (jaEscalado) Alerta else TextoFraco,
                )
            }
            Text("$rend%", fontWeight = FontWeight.Bold,
                color = when {
                    rend >= 75 -> Destaque
                    rend >= 60 -> Alerta
                    else -> Erro
                })
        }
    }
}
