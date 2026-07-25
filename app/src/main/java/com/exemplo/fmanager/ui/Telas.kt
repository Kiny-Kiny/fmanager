package com.exemplo.fmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exemplo.fmanager.EstadoJogo
import com.exemplo.fmanager.JogoViewModel
import com.exemplo.fmanager.dados.*
import com.exemplo.fmanager.formacao.*
import com.exemplo.fmanager.motor.Importancia
import com.exemplo.fmanager.motor.Lance
import com.exemplo.fmanager.sistemas.*
import kotlinx.coroutines.launch

// ------------------------------------------------------------- INÍCIO

// ------------------------------------------------------------- ELENCO

@Composable
fun TelaElenco(e: EstadoJogo) {
    var papelFiltro by remember { mutableStateOf<Papel?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(papelFiltro == null, { papelFiltro = null }, { Text("Todos") })
            Papel.entries.forEach { p ->
                FilterChip(papelFiltro == p, { papelFiltro = p }, { Text(p.sigla) })
            }
        }

        LazyColumn(
            Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val lista = papelFiltro?.let { p ->
                e.elenco.sortedByDescending { it.rendimentoEm(p) }
            } ?: e.elenco

            items(lista, key = { it.id }) { j ->
                LinhaJogador(j, papelFiltro, e.contratos[j.id])
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LinhaJogador(j: Jogador, papel: Papel?, contrato: Contrato?) {
    var aberto by remember { mutableStateOf(false) }

    Surface(
        Modifier.fillMaxWidth().clickable { aberto = !aberto },
        shape = MaterialTheme.shapes.medium, color = SuperficieAlta,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CartaJogador(j, 46.dp, papel)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(j.nome, color = Texto,
                        style = MaterialTheme.typography.bodyLarge)
                    Text("${j.posicao} · ${j.idade} anos · pot ${j.potencial}",
                        style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                }
                papel?.let {
                    val ad = j.rendimentoEm(it)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$ad%", fontWeight = FontWeight.Bold,
                            color = if (ad >= 75) Destaque
                            else if (ad >= 60) Alerta else Erro)
                        Text(j.familiaridade(it).rotulo,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                    }
                }
            }

            if (aberto) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = TextoFraco.copy(alpha = .2f))
                Spacer(Modifier.height(12.dp))

                BarraAtributo("Velocidade", j.velocidade)
                BarraAtributo("Aceleração", j.aceleracao)
                BarraAtributo("Finalização", j.finalizacao)
                BarraAtributo("Passe baixo", j.passeBaixo)
                BarraAtributo("Passe alto", j.passeAlto)
                BarraAtributo("Visão", j.visao)
                BarraAtributo("Drible", j.drible)
                BarraAtributo("Controle de bola", j.controleBola)
                BarraAtributo("Roubo de bola", j.rouboBola)
                BarraAtributo("Consciência def.", j.consciencaDef)
                BarraAtributo("Contato físico", j.contatoFisico)
                BarraAtributo("Resistência", j.resistencia)

                val tracos = j.tracos()
                if (tracos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("PLAYSTYLES", style = MaterialTheme.typography.labelSmall,
                        color = TextoFraco)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tracos.forEach { t ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (t.elite) Destaque.copy(alpha = .22f)
                                        else TextoFraco.copy(alpha = .15f),
                            ) {
                                Text(t.toString(),
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (t.elite) Destaque else Texto)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Melhor posição: ${j.melhorPapel().sigla}",
                    style = MaterialTheme.typography.bodySmall, color = Destaque)
                contrato?.let {
                    Text("Salário ${formatarEuro(it.salarioSemanalEur)}/sem · " +
                            "contrato até T${it.terminaEmTemporada}",
                        style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                }
            }
        }
    }
}


// ------------------------------------------------------------ TÁTICAS

