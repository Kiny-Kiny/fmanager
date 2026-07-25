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
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.sistemas.*
import kotlinx.coroutines.launch

/*
 * GESTÃO HUMANA.
 *
 * A camada que faltava. Tudo que eu havia construído era tático ou
 * estatístico — formação, xG, garimpo, olheiro. Nada sobre lidar com
 * gente, que é metade do trabalho de um treinador.
 *
 * O princípio de desenho: NENHUMA opção é sempre certa. Elogiar quem está
 * mal soa falso. Prometer titularidade cobra depois. Provocar a imprensa
 * ganha a torcida e irrita a diretoria. Se houvesse resposta ótima, a
 * escolha não seria escolha.
 */

@Composable
fun TelaVestiario(e: EstadoJogo, vm: JogoViewModel) {
    var aba by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(aba, containerColor = Fundo, contentColor = Destaque) {
            listOf("Vestiário", "Imprensa", "Comissão").forEachIndexed { i, t ->
                Tab(aba == i, { aba = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }

        if (e.ultimaConversa.isNotBlank()) {
            Surface(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Destaque.copy(alpha = .12f),
            ) {
                Text(e.ultimaConversa, Modifier.padding(13.dp),
                    style = MaterialTheme.typography.bodySmall, color = Texto)
            }
        }

        when (aba) {
            0 -> AbaVestiario(e, vm)
            1 -> AbaImprensa(e, vm)
            else -> AbaComissao(e, vm)
        }
    }
}

// --------------------------------------------------------- VESTIÁRIO

@Composable
private fun AbaVestiario(e: EstadoJogo, vm: JogoViewModel) {
    var selecionado by remember { mutableStateOf<Jogador?>(null) }
    var avisoRenovacao by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            val clima = e.climaVestiario
            val estado = EstadoMoral.de(clima)
            val cor = when (estado) {
                EstadoMoral.EUFORICO, EstadoMoral.CONTENTE -> Destaque
                EstadoMoral.INDIFERENTE -> Alerta
                else -> Erro
            }

            Spacer(Modifier.height(6.dp))
            Text("$clima", style = EstiloPlacar, color = cor)
            Text("clima do vestiário · ${estado.rotulo.lowercase()}",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            Text(
                "A média é ponderada pelo overall: o titular insatisfeito " +
                        "contamina mais que o décimo reserva.",
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = TextoFraco,
            )

            if (e.insatisfeitos.isNotEmpty()) {
                Secao("Criando caso (${e.insatisfeitos.size})")
            } else {
                Secao("Elenco")
            }
        }

        val ordem = if (e.insatisfeitos.isNotEmpty())
            e.insatisfeitos.map { it.first } +
                    e.elenco.filter { j -> e.insatisfeitos.none { it.first.id == j.id } }
        else e.elenco.sortedByDescending { it.geral }

        items(ordem, key = { it.id }) { j ->
            val c = e.contratos[j.id]
            val moral = c?.moral ?: 55
            val estado = EstadoMoral.de(moral)

            Surface(
                onClick = { selecionado = j; avisoRenovacao = null },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Row(Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CartaJogador(j, 42.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(j.nome, maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                        Text(
                            "${estado.rotulo} · contrato até T" +
                                    "${c?.terminaEmTemporada ?: "?"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (estado) {
                                EstadoMoral.REVOLTADO,
                                EstadoMoral.INSATISFEITO -> Erro
                                EstadoMoral.INDIFERENTE -> Alerta
                                else -> Destaque
                            },
                        )
                    }
                    // Barra de moral: leitura imediata de quem precisa
                    // de conversa.
                    Box(
                        Modifier.width(4.dp).height(28.dp)
                            .background(TextoFraco.copy(alpha = .2f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(moral / 100f)
                                .align(Alignment.BottomCenter)
                                .background(corPorValor(moral))
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }

    selecionado?.let { j ->
        ModalBottomSheet(
            onDismissRequest = { selecionado = null },
            containerColor = Superficie,
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CartaJogador(j, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(j.nome, style = MaterialTheme.typography.titleLarge,
                            color = Texto)
                        val moral = e.contratos[j.id]?.moral ?: 55
                        Selo(EstadoMoral.de(moral).rotulo, corPorValor(moral))
                    }
                }

                Secao("Conversar")
                vm.assuntosDisponiveis(j).forEach { assunto ->
                    Surface(
                        onClick = { vm.conversar(j, assunto); selecionado = null },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(11.dp), color = SuperficieAlta,
                    ) {
                        Text(assunto.rotulo, Modifier.padding(13.dp),
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                    }
                }

                Secao("Renovar contrato")
                avisoRenovacao?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = Alerta, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "mesmo salário" to 1.0f,
                        "+20%" to 1.20f,
                        "+50%" to 1.50f,
                    ).forEach { (rotulo, fator) ->
                        OutlinedButton(
                            onClick = {
                                escopo.launch {
                                    avisoRenovacao = vm.renovar(j, fator)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) { Text(rotulo, fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// ---------------------------------------------------------- IMPRENSA

@Composable
private fun AbaImprensa(e: EstadoJogo, vm: JogoViewModel) {
    var perguntaAberta by remember { mutableStateOf<Pergunta?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                "As perguntas nascem do que está acontecendo com o time. " +
                        "Cada tom tem custo: nenhum agrada vestiário, " +
                        "diretoria e torcida ao mesmo tempo.",
                style = MaterialTheme.typography.bodySmall, color = TextoMedio,
            )
            Secao("Coletiva")
        }

        items(e.perguntasDaColetiva) { p ->
            Surface(
                onClick = { perguntaAberta = p },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Selo(p.contexto, Info)
                    Spacer(Modifier.height(7.dp))
                    Text("\u201C${p.texto}\u201D",
                        style = MaterialTheme.typography.bodyMedium, color = Texto)
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }

    perguntaAberta?.let { p ->
        ModalBottomSheet(
            onDismissRequest = { perguntaAberta = null },
            containerColor = Superficie,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("\u201C${p.texto}\u201D",
                    style = MaterialTheme.typography.titleMedium, color = Texto)
                Secao("Como responder")
                TomDaResposta.entries.forEach { tom ->
                    Surface(
                        onClick = {
                            vm.responderColetiva(p, tom)
                            perguntaAberta = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(11.dp), color = SuperficieAlta,
                    ) {
                        Text(tom.rotulo, Modifier.padding(13.dp),
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------- COMISSÃO

@Composable
private fun AbaComissao(e: EstadoJogo, vm: JogoViewModel) {
    var cargoAberto by remember { mutableStateOf<Cargo?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            Text(
                "O treino não funciona sozinho. Sem auxiliar, o elenco " +
                        "evolui a 82% do que evoluiria com uma comissão boa — " +
                        "e ao longo de uma temporada isso é muito.",
                style = MaterialTheme.typography.bodySmall, color = TextoMedio,
            )
            Spacer(Modifier.height(8.dp))
            Text("Folha da comissão: ${formatarEuro(e.folhaComissao)}/sem",
                style = MaterialTheme.typography.labelMedium, color = Alerta)
            Secao("Cargos")
        }

        items(Cargo.entries.toList()) { cargo ->
            val ocupante = e.comissao.firstOrNull { it.cargo == cargo }
            Surface(
                onClick = { cargoAberto = cargo },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (ocupante != null) SuperficieAlta else Superficie,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cargo.rotulo,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Texto)
                            Text(cargo.efeito,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextoFraco)
                        }
                        if (ocupante != null) {
                            Text("${ocupante.competencia}",
                                style = EstiloNumeroPequeno,
                                color = corPorValor(ocupante.competencia * 5))
                        } else {
                            Selo("vago", Erro)
                        }
                    }
                    ocupante?.let {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "${it.nome} · ${it.faixa} · " +
                                    "${formatarEuro(it.salarioSemanalEur)}/sem",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoMedio,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(90.dp)) }
    }

    cargoAberto?.let { cargo ->
        ModalBottomSheet(
            onDismissRequest = { cargoAberto = null },
            containerColor = Superficie,
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(cargo.rotulo,
                    style = MaterialTheme.typography.titleLarge, color = Texto)
                Text(
                    "Candidatos disponíveis. A qualidade depende da reputação " +
                            "do clube — clube pequeno não atrai referência.",
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco,
                )

                e.comissao.firstOrNull { it.cargo == cargo }?.let { atual ->
                    Secao("Atual")
                    OutlinedButton(
                        onClick = { vm.demitirStaff(atual); cargoAberto = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Demitir ${atual.nome}", fontSize = 12.sp) }
                }

                Secao("Contratar")
                vm.candidatosParaCargo(cargo).forEach { c ->
                    Surface(
                        onClick = { vm.contratarStaff(c); cargoAberto = null },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(11.dp), color = SuperficieAlta,
                    ) {
                        Row(Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.nome,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Texto)
                                Text("${c.faixa} · " +
                                        "${formatarEuro(c.salarioSemanalEur)}/sem",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextoFraco)
                            }
                            Text("${c.competencia}", style = EstiloNumeroPequeno,
                                color = corPorValor(c.competencia * 5))
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
