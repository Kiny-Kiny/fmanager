package com.exemplo.fmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.familiaridade
import com.exemplo.fmanager.dados.geralEm
import com.exemplo.fmanager.formacao.Papel

/*
 * COMPONENTES.
 *
 * A carta do jogador é o elemento central do app, então recebe o
 * tratamento de carta de coleção: moldura da faixa (bronze, prata, ouro,
 * elite), overall no canto e a imagem da API por baixo. Quando o jogador
 * está fora da posição de origem, a moldura muda de cor — a informação
 * mais importante da tela de escalação virou parte do desenho.
 */

@Composable
fun CartaJogador(
    jogador: Jogador,
    tamanho: Dp = 56.dp,
    papel: Papel? = null,
    mostrarNome: Boolean = false,
    onClicar: (() -> Unit)? = null,
) {
    val overall = papel?.let { jogador.geralEm(it) } ?: jogador.geral
    val faixa = faixaDe(overall)

    // Fora da posição, a moldura avisa antes de qualquer texto.
    val corMoldura = papel?.let {
        val f = jogador.familiaridade(it).fator
        when {
            f >= .94f -> faixa.cor
            f >= .85f -> Alerta
            else -> Erro
        }
    } ?: faixa.cor

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(tamanho)
                .clip(RoundedCornerShape(tamanho * 0.22f))
                .background(faixa.gradiente)
                .border(1.5.dp, corMoldura, RoundedCornerShape(tamanho * 0.22f))
                .then(
                    if (onClicar != null) Modifier.clickable(onClick = onClicar)
                    else Modifier
                ),
        ) {
            if (jogador.urlFoto != null) {
                AsyncImage(
                    model = jogador.urlFoto,
                    contentDescription = jogador.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(tamanho * 0.22f)),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        jogador.nome.split(" ").let { partes ->
                            if (partes.size > 1) "${partes.first().first()}${partes.last().first()}"
                            else partes.first().take(2)
                        }.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black.copy(alpha = .6f),
                    )
                }
            }

            // Selo do overall, ancorado no canto como nas cartas.
            Box(
                Modifier.align(Alignment.TopStart)
                    .background(
                        Color.Black.copy(alpha = .72f),
                        RoundedCornerShape(bottomEnd = tamanho * 0.2f),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("$overall", style = EstiloCartaOverall, color = faixa.cor)
            }

            papel?.let {
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .background(
                            Color.Black.copy(alpha = .72f),
                            RoundedCornerShape(topStart = tamanho * 0.2f),
                        )
                        .padding(horizontal = 4.dp),
                ) {
                    Text(it.sigla, style = EstiloRotulo, color = Texto, fontSize = 8.sp)
                }
            }
        }

        if (mostrarNome) {
            Text(
                jogador.nome.split(" ").last(),
                style = MaterialTheme.typography.labelSmall,
                color = TextoMedio, maxLines = 1, fontSize = 9.sp,
                modifier = Modifier.padding(top = 3.dp).width(tamanho + 8.dp),
            )
        }
    }
}

/** Rótulo de seção com uma linha fina do lado. Estrutura sem peso. */
@Composable
fun Secao(titulo: String, modifier: Modifier = Modifier, acao: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo.uppercase(), style = EstiloRotulo, color = Destaque)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(TextoFraco.copy(alpha = .22f))
        )
        acao?.let { Spacer(Modifier.width(10.dp)); it() }
    }
}

/** Cartão com número grande. A unidade base do painel. */
@Composable
fun CartaoNumero(
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    cor: Color = Texto,
    complemento: String? = null,
) {
    Surface(
        modifier, shape = RoundedCornerShape(14.dp), color = SuperficieAlta,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(rotulo.uppercase(), style = EstiloRotulo, color = TextoFraco)
            Spacer(Modifier.height(6.dp))
            Text(valor, style = EstiloNumero, color = cor)
            complemento?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = TextoFraco)
            }
        }
    }
}

/** Barra de atributo com o número na ponta. */
@Composable
fun BarraAtributo(nome: String, valor: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nome, Modifier.width(118.dp),
            style = MaterialTheme.typography.bodySmall, color = TextoMedio)
        Box(
            Modifier.weight(1f).height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TextoFraco.copy(alpha = .18f)),
        ) {
            Box(
                Modifier.fillMaxWidth(valor / 100f).fillMaxHeight()
                    .background(corPorValor(valor))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$valor", Modifier.width(26.dp),
            style = EstiloNumeroPequeno, color = Texto, fontSize = 13.sp)
    }
}

/** Comparativo entre dois times, com a barra dividida ao meio. */
@Composable
fun BarraComparativa(
    rotulo: String,
    esquerda: Int,
    direita: Int,
    modifier: Modifier = Modifier,
    sufixo: String = "",
) {
    val total = (esquerda + direita).coerceAtLeast(1)
    Column(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$esquerda$sufixo", Modifier.width(46.dp),
                style = EstiloNumeroPequeno, color = Destaque, fontSize = 13.sp)
            Text(rotulo, Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = TextoFraco)
            Text("$direita$sufixo", style = EstiloNumeroPequeno,
                color = Erro, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        ) {
            Box(Modifier.weight(esquerda.toFloat().coerceAtLeast(0.01f))
                .fillMaxHeight().background(Destaque))
            Box(Modifier.weight(direita.toFloat().coerceAtLeast(0.01f))
                .fillMaxHeight().background(Erro.copy(alpha = .7f)))
        }
    }
}

/** Sequência de resultados: V E D coloridos, como nos apps de placar. */
@Composable
fun Forma(resultados: List<Char>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        resultados.takeLast(5).forEach { r ->
            val cor = when (r) {
                'V' -> Destaque
                'E' -> Alerta
                else -> Erro
            }
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                    .background(cor.copy(alpha = .22f))
                    .border(1.dp, cor.copy(alpha = .55f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$r", style = EstiloRotulo, color = cor, fontSize = 10.sp)
            }
        }
        if (resultados.isEmpty()) {
            Text("Sem jogos ainda", style = MaterialTheme.typography.labelSmall,
                color = TextoFraco)
        }
    }
}

/** Etiqueta pequena para categorizar. */
@Composable
fun Selo(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(cor.copy(alpha = .18f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(texto.uppercase(), style = EstiloRotulo, color = cor, fontSize = 9.sp)
    }
}

/** Item de lista com carta, texto e valor à direita. */
@Composable
fun LinhaElenco(
    jogador: Jogador,
    detalhe: String,
    valorDireita: String,
    corValor: Color = Texto,
    papel: Papel? = null,
    corDetalhe: Color = TextoFraco,
    onClicar: (() -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth().then(
            if (onClicar != null) Modifier.clickable(onClick = onClicar)
            else Modifier
        ),
        shape = RoundedCornerShape(12.dp),
        color = SuperficieAlta,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CartaJogador(jogador, 44.dp, papel)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(jogador.nome, maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium, color = Texto)
                Text(detalhe, style = MaterialTheme.typography.labelSmall,
                    color = corDetalhe, maxLines = 1)
            }
            Text(valorDireita, style = EstiloNumeroPequeno, color = corValor)
        }
    }
}

fun formatarEuro(v: Long): String = when {
    v >= 1_000_000_000 -> "€%.2fB".format(v / 1_000_000_000.0)
    v >= 1_000_000 -> "€%.1fM".format(v / 1_000_000.0)
    v >= 1_000 -> "€%.0fK".format(v / 1_000.0)
    else -> "€$v"
}
