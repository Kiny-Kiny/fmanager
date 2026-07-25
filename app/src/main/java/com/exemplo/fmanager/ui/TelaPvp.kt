package com.exemplo.fmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.EstadoJogo
import com.exemplo.fmanager.JogoViewModel
import com.exemplo.fmanager.rede.*

/*
 * MODO ONLINE — em duas colunas, aproveitando a tela deitada.
 *
 * Coluna esquerda: seu time e o orçamento. Coluna direita: desafios e
 * classificação. Em pé isso seria rolagem infinita; deitado as duas coisas
 * que você compara ficam lado a lado.
 */

@Composable
fun TelaPvp(e: EstadoJogo, vm: JogoViewModel, irPara: (String) -> Unit) {
    if (!e.pvpConectado) {
        TelaConexao(e, vm)
        return
    }

    Row(Modifier.fillMaxSize()) {
        // ------------------------------------------- MEU TIME
        Column(
            Modifier.width(300.dp).fillMaxHeight()
                .background(Superficie).padding(14.dp),
        ) {
            Text(e.pvpApelido, style = MaterialTheme.typography.titleLarge,
                color = Texto, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${e.pvpPontos}", style = EstiloNumero, color = Destaque)
                Spacer(Modifier.width(8.dp))
                Selo(e.pvpDivisao, Ouro)
            }
            Text("${e.pvpVitorias}V ${e.pvpEmpates}E ${e.pvpDerrotas}D",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)

            Secao("Orçamento")
            val custo = e.pvpCustoElenco
            val cor = if (custo <= ORCAMENTO_PVP) Destaque else Erro
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$custo", style = EstiloNumero, color = cor)
                Text(" / $ORCAMENTO_PVP moedas",
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            }
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(TextoFraco.copy(alpha = .2f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(
                            (custo.toFloat() / ORCAMENTO_PVP).coerceIn(0f, 1f))
                        .fillMaxHeight().background(cor)
                )
            }
            Text(
                "Todos recebem o mesmo orçamento. O que se compara é montar " +
                        "elenco e armar time — não quem pegou o clube mais rico.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = TextoFraco,
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.sugerirElencoPvp() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            ) { Text("Sugerir elenco no orçamento", fontSize = 12.sp) }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { irPara("escalacao") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            ) { Text("Ajustar escalação", fontSize = 12.sp) }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.publicarDesafio() },
                enabled = custo in 1..ORCAMENTO_PVP,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            ) { Text("Publicar desafio", fontSize = 12.sp) }

            e.pvpAviso.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = Alerta)
            }

            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.sairDoPvp() }) {
                Text("Sair da conta", fontSize = 11.sp, color = TextoFraco)
            }
        }

        // -------------------------------------- DESAFIOS E RANKING
        Column(Modifier.weight(1f).fillMaxHeight()) {
            var aba by remember { mutableIntStateOf(0) }
            TabRow(aba, containerColor = Fundo, contentColor = Destaque) {
                listOf("Desafios abertos", "Meus jogos", "Classificação")
                    .forEachIndexed { i, t ->
                        Tab(aba == i, {
                            aba = i
                            when (i) {
                                0 -> vm.buscarDesafios()
                                1 -> vm.buscarMeusDesafios()
                                else -> vm.buscarClassificacao()
                            }
                        }, text = { Text(t, fontSize = 12.sp) })
                    }
            }
            when (aba) {
                0 -> ListaDesafios(e, vm)
                1 -> MeusJogos(e, vm)
                else -> Classificacao(e)
            }
        }
    }
}

// ---------------------------------------------------------- CONEXÃO

