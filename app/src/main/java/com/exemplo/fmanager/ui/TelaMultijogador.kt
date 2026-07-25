package com.exemplo.fmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.rede.*
import kotlinx.coroutines.launch

/*
 * TELA DO MULTIJOGADOR LOCAL.
 *
 * Um aparelho abre a sala, o outro encontra na rede. Não existe servidor
 * em nenhum ponto do caminho.
 *
 * A tela expõe de propósito duas coisas que normalmente ficariam
 * escondidas, porque sem servidor elas são a garantia do jogo:
 *
 *   - o CÓDIGO DE VERIFICAÇÃO, que os dois comparam a olho para saber
 *     que ninguém está no meio da conexão
 *   - a CONFERÊNCIA DE BASE, que impede começar uma partida que vai
 *     dessincronizar no meio
 */

@Composable
fun TelaMultijogador(
    estadoRede: EstadoRede,
    estadoPartida: EstadoPartidaRede?,
    salasEncontradas: List<SalaEncontrada>,
    procurando: Boolean,
    onHospedar: (String) -> Unit,
    onProcurar: () -> Unit,
    onEntrar: (SalaEncontrada) -> Unit,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    var nomeDaSala by remember { mutableStateOf("") }
    val escopo = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Partida local", style = EstiloTituloTela, color = Texto)
            Text("Dois aparelhos na mesma rede Wi-Fi. Sem servidor.",
                style = MaterialTheme.typography.bodySmall, color = TextoMedio)
            Spacer(Modifier.height(6.dp))
            Text(
                "Jogar pela internet exigiria um servidor de sinalização " +
                        "para atravessar o NAT — não tem como fugir disso.",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco,
            )
        }

        // ------------------------------------------------- CONECTADO
        if (estadoRede is EstadoRede.Conectado) {
            item {
                Secao("Confirmação de segurança")
                Surface(Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), color = SuperficieTopo) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("CONECTADO A ${estadoRede.apelidoDoOutro.uppercase()}",
                            style = EstiloRotulo, color = Destaque)
                        Spacer(Modifier.height(14.dp))
                        Text(estadoRede.codigoVerificacao,
                            style = EstiloPlacar, color = Texto)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Os dois aparelhos precisam mostrar EXATAMENTE " +
                                    "este número. Se diferirem, alguém está " +
                                    "interceptando a conexão — não continuem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextoMedio, textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            item { EstadoDaPartida(estadoPartida) }

            item {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onConfirmar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = estadoPartida !is EstadoPartidaRede.BaseIncompativel,
                ) { Text("Os números batem — começar") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Cancelar") }
                Spacer(Modifier.height(60.dp))
            }
            return@LazyColumn
        }

        // ----------------------------------------------- AGUARDANDO
        if (estadoRede is EstadoRede.Aguardando) {
            item {
                Secao("Sala aberta")
                Surface(Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), color = SuperficieAlta) {
                    Column(Modifier.padding(20.dp)) {
                        Text(estadoRede.nomeDaSala,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Texto)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), color = Destaque,
                                strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Esperando o outro jogador entrar...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextoMedio)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancelar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Fechar a sala") }
                Spacer(Modifier.height(60.dp))
            }
            return@LazyColumn
        }

        if (estadoRede is EstadoRede.Recusado) {
            item {
                Spacer(Modifier.height(14.dp))
                Surface(Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Erro.copy(alpha = .14f)) {
                    Text(estadoRede.motivo, Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall, color = Erro)
                }
            }
        }

        // ---------------------------------------------------- CRIAR
        item {
            Secao("Criar uma sala")
            OutlinedTextField(
                value = nomeDaSala,
                onValueChange = { nomeDaSala = it.take(20) },
                label = { Text("Nome da sala") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onHospedar(nomeDaSala.ifBlank { "Sala sem nome" }) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Abrir sala") }
        }

        // -------------------------------------------------- ENTRAR
        item {
            Secao("Entrar numa sala") {
                if (procurando) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp), color = Destaque, strokeWidth = 2.dp)
                } else {
                    Text("Procurar", style = MaterialTheme.typography.labelSmall,
                        color = Destaque,
                        modifier = Modifier.clickable { onProcurar() })
                }
            }
        }

        if (salasEncontradas.isEmpty() && !procurando) {
            item {
                Text("Nenhuma sala encontrada na rede. Toque em Procurar.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
        }

        items(salasEncontradas) { s ->
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable { onEntrar(s) },
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Row(Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.nomeDaSala,
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                        Text("${s.apelidoDoAnfitriao} · ${s.endereco}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                    }
                    Text("Entrar", style = MaterialTheme.typography.labelSmall,
                        color = Destaque)
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun EstadoDaPartida(e: EstadoPartidaRede?) {
    if (e == null) return

    val (texto, cor) = when (e) {
        is EstadoPartidaRede.Preparando ->
            "Conferindo se os dois têm a mesma base de jogadores..." to TextoMedio
        is EstadoPartidaRede.BaseIncompativel -> e.detalhe to Erro
        is EstadoPartidaRede.TrocandoEsquadroes ->
            "Bases compatíveis. Trocando escalações." to Destaque
        is EstadoPartidaRede.EmJogo -> "Em jogo — lance ${e.lance}" to Destaque
        is EstadoPartidaRede.Dessincronizada ->
            "Dessincronizou no lance ${e.lance}. A partida foi anulada: " +
                    "as duas simulações divergiram." to Erro
        is EstadoPartidaRede.Terminada ->
            "Fim: ${e.golsMandante} x ${e.golsVisitante}" to Destaque
        is EstadoPartidaRede.Abortada -> e.motivo to Erro
    }

    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = cor.copy(alpha = .12f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = cor)
        }
    }
}
