package com.exemplo.fmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.EstadoJogo
import com.exemplo.fmanager.JogoViewModel
import com.exemplo.fmanager.dados.Artilheiro
import com.exemplo.fmanager.sistemas.Noticia
import com.exemplo.fmanager.sistemas.Situacao
import com.exemplo.fmanager.sistemas.TipoNoticia
import kotlinx.coroutines.launch

/*
 * PAINEL DA CARREIRA.
 *
 * Substitui a lista de links por um painel: o que importa aparece de
 * cara — situação com a diretoria, forma recente, próximo jogo e as
 * mensagens urgentes. Navegação profunda fica na barra de baixo.
 */

@Composable
fun TelaPainel(e: EstadoJogo, vm: JogoViewModel, irPara: (String) -> Unit) {
    val corClube = corDoClube(e.clube?.nome ?: "")
    val escopo = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize()) {

        // ------------------------------------------------ CABEÇALHO
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(gradienteClube(corClube))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(corClube.copy(alpha = .25f))
                                .border(1.5.dp, corClube, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (e.clube?.nome ?: "?").take(2).uppercase(),
                                style = EstiloNumeroPequeno, color = corClube,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.clube?.nome ?: "—",
                                style = EstiloTituloTela, color = Texto, maxLines = 2)
                            Text(
                                "Temporada ${e.carreira?.temporada}  ·  " +
                                        "Rodada ${e.carreira?.rodada}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextoMedio,
                            )
                        }
                    }

                    if (e.estiloHerdado.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Selo(e.estiloHerdado, corClube)
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CartaoNumero(
                            "Posição",
                            if (e.posicao > 0) "${e.posicao}º" else "—",
                            Modifier.weight(1f),
                            cor = if (e.expectativa != null && e.posicao > 0 &&
                                e.posicao <= e.expectativa.posicaoAlvo
                            ) Destaque else Alerta,
                            complemento = e.expectativa?.let {
                                "meta: ${it.posicaoAlvo}º"
                            },
                        )
                        CartaoNumero(
                            "Caixa", formatarEuro(e.caixa), Modifier.weight(1f),
                            complemento = "${formatarEuro(e.folha)}/sem",
                        )
                    }
                }
            }
        }

        // -------------------------------------------------- DIRETORIA
        e.expectativa?.let { exp ->
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Secao("Diretoria") {
                        Text("Ver metas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Destaque,
                            modifier = Modifier.clickable { irPara("diretoria") })
                    }

                    val corSit = when (exp.situacao) {
                        Situacao.SEGURO -> Destaque
                        Situacao.ESTAVEL -> Info
                        Situacao.PRESSIONADO -> Alerta
                        Situacao.AMEACADO -> Erro
                    }

                    Surface(Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp), color = SuperficieAlta) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Selo(exp.situacao.rotulo, corSit)
                                Spacer(Modifier.weight(1f))
                                Text("${exp.confianca}%",
                                    style = EstiloNumeroPequeno, color = corSit)
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier.fillMaxWidth().height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(TextoFraco.copy(alpha = .18f)),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(exp.confianca / 100f)
                                        .fillMaxHeight().background(corSit)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(exp.resumo,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextoMedio)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------ FORMA
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Secao("Forma recente")
                Forma(e.forma)

                if (e.insatisfeitos.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        Modifier.fillMaxWidth().clickable { irPara("vestiario") },
                        shape = RoundedCornerShape(12.dp),
                        color = Erro.copy(alpha = .12f),
                    ) {
                        Row(Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${e.insatisfeitos.size} jogador(es) " +
                                        "insatisfeito(s)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Erro)
                                Text(e.insatisfeitos.take(3)
                                    .joinToString(", ") { it.first.nome },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextoFraco, maxLines = 1)
                            }
                            Text("→", color = Erro)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ PRÓXIMO JOGO
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Secao("Próximo compromisso")

                e.proximaPartida?.let { p ->
                    val emCasa = p.mandanteId == e.clube?.id
                    Surface(Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp), color = SuperficieTopo) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Selo(if (emCasa) "Em casa" else "Fora",
                                    if (emCasa) Destaque else Alerta)
                                Spacer(Modifier.weight(1f))
                                Text("Rodada ${p.rodada}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextoFraco)
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    escopo.launch {
                                        if (vm.prepararAoVivo(daCopa = false) != null)
                                            irPara("aovivo")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Assistir a partida") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    vm.jogarProximaPartida(); irPara("partida")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Simular direto") }
                        }
                    }
                } ?: Text("Temporada da liga encerrada.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)

                if (e.proximaCopa != null && e.viveNaCopa) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        Modifier.fillMaxWidth().clickable { irPara("copa") },
                        shape = RoundedCornerShape(14.dp),
                        color = SuperficieAlta,
                    ) {
                        Row(Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Copa Nacional",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Texto)
                                Text(e.faseDaCopa,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Destaque)
                            }
                            Text("→", color = TextoFraco)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------- MENSAGENS
        val urgentes = e.noticias.filter { it.urgente }
        if (urgentes.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Secao("Precisa da sua atenção") {
                        Text("Caixa de entrada",
                            style = MaterialTheme.typography.labelSmall,
                            color = Destaque,
                            modifier = Modifier.clickable { irPara("inbox") })
                    }
                }
            }
            items(urgentes.take(3)) { n ->
                Box(Modifier.padding(horizontal = 20.dp, vertical = 3.dp)) {
                    CartaoNoticia(n)
                }
            }
        }

        // ------------------------------------------------ ARTILHARIA
        if (e.artilheirosDoClube.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Secao("Destaques do elenco") {
                        Text("Artilharia",
                            style = MaterialTheme.typography.labelSmall,
                            color = Destaque,
                            modifier = Modifier.clickable { irPara("artilharia") })
                    }
                }
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(e.artilheirosDoClube.take(8)) { a ->
                        CartaoArtilheiro(a)
                    }
                }
            }
        }

        // ------------------------------------------------ ATALHOS
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Secao("Mais")
                listOf(
                    "vestiario" to "Vestiário, imprensa e comissão",
                    "olheiro" to "Olheiros e relatórios",
                    "analise" to "Análise: evolução, garimpo, parecidos",
                    "elenco" to "Elenco completo",
                    "treino" to "Centro de treinamento",
                    "tabela" to "Classificação",
                    "artilharia" to "Artilharia",
                    "copa" to "Copa Nacional",
                    "torneios" to "Torneios e palmarés",
                    "online" to "Jogar contra alguém na sua rede",
                ).forEach { (r, rotulo) ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { irPara(r) },
                        shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
                    ) {
                        Row(Modifier.padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(rotulo, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Texto)
                            Text("→", color = TextoFraco)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ------------------------------------------------------------- INBOX

@Composable
fun TelaInbox(e: EstadoJogo) {
    var filtro by remember { mutableStateOf<TipoNoticia?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(filtro == null, { filtro = null }, { Text("Tudo") })
            }
            items(TipoNoticia.entries.toList()) { t ->
                FilterChip(filtro == t, { filtro = t },
                    { Text(t.rotulo, fontSize = 11.sp) })
            }
        }

        val lista = e.noticias.filter { filtro == null || it.tipo == filtro }
            .sortedByDescending { it.urgente }

        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada por aqui.", color = TextoFraco)
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lista) { n -> CartaoNoticia(n) }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun CartaoNoticia(n: Noticia) {
    var aberta by remember { mutableStateOf(false) }
    val cor = when (n.tipo) {
        TipoNoticia.DIRETORIA -> if (n.urgente) Erro else Info
        TipoNoticia.OLHEIRO -> Destaque
        TipoNoticia.MEDICO -> Erro
        TipoNoticia.CONTRATO -> Alerta
        TipoNoticia.IMPRENSA -> TextoMedio
        TipoNoticia.MERCADO -> Ouro
    }

    Surface(
        Modifier.fillMaxWidth().clickable { aberta = !aberta },
        shape = RoundedCornerShape(14.dp),
        color = SuperficieAlta,
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier.width(3.dp).height(if (aberta) 64.dp else 38.dp)
                    .clip(RoundedCornerShape(2.dp)).background(cor)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Selo(n.tipo.rotulo, cor)
                    if (n.urgente) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Erro))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(n.titulo, style = MaterialTheme.typography.titleMedium,
                    color = Texto, maxLines = if (aberta) 3 else 2)
                if (aberta) {
                    Spacer(Modifier.height(8.dp))
                    Text(n.corpo, style = MaterialTheme.typography.bodySmall,
                        color = TextoMedio)
                }
            }
        }
    }
}

