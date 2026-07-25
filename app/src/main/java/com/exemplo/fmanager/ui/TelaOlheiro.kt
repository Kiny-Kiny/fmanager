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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Papel
import com.exemplo.fmanager.sistemas.*

/*
 * RELATÓRIO DE OLHEIRO — inspirado no PyScoutFM.
 *
 * Duas ideias daquele projeto se juntam aqui:
 *
 *   1. Nota do jogador para TODAS as posições ao lado, não só a dele.
 *      É assim que se acha o lateral que na verdade é um ala, ou o
 *      volante que dá um zagueiro melhor do que os que você tem.
 *
 *   2. Atributo mascarado quando o jogador não foi observado. Antes você
 *      via os 29 atributos exatos de 16 mil jogadores de graça. Agora
 *      jogador desconhecido mostra faixa, e observar custa.
 */

private val PAPEIS_RELATORIO = listOf(
    Papel.GOL, Papel.ZAG, Papel.LE, Papel.LD, Papel.VOL,
    Papel.MC, Papel.MEI, Papel.ME, Papel.MD, Papel.PE, Papel.PD, Papel.ATA,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaOlheiro(
    jogadores: List<Jogador>,
    niveis: Map<Int, Int>,
    dnaDoClube: Dna,
    onTrocarDna: (Dna) -> Unit,
    onObservar: (Jogador) -> Unit,
    onPararObservar: (Jogador) -> Unit,
) {
    var ordenarPor by remember { mutableStateOf<Papel?>(null) }
    var porDna by remember { mutableStateOf(false) }
    var selecionado by remember { mutableStateOf<Jogador?>(null) }

    Column(Modifier.fillMaxSize()) {

        // ---------------------------------------------- DNA DO CLUBE
        Column(Modifier.padding(horizontal = 16.dp)) {
            Secao("DNA do clube")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Dnas.todos.forEach { d ->
                    FilterChip(
                        selected = dnaDoClube.nome == d.nome,
                        onClick = { onTrocarDna(d) },
                        label = { Text(d.nome, fontSize = 12.sp) },
                    )
                }
            }
            Text(
                "O DNA não mede se ele joga bem na posição — mede se ele joga " +
                        "do jeito que este clube joga.",
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = TextoFraco,
            )
        }

        // -------------------------------------------- ORDENAÇÃO
        Column(Modifier.padding(horizontal = 16.dp)) {
            Secao("Ordenar por")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = porDna,
                    onClick = { porDna = true; ordenarPor = null },
                    label = { Text("DNA", fontSize = 11.sp) },
                )
                FilterChip(
                    selected = !porDna && ordenarPor == null,
                    onClick = { porDna = false; ordenarPor = null },
                    label = { Text("Overall", fontSize = 11.sp) },
                )
                PAPEIS_RELATORIO.forEach { p ->
                    FilterChip(
                        selected = !porDna && ordenarPor == p,
                        onClick = { porDna = false; ordenarPor = p },
                        label = { Text(p.sigla, fontSize = 11.sp) },
                    )
                }
            }
        }

        val lista = remember(jogadores, ordenarPor, porDna, dnaDoClube.nome) {
            when {
                porDna -> jogadores.sortedByDescending { dnaDoClube.notaDe(it) }
                ordenarPor != null -> jogadores.sortedByDescending {
                    PesosPorPosicao.nota(it, ordenarPor!!)
                }
                else -> jogadores.sortedByDescending { it.geral }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(lista, key = { it.id }) { j ->
                LinhaOlheiro(
                    jogador = j,
                    nivel = niveis[j.id] ?: 0,
                    dna = dnaDoClube,
                    destaque = ordenarPor,
                    porDna = porDna,
                ) { selecionado = j }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    selecionado?.let { j ->
        val nivel = niveis[j.id] ?: 0
        ModalBottomSheet(
            onDismissRequest = { selecionado = null },
            containerColor = Superficie,
        ) {
            FichaDeOlheiro(
                jogador = j, nivel = nivel, dna = dnaDoClube,
                onObservar = { onObservar(j); selecionado = null },
                onParar = { onPararObservar(j); selecionado = null },
            )
        }
    }
}

@Composable
private fun LinhaOlheiro(
    jogador: Jogador,
    nivel: Int,
    dna: Dna,
    destaque: Papel?,
    porDna: Boolean,
    onClicar: () -> Unit,
) {
    val geral = Olheiro.geralObservado(jogador, nivel)
    val notaDna = dna.notaDe(jogador)

    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClicar),
        shape = RoundedCornerShape(12.dp), color = SuperficieAlta,
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CartaJogador(jogador, 42.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(jogador.nome, maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium, color = Texto)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${jogador.posicao} · ${jogador.idade}a · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco)
                        Selo(
                            if (nivel == 0) "não observado"
                            else "${Olheiro.confianca(nivel)}% conf.",
                            if (nivel == 0) TextoFraco else corPorValor(
                                Olheiro.confianca(nivel)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Selo(Arquetipo.principalDe(jogador).rotulo, Info)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (porDna) {
                        Text("$notaDna", style = EstiloNumeroPequeno,
                            color = corPorValor(notaDna))
                        Text("DNA", style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco, fontSize = 8.sp)
                    } else {
                        // Fora do seu clube, o overall é estimado.
                        Text(geral.toString(), style = EstiloNumeroPequeno,
                            color = if (geral.exato) Texto else Alerta)
                        Text(Olheiro.potencialObservado(jogador, nivel),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco, fontSize = 8.sp)
                    }
                }
            }

            // As notas por posição lado a lado: o coração do relatório.
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PAPEIS_RELATORIO.forEach { p ->
                    val nota = PesosPorPosicao.nota(jogador, p)
                    val forte = destaque == p
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (forte) Destaque.copy(alpha = .2f)
                                else corPorValor(nota).copy(alpha = .10f)
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(p.sigla, style = MaterialTheme.typography.labelSmall,
                            color = TextoFraco, fontSize = 8.sp)
                        Text("$nota",
                            style = MaterialTheme.typography.labelMedium,
                            color = corPorValor(nota), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FichaDeOlheiro(
    jogador: Jogador,
    nivel: Int,
    dna: Dna,
    onObservar: () -> Unit,
    onParar: () -> Unit,
) {
    val n = NivelObservacao.de(nivel)

    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 560.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CartaJogador(jogador, 58.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(jogador.nome,
                        style = MaterialTheme.typography.titleLarge, color = Texto)
                    Text("${jogador.clube} · ${jogador.posicao}",
                        style = MaterialTheme.typography.bodySmall, color = TextoMedio)
                    Spacer(Modifier.height(4.dp))
                    Selo(n.rotulo, corPorValor(Olheiro.confianca(nivel)))
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CartaoNumero("Overall estimado",
                    Olheiro.geralObservado(jogador, nivel).toString(),
                    Modifier.weight(1f))
                CartaoNumero("Potencial",
                    Olheiro.potencialObservado(jogador, nivel),
                    Modifier.weight(1f))
                CartaoNumero("DNA", "${dna.notaDe(jogador)}",
                    Modifier.weight(1f), cor = corPorValor(dna.notaDe(jogador)))
            }

            Secao("Perfil")
            RadarDoJogador(jogador)

            Secao("Encaixe no DNA ${dna.nome}")
        }

        items(dna.detalhar(jogador)) { (pilar, valor) ->
            Column(Modifier.padding(vertical = 4.dp)) {
                BarraAtributo(pilar.nome, valor)
                Text(pilar.descricao,
                    Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            }
        }

        item { Secao("Atributos") }

        items(Atributos.todos) { attr ->
            val obs = Olheiro.observar(jogador, attr, nivel)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(attr, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = TextoMedio)
                Text(obs.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (obs.exato) Texto else Alerta,
                    fontWeight = FontWeight.Bold)
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            if (nivel >= 4) {
                Text("Relatório completo. Nada mais a descobrir.",
                    style = MaterialTheme.typography.bodySmall, color = Destaque)
            } else {
                Button(onClick = onObservar, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (nivel == 0) "Colocar um olheiro nele"
                        else "Continuar observando (${formatarEuro(n.custoSemanal)}/sem)"
                    )
                }
            }
            if (nivel in 1..3) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onParar, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)) {
                    Text("Parar de observar")
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
