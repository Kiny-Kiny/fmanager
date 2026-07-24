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
    "taticas" -> "Táticas"
    "mercado" -> "Mercado"
    "treino" -> "Treino"
    "tabela" -> "Classificação"
    "partida" -> "Resultado"
    else -> ""
}
