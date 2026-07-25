package com.exemplo.fmanager.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.EstadoJogo
import com.exemplo.fmanager.JogoViewModel
import com.exemplo.fmanager.formacao.Papel
import com.exemplo.fmanager.sistemas.*

/*
 * ANÁLISE — desenvolvimento, garimpo e semelhança.
 *
 * Três abas que respondem três perguntas de dirigente:
 *   "meu elenco está evoluindo?"        (moneyball-mentality)
 *   "onde está o melhor negócio?"        (moneyball-mentality)
 *   "quem joga como o cara que saiu?"    (FIFA-Player-Recomendation)
 */

@Composable
fun TelaAnalise(e: EstadoJogo, vm: JogoViewModel) {
    var aba by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(aba, containerColor = Fundo, contentColor = Destaque) {
            listOf("Evolução", "Garimpo", "Parecidos", "Motor").forEachIndexed { i, t ->
                Tab(aba == i, { aba = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        when (aba) {
            0 -> AbaEvolucao(e, vm)
            1 -> AbaGarimpo(e, vm)
            2 -> AbaParecidos(e, vm)
            else -> AbaCalibracao(e, vm)
        }
    }
}

// ----------------------------------------------------------- EVOLUÇÃO

@Composable
private fun AbaEvolucao(e: EstadoJogo, vm: JogoViewModel) {
    LaunchedEffect(e.carreira?.temporada) { vm.analisarDesenvolvimento() }
    var aberto by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                color = SuperficieAlta) {
                Text(e.resumoDesenvolvimento.ifBlank {
                    "Sem histórico para comparar ainda."
                }, Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall, color = TextoMedio)
            }
            Secao("Jogador por jogador")
        }

        items(e.desenvolvimento, key = { it.jogadorId }) { d ->
            val expandido = aberto == d.jogadorId
            Surface(
                Modifier.fillMaxWidth()
                    .clickable { aberto = if (expandido) null else d.jogadorId },
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Column(Modifier.padding(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.nome, maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Texto)
                            Text("${d.idadeAntes} → ${d.idadeAgora} anos",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco)
                        }
                        val cor = when (d.tendencia) {
                            Tendencia.EXPLODINDO, Tendencia.CRESCENDO -> Destaque
                            Tendencia.ESTAVEL -> TextoMedio
                            else -> Erro
                        }
                        Selo("${d.tendencia.seta} ${d.tendencia.rotulo}", cor)
                        Spacer(Modifier.width(10.dp))
                        Text("${d.geralAntes}→${d.geralAgora}",
                            style = EstiloNumeroPequeno, color = cor, fontSize = 14.sp)
                    }

                    if (expandido) {
                        if (d.maioresGanhos.isNotEmpty()) {
                            Secao("Onde cresceu")
                            d.maioresGanhos.forEach {
                                BarraVariacao(it.nome, it.antes, it.agora)
                            }
                        }
                        if (d.maioresPerdas.isNotEmpty()) {
                            Secao("Onde caiu")
                            d.maioresPerdas.forEach {
                                BarraVariacao(it.nome, it.antes, it.agora)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ------------------------------------------------------------ GARIMPO

@Composable
private fun AbaGarimpo(e: EstadoJogo, vm: JogoViewModel) {
    var papel by remember { mutableStateOf(Papel.ATA) }
    LaunchedEffect(papel) { vm.garimpar(papel) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                "A pergunta não é quem é o melhor, é quem entrega mais por " +
                        "euro. Orçamento: ${formatarEuro(e.caixa)}.",
                style = MaterialTheme.typography.bodySmall, color = TextoMedio,
            )
            Secao("Posição")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Papel.entries.forEach { p ->
                    FilterChip(papel == p, { papel = p },
                        { Text(p.sigla, fontSize = 11.sp) })
                }
            }
            Secao("Melhores negócios")
        }

        if (e.garimpo.isEmpty()) {
            item {
                Text("Nada dentro do orçamento para essa posição.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
        }

        items(e.garimpo, key = { it.jogador.id }) { a ->
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = SuperficieAlta) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CartaJogador(a.jogador, 44.dp, papel)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.jogador.nome, maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Texto)
                            Text("${a.jogador.idade}a · ${a.jogador.clube}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${a.eficiencia}", style = EstiloNumeroPequeno,
                                color = corPorValor(a.eficiencia))
                            Text("custo-benefício",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco, fontSize = 8.sp)
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row {
                        Text("${a.notaNoPapel} de ${papel.sigla}  ·  " +
                                formatarEuro(a.jogador.valorEur),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoMedio)
                    }
                    Text(a.motivo,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (a.eficiencia >= 70) Destaque else TextoFraco)
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ---------------------------------------------------------- PARECIDOS

@Composable
private fun AbaParecidos(e: EstadoJogo, vm: JogoViewModel) {
    val referencia = e.referenciaSemelhanca

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                "Escolha alguém do seu elenco e o app procura quem joga " +
                        "como ele. A comparação é de PERFIL, não de nível — " +
                        "por isso aparece gente mais barata jogando parecido.",
                style = MaterialTheme.typography.bodySmall, color = TextoMedio,
            )
            Secao("Referência")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                e.elenco.take(24).forEach { j ->
                    Column(
                        Modifier.width(58.dp)
                            .clickable { vm.buscarParecidos(j) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CartaJogador(j, 46.dp, mostrarNome = true)
                    }
                }
            }
        }

        referencia?.let { r ->
            item {
                Secao("Perfil de ${r.nome}")
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    color = SuperficieAlta) {
                    RadarDoJogador(r, Modifier.padding(vertical = 8.dp))
                }
                Secao("Quem joga como ele")
            }

            if (e.parecidos.isEmpty()) {
                item {
                    Text("Ninguém com perfil parecido dentro do orçamento.",
                        style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                }
            }

            items(e.parecidos, key = { it.jogador.id }) { p ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = SuperficieAlta) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CartaJogador(p.jogador, 44.dp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.jogador.nome, maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Texto)
                            Text(
                                "${p.jogador.idade}a · ${p.jogador.clube}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco, maxLines = 1,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (p.economia > 0)
                                    "economiza ${formatarEuro(p.economia)}"
                                else "custa ${formatarEuro(-p.economia)} mais",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.economia > 0) Destaque else Alerta,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(
                                        corPorValor(p.semelhanca).copy(alpha = .18f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text("${p.semelhanca}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = corPorValor(p.semelhanca))
                            }
                            Text(
                                if (p.diferencaGeral >= 0) "+${p.diferencaGeral} ger"
                                else "${p.diferencaGeral} ger",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco, fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

// -------------------------------------------------------- CALIBRAÇÃO

@Composable
private fun AbaCalibracao(e: EstadoJogo, vm: JogoViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            "Roda 40 partidas em segundos e compara as médias com os " +
                    "números do futebol real. Serve para verificar se o motor " +
                    "está calibrado sem você jogar uma temporada inteira.",
            style = MaterialTheme.typography.bodySmall, color = TextoMedio,
        )
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { vm.rodarCalibracao() },
            enabled = !e.calibrando,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (e.calibrando) "Simulando..." else "Rodar 40 partidas")
        }

        Spacer(Modifier.height(16.dp))

        if (e.calibracao.isNotBlank()) {
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                color = SuperficieAlta,
            ) {
                Text(
                    e.calibracao,
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Texto,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
