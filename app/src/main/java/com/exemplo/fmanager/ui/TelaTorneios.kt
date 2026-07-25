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
import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.sistemas.FormatoTorneio
import com.exemplo.fmanager.sistemas.Inscricoes
import com.exemplo.fmanager.sistemas.Torneio
import com.exemplo.fmanager.sistemas.Torneios

/*
 * TORNEIOS CUSTOMIZADOS.
 *
 * O formato que faltava: fase de grupos seguida de eliminatória. É o
 * desenho da Champions e da Copa do Mundo, e o que praticamente todo
 * organizador de torneio de eFootball monta.
 *
 * O sorteio usa potes por reputação, então não sai um grupo com quatro
 * gigantes e outro com quatro fracos — o problema óbvio de sortear tudo
 * no aleatório puro.
 */

@Composable
fun TelaTorneios(e: EstadoJogo, vm: JogoViewModel) {
    var aba by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.carregarTorneios()
        vm.carregarTitulos()
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(aba, containerColor = Fundo, contentColor = Destaque) {
            listOf("Criar", "Meus torneios", "Palmarés").forEachIndexed { i, t ->
                Tab(aba == i, { aba = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        when (aba) {
            0 -> AbaCriar(e, vm)
            1 -> AbaTorneios(e, vm)
            else -> AbaPalmares(e)
        }
    }
}

// ------------------------------------------------------------- CRIAR

@Composable
private fun AbaCriar(e: EstadoJogo, vm: JogoViewModel) {
    LaunchedEffect(Unit) { vm.carregarClubesParaTorneio() }

    var nome by remember { mutableStateOf("") }
    var formato by remember { mutableStateOf(FormatoTorneio.GRUPOS_E_ELIMINATORIA) }
    var grupos by remember { mutableIntStateOf(4) }
    var passam by remember { mutableIntStateOf(2) }
    val escolhidos = remember { mutableStateListOf<Clube>() }
    var filtroLiga by remember { mutableStateOf<String?>(null) }

    val ligas = remember(e.clubesParaTorneio) {
        e.ligas.filter { l -> e.clubesParaTorneio.any { it.ligaId == l.id } }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            OutlinedTextField(
                value = nome, onValueChange = { nome = it.take(28) },
                label = { Text("Nome do torneio") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Secao("Formato")
            FormatoTorneio.entries.forEach { f ->
                val ativo = formato == f
                Surface(
                    onClick = { formato = f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (ativo) Destaque.copy(alpha = .15f) else SuperficieAlta,
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(f.rotulo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ativo) Destaque else Texto)
                        Text(f.descricao,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                    }
                }
            }

            if (formato == FormatoTorneio.GRUPOS_E_ELIMINATORIA) {
                Secao("Grupos")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Torneios.gruposSugeridos(escolhidos.size.coerceAtLeast(8))
                        .forEach { g ->
                            FilterChip(grupos == g, { grupos = g },
                                { Text("$g grupos", fontSize = 11.sp) })
                        }
                }
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1, 2).forEach { q ->
                        FilterChip(passam == q, { passam = q },
                            { Text("passam $q", fontSize = 11.sp) })
                    }
                }
                if (escolhidos.size >= 4) {
                    Text(
                        "${escolhidos.size} clubes em $grupos grupos = " +
                                "${escolhidos.size / grupos} por grupo" +
                                if (escolhidos.size % grupos != 0)
                                    " (${escolhidos.size % grupos} de fora)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (escolhidos.size % grupos != 0) Alerta
                        else TextoFraco,
                    )
                }
            }

            Secao("Participantes (${escolhidos.size})")

            if (escolhidos.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    escolhidos.forEach { c ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(corDoClube(c.nome).copy(alpha = .22f))
                                .clickable { escolhidos.remove(c) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text("${c.nome.take(14)} ✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = corDoClube(c.nome))
                        }
                    }
                }
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(filtroLiga == null, { filtroLiga = null },
                    { Text("Todas", fontSize = 11.sp) })
                ligas.take(20).forEach { l ->
                    FilterChip(filtroLiga == l.nome, { filtroLiga = l.nome },
                        { Text(l.nome.take(16), fontSize = 11.sp) })
                }
            }
        }

        val disponiveis = e.clubesParaTorneio.filter { c ->
            (filtroLiga == null || e.ligas.firstOrNull { it.id == c.ligaId }
                ?.nome == filtroLiga) && c !in escolhidos
        }

        items(disponiveis, key = { it.id }) { c ->
            Surface(
                onClick = { escolhidos.add(c) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp), color = SuperficieAlta,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(RoundedCornerShape(2.dp))
                            .background(corDoClube(c.nome))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(c.nome, Modifier.weight(1f), maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium, color = Texto)
                    Text("${c.reputacao}", style = EstiloNumeroPequeno,
                        color = corDeReputacao(c.reputacao), fontSize = 13.sp)
                }
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    vm.criarTorneio(nome, formato, escolhidos.toList(),
                        grupos, passam)
                    escolhidos.clear()
                },
                enabled = escolhidos.size >= 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (escolhidos.size < 4) "Escolha ao menos 4 clubes"
                    else "Sortear e criar"
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

// -------------------------------------------------------- TORNEIOS

@Composable
private fun AbaTorneios(e: EstadoJogo, vm: JogoViewModel) {
    val aberto = e.torneioAberto

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (aberto == null) {
            if (e.torneios.isEmpty()) {
                item {
                    Text("Nenhum torneio criado ainda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoFraco)
                }
            }
            items(e.torneios, key = { it.id }) { t ->
                Surface(
                    onClick = { vm.abrirTorneio(t) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
                ) {
                    Row(Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.nome,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Texto)
                            Text(
                                "${t.idsClubes().size} clubes · T${t.temporada}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco,
                            )
                        }
                        if (t.campeaoId != null) Selo("Encerrado", Ouro)
                        else Selo("Em andamento", Destaque)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
            return@LazyColumn
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(aberto.nome, Modifier.weight(1f),
                    style = EstiloTituloTela, color = Texto)
                TextButton(onClick = { vm.carregarTorneios() }) {
                    Text("Lista", fontSize = 11.sp)
                }
            }

            Button(
                onClick = { vm.avancarTorneio(aberto) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = aberto.campeaoId == null,
            ) {
                Text(
                    if (aberto.campeaoId != null) "Torneio encerrado"
                    else "Simular a fase atual"
                )
            }
        }

        // ------------------------------------------ INSCRIÇÃO
        val souParticipante = aberto.idsClubes().contains(e.clube?.id)
        if (souParticipante) {
            item { InscricaoDoElenco(e, vm, aberto) }
        }

        if (e.gruposDoTorneio.isNotEmpty()) {
            item { Secao("Fase de grupos") }
            e.gruposDoTorneio.forEach { (letra, tabela) ->
                item {
                    Text("GRUPO $letra", style = EstiloRotulo, color = Destaque,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                }
                items(tabela, key = { "g$letra${it.clubeId}" }) { linha ->
                    val meu = linha.clubeId == e.clube?.id
                    val passa = tabela.indexOf(linha) < aberto.quantosPassam
                    Row(
                        Modifier.fillMaxWidth()
                            .background(
                                when {
                                    meu -> Destaque.copy(alpha = .13f)
                                    passa -> Destaque.copy(alpha = .05f)
                                    else -> Fundo
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${tabela.indexOf(linha) + 1}", Modifier.width(20.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (passa) Destaque else TextoFraco)
                        Text(linha.nome, Modifier.weight(1f), maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (meu) Destaque else Texto)
                        listOf("${linha.jogos}", "${linha.saldo}",
                            "${linha.pontos}").forEach {
                            Text(it, Modifier.width(28.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Texto)
                        }
                    }
                }
            }
        }

        if (e.eliminatoriaDoTorneio.isNotEmpty()) {
            item { Secao("Eliminatória") }
            val porFase = e.eliminatoriaDoTorneio.groupBy { it.rodada }.toSortedMap()
            porFase.forEach { (_, jogos) ->
                item {
                    Text(Torneios.nomeDaFase(jogos.size).uppercase(),
                        style = EstiloRotulo, color = Alerta,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                }
                items(jogos.size) { i ->
                    val p = jogos[i]
                    val meu = p.mandanteId == e.clube?.id ||
                            p.visitanteId == e.clube?.id
                    Row(
                        Modifier.fillMaxWidth()
                            .background(
                                if (meu) Destaque.copy(alpha = .13f) else Fundo)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text("Confronto ${i + 1}", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (meu) Destaque else TextoMedio)
                        Text(
                            if (p.golsMandante != null)
                                "${p.golsMandante} - ${p.golsVisitante}"
                            else "a jogar",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.golsMandante != null) Texto else TextoFraco,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// -------------------------------------------------------- PALMARÉS

@Composable
private fun AbaPalmares(e: EstadoJogo) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text("${e.titulos.size}", style = EstiloPlacar, color = Ouro)
            Text(
                if (e.titulos.size == 1) "título conquistado"
                else "títulos conquistados",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco,
            )
            Secao("Sala de trofeus")
        }

        if (e.titulos.isEmpty()) {
            item {
                Text("Nada na estante ainda. Vá ganhar algo.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
        }

        items(e.titulos, key = { it.id }) { t ->
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = SuperficieAlta) {
                Row(Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.nomeDaCompeticao,
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                        Text("Temporada ${t.temporada}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                    }
                    Selo(t.tipo, Ouro)
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}


/**
 * INSCRIÇÃO DE ELENCO.
 *
 * Lista fechada de 23 para o torneio. Quem ficou de fora não joga, nem em
 * caso de lesão — é a mecânica real da UEFA, e ela faz a escolha ter peso:
 * deixar o garoto fora para inscrever o veterano é uma decisão, não um
 * detalhe.
 */
@Composable
private fun InscricaoDoElenco(
    e: EstadoJogo,
    vm: JogoViewModel,
    torneio: Torneio,
) {
    var editando by remember { mutableStateOf(false) }
    val selecao = remember(e.inscritosNoTorneio) {
        mutableStateListOf<Int>().apply { addAll(e.inscritosNoTorneio) }
    }

    Column(Modifier.fillMaxWidth()) {
        Secao("Inscrição de elenco") {
            Text(
                if (editando) "Cancelar" else "Editar",
                style = MaterialTheme.typography.labelSmall, color = Destaque,
                modifier = Modifier.clickable { editando = !editando },
            )
        }

        val faltam = Inscricoes.MINIMO - selecao.size
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            color = SuperficieAlta) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selecao.size} de ${Inscricoes.VAGAS} vagas",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = Texto)
                    Selo(
                        when {
                            selecao.size >= Inscricoes.MINIMO -> "Lista válida"
                            else -> "faltam $faltam"
                        },
                        if (selecao.size >= Inscricoes.MINIMO) Destaque else Erro,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Quem não está na lista não joga este torneio — nem por " +
                            "lesão de outro. Contratar depois não resolve.",
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco,
                )
            }
        }

        if (editando) {
        Spacer(Modifier.height(8.dp))
        e.elenco.sortedByDescending { it.geral }.forEach { j ->
            val dentro = j.id in selecao
            Surface(
                onClick = {
                    if (dentro) selecao.remove(j.id)
                    else if (selecao.size < Inscricoes.VAGAS) selecao.add(j.id)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (dentro) Destaque.copy(alpha = .14f) else Superficie,
            ) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (dentro) "✓" else "○", Modifier.width(20.dp),
                        color = if (dentro) Destaque else TextoFraco)
                    Column(Modifier.weight(1f)) {
                        Text(j.nome, maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dentro) Destaque else Texto)
                        Text("${j.posicao} · ${j.idade}a",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                    }
                    Text("${j.geral}", style = EstiloNumeroPequeno,
                        color = Texto, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                vm.inscreverElenco(
                    torneio,
                    e.elenco.filter { it.id in selecao },
                )
                editando = false
            },
            enabled = selecao.size >= Inscricoes.MINIMO,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Fechar a lista") }
        Spacer(Modifier.height(10.dp))
        }
    }
}
