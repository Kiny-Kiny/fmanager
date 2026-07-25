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
    PVP("pvp", "Online", "⚔"),
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
    ) { padding ->
        /*
         * Em tela deitada a ALTURA é o recurso escasso. Gastar 80dp dela
         * com uma barra embaixo é desperdício, então a navegação virou um
         * trilho na lateral — que é a orientação natural do gesto quando
         * o aparelho está de lado.
         */
        Row(Modifier.padding(padding).fillMaxSize()) {
            NavigationRail(
                containerColor = Superficie,
                modifier = Modifier.width(76.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                Aba.entries.forEach { aba ->
                    NavigationRailItem(
                        selected = rota == aba.rota,
                        onClick = { rota = aba.rota },
                        icon = {
                            if (aba == Aba.INBOX && urgentes > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = Erro) {
                                        Text("$urgentes", fontSize = 9.sp)
                                    }
                                }) { Text(aba.icone, fontSize = 18.sp) }
                            } else {
                                Text(aba.icone, fontSize = 18.sp)
                            }
                        },
                        label = {
                            Text(aba.rotulo, fontSize = 9.sp,
                                fontWeight = if (rota == aba.rota)
                                    FontWeight.Bold else FontWeight.Normal)
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Destaque,
                            selectedTextColor = Destaque,
                            indicatorColor = Destaque.copy(alpha = .14f),
                            unselectedIconColor = TextoFraco,
                            unselectedTextColor = TextoFraco,
                        ),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
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
                "online" -> {
                    val rede by vm.estadoRede.collectAsState()
                    val partidaRede by vm.estadoPartidaRede.collectAsState()
                    val salas by vm.salasEncontradas.collectAsState()
                    val buscando by vm.procurando.collectAsState()
                    TelaMultijogador(
                        estadoRede = rede,
                        estadoPartida = partidaRede,
                        salasEncontradas = salas,
                        procurando = buscando,
                        onHospedar = { vm.hospedarSala(it) },
                        onProcurar = { vm.procurarSalas() },
                        onEntrar = { vm.entrarNaSala(it) },
                        onConfirmar = {
                            if (vm.comecarPartidaEmRede() != null) rota = "aovivo"
                        },
                        onCancelar = { vm.fecharSala() },
                    )
                }
                "artilharia" -> TelaArtilharia(estado)
                "analise" -> TelaAnalise(estado, vm)
                "torneios" -> TelaTorneios(estado, vm)
                "vestiario" -> TelaVestiario(estado, vm)
                "olheiro" -> {
                    LaunchedEffect(Unit) { vm.buscarParaOlheiro() }
                    TelaOlheiro(
                        jogadores = estado.candidatosOlheiro,
                        niveis = estado.niveisObservacao,
                        dnaDoClube = estado.dnaDoClube,
                        onTrocarDna = { vm.definirDna(it) },
                        onObservar = { vm.observar(it) },
                        onPararObservar = { vm.pararDeObservar(it) },
                    )
                }
                "partida" -> TelaPartida(estado)
                "pvp" -> TelaPvp(estado, vm) { rota = it }
            }
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
    "online" -> "Partida local"
    "olheiro" -> "Olheiros"
    "analise" -> "Análise"
    "torneios" -> "Torneios"
    "vestiario" -> "Vestiário"
    "pvp" -> "Modo online"
    "partida" -> "Resumo da partida"
    else -> ""
}