@Composable
private fun TelaConexao(e: EstadoJogo, vm: JogoViewModel) {
    var servidor by remember { mutableStateOf(e.pvpServidor) }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var apelido by remember { mutableStateOf("") }
    var criandoConta by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().padding(20.dp)) {
        // Explicação à esquerda, formulário à direita.
        Column(Modifier.weight(1f).padding(end = 20.dp)) {
            Text("Modo online", style = EstiloTituloTela, color = Texto)
            Spacer(Modifier.height(10.dp))
            Text(
                "Não existe servidor oficial. Você aponta para a instância " +
                        "que quiser — a sua, a de um amigo, uma comunitária.",
                style = MaterialTheme.typography.bodyMedium, color = TextoMedio,
            )
            Secao("Como funciona")
            listOf(
                "Todos recebem o mesmo orçamento de moedas e montam os 11 " +
                        "do acervo mundial.",
                "Você publica um desafio; alguém aceita quando quiser. " +
                        "Ninguém precisa estar online ao mesmo tempo.",
                "Os dois aparelhos simulam a MESMA partida com a mesma " +
                        "semente e comparam o resultado.",
                "O servidor só guarda dados — ele não simula nem julga. " +
                        "O árbitro é o determinismo do motor.",
            ).forEach {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("·  ", color = Destaque)
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = TextoMedio)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Para hospedar: veja HOSPEDAR-PVP.md no projeto. É um " +
                        "binário único, sem código de servidor para escrever.",
                style = MaterialTheme.typography.labelSmall, color = TextoFraco,
            )
        }

        Column(Modifier.width(320.dp)) {
            OutlinedTextField(
                value = servidor, onValueChange = { servidor = it },
                label = { Text("Endereço do servidor") },
                placeholder = { Text("https://meu-servidor.com") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.testarServidor(servidor) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            ) { Text("Testar conexão", fontSize = 12.sp) }

            Secao(if (criandoConta) "Criar conta" else "Entrar")

            if (criandoConta) {
                OutlinedTextField(
                    value = apelido, onValueChange = { apelido = it.take(18) },
                    label = { Text("Apelido no ranking") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                )
                Spacer(Modifier.height(6.dp))
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text("E-mail") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = senha, onValueChange = { senha = it },
                label = { Text("Senha") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            )

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (criandoConta) vm.criarContaPvp(servidor, email, senha, apelido)
                    else vm.entrarNoPvp(servidor, email, senha)
                },
                enabled = email.isNotBlank() && senha.length >= 8 &&
                        servidor.startsWith("http"),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
            ) { Text(if (criandoConta) "Criar e entrar" else "Entrar") }

            TextButton(onClick = { criandoConta = !criandoConta }) {
                Text(
                    if (criandoConta) "Já tenho conta" else "Criar uma conta",
                    fontSize = 11.sp,
                )
            }

            e.pvpAviso.takeIf { it.isNotBlank() }?.let {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    color = Alerta.copy(alpha = .14f)) {
                    Text(it, Modifier.padding(11.dp),
                        style = MaterialTheme.typography.labelSmall, color = Alerta)
                }
            }
        }
    }
}

// ---------------------------------------------------------- DESAFIOS

@Composable
private fun ListaDesafios(e: EstadoJogo, vm: JogoViewModel) {
    LaunchedEffect(Unit) { vm.buscarDesafios() }

    if (e.pvpDesafios.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum desafio aberto. Publique o seu.",
                style = MaterialTheme.typography.bodySmall, color = TextoFraco)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(e.pvpDesafios, key = { it.id }) { d ->
            Surface(
                onClick = { vm.aceitarDesafio(d) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Row(Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.donoApelido,
                            style = MaterialTheme.typography.bodyMedium, color = Texto)
                        Text(
                            d.elencoDono.optString("clube").ifBlank { "Time" },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${d.donoPontos}", style = EstiloNumeroPequeno,
                            color = Destaque)
                        Text("aceitar →",
                            style = MaterialTheme.typography.labelSmall,
                            color = Alerta, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeusJogos(e: EstadoJogo, vm: JogoViewModel) {
    LaunchedEffect(Unit) { vm.buscarMeusDesafios() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (e.pvpMeusDesafios.isEmpty()) {
            item {
                Text("Nada aqui ainda.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
        }

        items(e.pvpMeusDesafios, key = { it.id }) { d ->
            val prontoParaSimular = d.estado == EstadoDesafio.ACEITO &&
                    d.elencoAdversario != null

            Surface(
                onClick = { if (prontoParaSimular) vm.simularDesafio(d) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${d.donoApelido} × " +
                                        (d.adversarioApelido ?: "aguardando"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Texto,
                            )
                        }
                        Selo(
                            when (d.estado) {
                                EstadoDesafio.ABERTO -> "aberto"
                                EstadoDesafio.ACEITO -> "a simular"
                                EstadoDesafio.CONFIRMADO -> "confirmado"
                                EstadoDesafio.EM_DISPUTA -> "em disputa"
                            },
                            when (d.estado) {
                                EstadoDesafio.CONFIRMADO -> Destaque
                                EstadoDesafio.EM_DISPUTA -> Erro
                                EstadoDesafio.ACEITO -> Alerta
                                else -> TextoFraco
                            },
                        )
                    }
                    if (prontoParaSimular) {
                        Spacer(Modifier.height(6.dp))
                        Text("Toque para simular e enviar o resultado.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Alerta)
                    }
                    if (d.estado == EstadoDesafio.EM_DISPUTA) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Os dois resultados divergiram. A partida foi " +
                                    "anulada e ninguém pontuou.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Erro,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Classificacao(e: EstadoJogo) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (e.pvpClassificacao.isEmpty()) {
            item {
                Text("Classificação vazia.",
                    style = MaterialTheme.typography.bodySmall, color = TextoFraco)
            }
        }
        items(e.pvpClassificacao.size) { i ->
            val t = e.pvpClassificacao[i]
            val eu = t.id == e.pvpUsuarioId
            Row(
                Modifier.fillMaxWidth()
                    .background(if (eu) Destaque.copy(alpha = .12f) else Fundo)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i < 3) Ouro else TextoFraco)
                Column(Modifier.weight(1f)) {
                    Text(t.apelido, style = MaterialTheme.typography.bodySmall,
                        color = if (eu) Destaque else Texto, maxLines = 1)
                    Text("${t.divisao} · ${t.vitorias}V ${t.empates}E ${t.derrotas}D",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextoFraco, fontSize = 9.sp)
                }
                Text("${t.pontos}", style = EstiloNumeroPequeno, color = Texto)
            }
        }
    }
}