// --------------------------------------------------------- DIRETORIA

@Composable
fun TelaDiretoria(e: EstadoJogo) {
    val exp = e.expectativa ?: return
    val corSit = when (exp.situacao) {
        Situacao.SEGURO -> Destaque
        Situacao.ESTAVEL -> Info
        Situacao.PRESSIONADO -> Alerta
        Situacao.AMEACADO -> Erro
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("${exp.confianca}%", style = EstiloPlacar, color = corSit)
            Text("confiança da diretoria",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            Spacer(Modifier.height(10.dp))
            Selo(exp.situacao.rotulo, corSit)
            Spacer(Modifier.height(14.dp))
            Text(exp.resumo, style = MaterialTheme.typography.bodyMedium,
                color = TextoMedio)
            Secao("Objetivos da temporada")
        }

        items(exp.objetivos) { o ->
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Row(Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(
                                (if (o.cumprido) Destaque else Alerta)
                                    .copy(alpha = .2f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (o.cumprido) "✓" else "!",
                            color = if (o.cumprido) Destaque else Alerta,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.descricao,
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                        Text(o.progresso,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (o.cumprido) Destaque else TextoFraco)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }
}

// -------------------------------------------------------- ARTILHARIA

@Composable
fun TelaArtilharia(e: EstadoJogo) {
    var daLiga by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = if (daLiga) 1 else 0,
            containerColor = Fundo, contentColor = Destaque,
        ) {
            Tab(!daLiga, { daLiga = false },
                text = { Text("Meu elenco") })
            Tab(daLiga, { daLiga = true },
                text = { Text("Liga") })
        }

        val lista = if (daLiga) e.artilheirosDaLiga else e.artilheirosDoClube

        if (lista.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum jogo disputado ainda.", color = TextoFraco)
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(lista) { i, a -> LinhaArtilheiro(i + 1, a) }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun LinhaArtilheiro(posicao: Int, a: Artilheiro) {
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = SuperficieAlta,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$posicao", Modifier.width(24.dp),
                style = EstiloNumeroPequeno,
                color = if (posicao <= 3) Ouro else TextoFraco, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(a.nome, style = MaterialTheme.typography.bodyMedium,
                    color = Texto, maxLines = 1)
                Text("${a.clube} · ${a.jogos} jogos",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextoFraco, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${a.gols}", style = EstiloNumeroPequeno, color = Destaque)
                Text("${a.assistencias} assist.",
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            }
        }
    }
}

@Composable
private fun CartaoArtilheiro(a: Artilheiro) {
    Surface(
        Modifier.width(96.dp), shape = RoundedCornerShape(14.dp),
        color = SuperficieAlta,
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(faixaDe(a.geral).gradiente),
                contentAlignment = Alignment.Center,
            ) {
                Text("${a.geral}", style = EstiloNumeroPequeno,
                    color = Color.Black.copy(alpha = .7f))
            }
            Spacer(Modifier.height(8.dp))
            Text(a.nome.split(" ").last(),
                style = MaterialTheme.typography.labelSmall,
                color = Texto, maxLines = 1, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text("${a.gols}g ${a.assistencias}a",
                style = MaterialTheme.typography.labelSmall, color = Destaque,
                fontSize = 10.sp)
        }
    }
}
