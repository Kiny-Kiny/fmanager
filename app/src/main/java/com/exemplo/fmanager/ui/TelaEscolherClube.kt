package com.exemplo.fmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.Liga
import com.exemplo.fmanager.formacao.descricaoDoTeto

/*
 * ESCOLHA DO CLUBE.
 *
 * Primeira tela de uma carreira nova. Navega liga → clube, mostrando
 * o que importa para a decisão: força do elenco, caixa e teto salarial.
 * Clube de reputação baixa é o modo difícil.
 */

@Composable
fun TelaEscolherClube(
    ligas: List<Liga>,
    clubesDaLiga: List<Clube>,
    ligaSelecionada: Liga?,
    tetoReputacao: Int,
    onEscolherLiga: (Liga) -> Unit,
    onVoltarParaLigas: () -> Unit,
    onEscolherClube: (Clube) -> Unit,
    onClubeAleatorio: () -> Unit,
) {
    if (ligaSelecionada == null) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                Text("Escolha uma liga",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = Texto)
                Text("${ligas.size} competições disponíveis",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
                Spacer(Modifier.height(14.dp))

                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    color = Alerta.copy(alpha = .12f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(descricaoDoTeto(1),
                            style = MaterialTheme.typography.bodySmall, color = Alerta)
                        Text("Os grandes abrem a porta conforme você constrói " +
                                "carreira nas próximas temporadas.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                    }
                }
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = onClubeAleatorio,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sortear um clube pra mim") }

                Spacer(Modifier.height(14.dp))
            }

            items(ligas, key = { it.id }) { liga ->
                Surface(
                    Modifier.fillMaxWidth().clickable { onEscolherLiga(liga) },
                    shape = MaterialTheme.shapes.medium,
                    color = SuperficieAlta,
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(liga.nome, color = Texto,
                                style = MaterialTheme.typography.bodyLarge)
                            Text(faixaDeForca(liga.reputacao),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextoFraco)
                        }
                        Text("${liga.reputacao}",
                            color = corDeReputacao(liga.reputacao),
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onVoltarParaLigas) { Text("← Outras ligas") }
            Text(ligaSelecionada.nome,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = Texto)
            Text("Toque num clube para assumir o comando",
                style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            Spacer(Modifier.height(12.dp))
        }

        items(clubesDaLiga, key = { it.id }) { clube ->
            val bloqueado = clube.reputacao > tetoReputacao

            Surface(
                Modifier.fillMaxWidth()
                    .clickable(enabled = !bloqueado) { onEscolherClube(clube) },
                shape = MaterialTheme.shapes.medium,
                color = if (bloqueado) Superficie else SuperficieAlta,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(clube.nome,
                                color = if (bloqueado) TextoFraco else Texto,
                                style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (bloqueado)
                                    "Fora do seu alcance por enquanto"
                                else faixaDeForca(clube.reputacao),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bloqueado) Erro
                                else corDeReputacao(clube.reputacao),
                            )
                        }
                        Text("${clube.reputacao}",
                            color = if (bloqueado) TextoFraco
                            else corDeReputacao(clube.reputacao),
                            fontWeight = FontWeight.Bold)
                    }
                    if (!bloqueado) {
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("CAIXA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextoFraco)
                                Text(formatarEuro(clube.caixaEur),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Texto)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("TETO SALARIAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextoFraco)
                                Text("${formatarEuro(clube.folhaMaxEur)}/sem",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Texto)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun faixaDeForca(reputacao: Int): String = when {
    reputacao >= 82 -> "Elite — pressão por título"
    reputacao >= 70 -> "Forte — briga por vaga continental"
    reputacao >= 58 -> "Meio de tabela"
    reputacao >= 45 -> "Luta contra o rebaixamento"
    else -> "Modo difícil"
}

internal fun corDeReputacao(reputacao: Int) = when {
    reputacao >= 78 -> Destaque
    reputacao >= 60 -> Alerta
    else -> TextoFraco
}
