package com.exemplo.fmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exemplo.fmanager.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaFManager {
                Surface(Modifier.fillMaxSize(), color = Fundo) { App() }
            }
        }
    }
}

/** Abas da barra de baixo. O resto é navegação profunda. */
private enum class Aba(val rota: String, val rotulo: String, val icone: String) {
    PAINEL("painel", "Painel", "▣"),
    ESCALACAO("escalacao", "Time", "◈"),
    TATICAS("taticas", "Táticas", "◎"),
    MERCADO("mercado", "Mercado", "⇄"),
    INBOX("inbox", "Caixa", "✉"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: JogoViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    var rota by remember { mutableStateOf("painel") }

    BackHandler(enabled = rota != "painel") { rota = "painel" }

    // Carreira nova: escolher o clube antes de tudo.
    if (estado.precisaEscolherClube) {
        TelaEscolherClube(
            ligas = estado.ligas,
            clubesDaLiga = estado.clubesDaLiga,
            ligaSelecionada = estado.ligaSelecionada,
            tetoReputacao = estado.tetoReputacao,
            onEscolherLiga = { vm.selecionarLiga(it) },
            onVoltarParaLigas = { vm.selecionarLiga(null) },
            onEscolherClube = { vm.escolherClube(it) },
            onClubeAleatorio = { vm.clubeAleatorio() },
        )
        return
    }

    // Partida ao vivo ocupa a tela inteira, sem barras.
    val aoVivo = vm.partidaAoVivo
    if (aoVivo != null && rota == "aovivo") {
        TelaPartidaAoVivo(
            partida = aoVivo,
            nomeMandante = if (vm.souMandanteAoVivo)
                estado.clube?.nome ?: "Casa" else "Adversário",
            nomeVisitante = if (vm.souMandanteAoVivo)
                "Adversário" else estado.clube?.nome ?: "Fora",
            souMandante = vm.souMandanteAoVivo,
            taticaInicial = vm.taticaDaPartida,
            onTerminar = { vm.encerrarAoVivo(); rota = "partida" },
        )
        return
    }

    if (estado.carregando || estado.clube == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Destaque)
            Spacer(Modifier.height(22.dp))
            Text(estado.mensagem, color = TextoMedio, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val abaAtual = Aba.entries.firstOrNull { it.rota == rota }
    val urgentes = estado.noticias.count { it.urgente }

    Scaffold(
        containerColor = Fundo,
        topBar = {
            if (abaAtual == null) {
                TopAppBar(
                    title = {
                        Text(tituloDe(rota),
                            style = MaterialTheme.typography.titleLarge)
                    },
                    navigationIcon = {
                        TextButton(onClick = { rota = "painel" }) { Text("Voltar") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Fundo, titleContentColor = Texto,
                    ),
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Superficie, tonalElevation = 0.dp) {
                Aba.entries.forEach { aba ->
                    NavigationBarItem(
                        selected = rota == aba.rota,
                        onClick = { rota = aba.rota },
                        icon = {
                            if (aba == Aba.INBOX && urgentes > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = Erro) {
                                        Text("$urgentes", fontSize = 9.sp)
                                    }
                                }) { Text(aba.icone, fontSize = 17.sp) }
                            } else {
                                Text(aba.icone, fontSize = 17.sp)
                            }
                        },
                        label = {
                            Text(aba.rotulo, fontSize = 10.sp,
                                fontWeight = if (rota == aba.rota)
                                    FontWeight.Bold else FontWeight.Normal)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Destaque,
                            selectedTextColor = Destaque,
                            indicatorColor = Destaque.copy(alpha = .14f),
                            unselectedIconColor = TextoFraco,
                            unselectedTextColor = TextoFraco,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (rota) {
                "painel" -> TelaPainel(estado, vm) { rota = it }
                "escalacao" -> TelaEscalacao(vm.slots, estado.elenco)
                "taticas" -> TelaTaticas(estado, vm)
                "mercado" -> TelaMercado(estado, vm)
                "inbox" -> TelaInbox(estado)
                "elenco" -> TelaElenco(estado)
                "treino" -> TelaTreino(estado, vm)
                "tabela" -> TelaTabela(estado)
                "copa" -> TelaCopa(estado, vm) { rota = it }
                "diretoria" -> TelaDiretoria(estado)
                "artilharia" -> TelaArtilharia(estado)
                "partida" -> TelaPartida(estado)
            }
        }
    }
}

private fun tituloDe(rota: String) = when (rota) {
    "elenco" -> "Elenco"
    "treino" -> "Centro de treinamento"
    "tabela" -> "Classificação"
    "copa" -> "Copa Nacional"
    "diretoria" -> "Diretoria"
    "artilharia" -> "Artilharia"
    "partida" -> "Resumo da partida"
    else -> ""
}