@Composable
fun TelaTaticas(e: EstadoJogo, vm: JogoViewModel) {
    var t by remember { mutableStateOf(e.tatica) }
    var abaEditor by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = if (abaEditor) 0 else 1,
            containerColor = Fundo, contentColor = Destaque,
        ) {
            Tab(abaEditor, { abaEditor = true }) {
                Text("Formação", Modifier.padding(14.dp))
            }
            Tab(!abaEditor, { abaEditor = false }) {
                Text("Estilo de jogo", Modifier.padding(14.dp))
            }
        }

        if (abaEditor) {
            EditorFormacaoScreen(
                slots = vm.slots,
                elenco = e.elenco.associateBy { it.id },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Estilos.todos.forEach { (nome, preset) ->
                            FilterChip(t == preset, {
                                t = preset; vm.definirTatica(preset)
                            }, { Text(nome) })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    Ajuste("Velocidade de construção", t.velocidadeConstrucao,
                        "Toque paciente", "Vertical direto") {
                        t = t.copy(velocidadeConstrucao = it); vm.definirTatica(t)
                    }
                    Ajuste("Altura da linha", t.alturaLinha,
                        "Recuada", "Adiantada") {
                        t = t.copy(alturaLinha = it); vm.definirTatica(t)
                    }
                    Ajuste("Intensidade de pressão", t.intensidadePressao,
                        "Espera", "Pressiona na saída") {
                        t = t.copy(intensidadePressao = it); vm.definirTatica(t)
                    }
                    Ajuste("Compactação", t.compactacao,
                        "Espalha pelos lados", "Concentra no meio") {
                        t = t.copy(compactacao = it); vm.definirTatica(t)
                    }
                    Ajuste("Contra-ataque", t.contraAtaque,
                        "Jogo apoiado", "Sai em velocidade") {
                        t = t.copy(contraAtaque = it); vm.definirTatica(t)
                    }
                    Ajuste("Liberdade criativa", t.liberdadeCriativa,
                        "Simplifica", "Incentiva o drible") {
                        t = t.copy(liberdadeCriativa = it); vm.definirTatica(t)
                    }
                    Ajuste("Risco no passe", t.riscoNoPasse,
                        "Seguro", "Ousado") {
                        t = t.copy(riscoNoPasse = it); vm.definirTatica(t)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Ajuste(
    titulo: String, valor: Int, esquerda: String, direita: String,
    aoMudar: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium,
                color = Texto, modifier = Modifier.weight(1f))
            Text("$valor", color = Destaque,
                style = MaterialTheme.typography.bodyMedium)
        }
        Slider(valor.toFloat(), { aoMudar(it.toInt()) }, valueRange = 0f..100f)
        Row(Modifier.fillMaxWidth()) {
            Text(esquerda, style = MaterialTheme.typography.labelSmall,
                color = TextoFraco, modifier = Modifier.weight(1f))
            Text(direita, style = MaterialTheme.typography.labelSmall, color = TextoFraco)
        }
    }
}

// ------------------------------------------------------------ MERCADO

@Composable
fun TelaMercado(e: EstadoJogo, vm: JogoViewModel) {
    var geralMin by remember { mutableIntStateOf(70) }
    var idadeMax by remember { mutableIntStateOf(30) }
    var resultados by remember { mutableStateOf<List<Jogador>>(emptyList()) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        resultados = vm.buscarMercado(null, idadeMax, e.caixa, geralMin)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Caixa disponível: ${formatarEuro(e.caixa)}",
            style = MaterialTheme.typography.bodyMedium, color = Destaque)

        Ajuste("Overall mínimo", geralMin, "Qualquer", "Craque") { geralMin = it }
        Ajuste("Idade máxima", idadeMax, "Jovem", "Veterano") {
            idadeMax = it.coerceIn(16, 40)
        }

        Button(
            onClick = {
                escopo.launch {
                    resultados = vm.buscarMercado(null, idadeMax, e.caixa, geralMin)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Buscar jogadores") }

        aviso?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Alerta, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(resultados, key = { it.id }) { j ->
                val preco = vm.precoDe(j)
                val salario = vm.salarioDe(j)
                Surface(
                    Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    color = SuperficieAlta,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(j.nome, color = Texto)
                                Text("${j.posicao} · ${j.idade}a · ${j.clube}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextoFraco)
                            }
                            Text("${j.geral}", fontWeight = FontWeight.Bold,
                                color = Destaque)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Pedem ${formatarEuro(preco)} · " +
                                "salário ${formatarEuro(salario)}/sem",
                            style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                escopo.launch {
                                    val r = vm.fazerProposta(j, preco, salario)
                                    aviso = "${j.nome}: ${r.motivo}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Fazer proposta") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ------------------------------------------------------------- TREINO

@Composable
fun TelaTreino(e: EstadoJogo, vm: JogoViewModel) {
    var foco by remember { mutableStateOf(FocoTreino.RITMO) }
    var intensidade by remember { mutableStateOf(Intensidade.NORMAL) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Centro de treinamento",
            style = MaterialTheme.typography.headlineSmall, color = Texto)
        Spacer(Modifier.height(20.dp))

        Text("FOCO", style = MaterialTheme.typography.labelSmall, color = TextoFraco)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocoTreino.entries.forEach { f ->
                FilterChip(foco == f, { foco = f }, { Text(f.rotulo) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("INTENSIDADE", style = MaterialTheme.typography.labelSmall, color = TextoFraco)
        Row(Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Intensidade.entries.forEach { i ->
                FilterChip(intensidade == i, { intensidade = i }, { Text(i.rotulo) })
            }
        }

        Text("Intensidade pesada acelera a evolução, mas aumenta o risco de lesão.",
            style = MaterialTheme.typography.bodySmall, color = TextoFraco)

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.treinarElenco(foco, intensidade) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Treinar uma semana") }

        Spacer(Modifier.height(24.dp))
        Text("Jovens com margem de evolução",
            style = MaterialTheme.typography.titleSmall, color = Texto)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val promessas = e.elenco
                .filter { it.potencial - it.geral >= 4 }
                .sortedByDescending { it.potencial - it.geral }
            items(promessas, key = { it.id }) { j ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(j.nome, color = Texto,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Recomendado: ${
                            Treino.focoRecomendado(j, j.melhorPapel()).rotulo
                        }", style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                    }
                    Text("${j.geral} → ${j.potencial}", color = Destaque,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ------------------------------------------------------------- TABELA

@Composable
fun TelaTabela(e: EstadoJogo) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("#", Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco)
                Text("CLUBE", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco)
                listOf("J", "V", "SG", "P").forEach {
                    Text(it, Modifier.width(30.dp),
                        style = MaterialTheme.typography.labelSmall, color = TextoFraco)
                }
            }
        }
        itemsIndexed(e.tabela) { i, linha ->
            val meu = linha.clubeId == e.clube?.id
            Row(
                Modifier.fillMaxWidth()
                    .background(if (meu) Destaque.copy(alpha = .12f) else Fundo)
                    .padding(vertical = 8.dp),
            ) {
                Text("${i + 1}", Modifier.width(28.dp),
                    color = if (meu) Destaque else TextoFraco,
                    style = MaterialTheme.typography.bodySmall)
                Text(linha.nome, Modifier.weight(1f),
                    color = if (meu) Destaque else Texto,
                    style = MaterialTheme.typography.bodySmall)
                listOf(
                    linha.jogos.toString(), linha.vitorias.toString(),
                    linha.saldo.toString(), linha.pontos.toString(),
                ).forEach {
                    Text(it, Modifier.width(30.dp), color = Texto,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ------------------------------------------------------------ PARTIDA

@Composable
fun TelaPartida(e: EstadoJogo) {
    val r = e.ultimoResultado

    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Destaque)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("${r.golsMandante}  -  ${r.golsVisitante}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold, color = Texto)
            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.height(16.dp))
            val a = r.statsMandante
            val b = r.statsVisitante
            listOf(
                "Posse" to ("${a.posse}%" to "${b.posse}%"),
                "Finalizações" to ("${a.chutes}" to "${b.chutes}"),
                "No gol" to ("${a.chutesNoGol}" to "${b.chutesNoGol}"),
                "Precisão de passe" to
                        ("${a.precisaoPasse}%" to "${b.precisaoPasse}%"),
                "Faltas" to ("${a.faltas}" to "${b.faltas}"),
                "Cartões" to
                        ("${a.amarelos + a.vermelhos}" to "${b.amarelos + b.vermelhos}"),
                "Impedimentos" to ("${a.impedimentos}" to "${b.impedimentos}"),
                "Desarmes" to ("${a.desarmes}" to "${b.desarmes}"),
            ).forEach { (rotulo, valores) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(valores.first, Modifier.width(52.dp),
                        style = MaterialTheme.typography.bodySmall, color = Texto)
                    Text(rotulo, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                    Text(valores.second,
                        style = MaterialTheme.typography.bodySmall, color = Texto)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Só os lances que importam — o resumo do jogo, não a fita toda.
        items(r.lances.filter { it.importancia != Importancia.ROTINA }) { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${l.minuto}'", Modifier.width(40.dp),
                    color = TextoFraco, style = MaterialTheme.typography.bodySmall)
                Text(l.narrar(),
                    color = when (l.importancia) {
                        Importancia.DECISIVO -> Destaque
                        Importancia.DESTAQUE -> Texto
                        else -> TextoFraco
                    },
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ----------------------------------------------------------- AUXILIAR


