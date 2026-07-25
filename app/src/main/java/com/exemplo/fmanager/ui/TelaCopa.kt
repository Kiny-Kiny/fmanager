package com.exemplo.fmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exemplo.fmanager.EstadoJogo
import com.exemplo.fmanager.JogoViewModel
import com.exemplo.fmanager.sistemas.Copa
import kotlinx.coroutines.launch

/*
 * CHAVEAMENTO DA COPA.
 *
 * Eliminatória em jogo único, em paralelo à liga. Empate é decidido
 * pelo mando de campo — a aproximação mais simples para pênaltis.
 */

@Composable
fun TelaCopa(
    e: EstadoJogo,
    vm: JogoViewModel? = null,
    irPara: (String) -> Unit = {},
) {
    val escopo = rememberCoroutineScope()
    val meuId = e.clube?.id

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Copa Nacional",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Texto)
            Text(
                if (e.viveNaCopa) "Você está na ${e.faseDaCopa.lowercase()}"
                else "Eliminado desta edição",
                style = MaterialTheme.typography.bodySmall,
                color = if (e.viveNaCopa) Destaque else Erro,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Próximo jogo de copa, com opção de assistir
        e.proximaCopa?.let { p ->
            item {
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = SuperficieAlta) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PRÓXIMO JOGO DE COPA",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (p.mandanteId == meuId) "Em casa" else "Fora de casa",
                            style = MaterialTheme.typography.titleMedium, color = Texto,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (vm != null) {
                            Button(
                                onClick = {
                                    escopo.launch {
                                        if (vm.prepararAoVivo(daCopa = true) != null) {
                                            irPara("aovivo")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Assistir a partida") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Chaveamento por fase
        val porFase = e.partidasCopa.groupBy { it.rodada }.toSortedMap()
        porFase.forEach { (rodada, jogos) ->
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    Copa.nomeDaFase(jogos.size).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Destaque,
                )
            }
            items(jogos.size) { idx ->
                val p = jogos[idx]
                val souEu = p.mandanteId == meuId || p.visitanteId == meuId
                val jogado = p.golsMandante != null

                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = if (souEu) Destaque.copy(alpha = .12f) else Superficie,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Confronto ${idx + 1}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (souEu) Destaque else TextoFraco,
                        )
                        Text(
                            if (jogado) "${p.golsMandante} - ${p.golsVisitante}"
                            else "a jogar",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (jogado) Texto else TextoFraco,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
