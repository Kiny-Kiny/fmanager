package com.exemplo.fmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exemplo.fmanager.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaFManager {
                Surface(Modifier.fillMaxSize(), color = Fundo) {
                    App()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: JogoViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    var rota by remember { mutableStateOf("inicio") }

    BackHandler(enabled = rota != "inicio") { rota = "inicio" }

    // Carreira nova: escolher clube antes de qualquer coisa.
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

    // Partida ao vivo ocupa a tela inteira.
    val aoVivo = vm.partidaAoVivo
    if (aoVivo != null && rota == "aovivo") {
        val e = estado
        TelaPartidaAoVivo(
            partida = aoVivo,
            nomeMandante = if (vm.souMandanteAoVivo) e.clube?.nome ?: "Casa" else "Visitante",
            nomeVisitante = if (vm.souMandanteAoVivo) "Visitante" else e.clube?.nome ?: "Fora",
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
            Spacer(Modifier.height(20.dp))
            Text(estado.mensagem, color = TextoFraco, textAlign = TextAlign.Center)
        }
        return
    }

    Scaffold(
        containerColor = Fundo,
        topBar = {
            if (rota != "inicio") {
                TopAppBar(
                    title = { Text(tituloDe(rota)) },
                    navigationIcon = {
                        TextButton(onClick = { rota = "inicio" }) { Text("Voltar") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Fundo,
                        titleContentColor = Texto,
                    ),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (rota) {
                "inicio" -> TelaInicio(estado, vm) { rota = it }
                "elenco" -> TelaElenco(estado)
                "escalacao" -> TelaEscalacao(vm.slots, estado.elenco)
                "copa" -> TelaCopa(estado, vm) { rota = it }
                "taticas" -> TelaTaticas(estado, vm)
                "mercado" -> TelaMercado(estado, vm)
                "treino" -> TelaTreino(estado, vm)
                "tabela" -> TelaTabela(estado)
                "partida" -> TelaPartida(estado)
            }
        }
    }
}

private fun tituloDe(rota: String) = when (rota) {
    "elenco" -> "Elenco"
    "escalacao" -> "Escalação"
    "copa" -> "Copa"
    "taticas" -> "Táticas"
    "mercado" -> "Mercado"
    "treino" -> "Treino"
    "tabela" -> "Classificação"
    "partida" -> "Resultado"
    else -> ""
}
