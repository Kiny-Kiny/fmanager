package com.exemplo.fmanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.sistemas.Arquetipo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * RADAR DE ATRIBUTOS — ideia do FplDataCard.
 *
 * Aquele projeto monta "data cards" visuais em vez de tabelas de números.
 * A diferença prática é grande: uma tabela de 29 atributos obriga a ler
 * tudo para formar uma impressão; um radar entrega o PERFIL do jogador de
 * relance — dá para ver na hora se é um atacante de área ou um ponta.
 *
 * Uso os seis arquétipos como eixos em vez dos 29 atributos. Radar com
 * muitos eixos vira uma bolha ilegível; com seis, cada ponta significa
 * algo e a forma inteira é reconhecível.
 */

@Composable
fun RadarDoJogador(
    jogador: Jogador,
    modifier: Modifier = Modifier,
    comparar: Jogador? = null,
) {
    val eixos = Arquetipo.entries.filter { it != Arquetipo.GOLEIRO }
    val valores = eixos.map { it.notaDe(jogador) }
    val comparacao = comparar?.let { c -> eixos.map { it.notaDe(c) } }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(28.dp)) {
            val centro = Offset(size.width / 2, size.height / 2)
            val raio = size.minDimension / 2

            // Teia de fundo: quatro anéis de referência.
            repeat(4) { anel ->
                val r = raio * (anel + 1) / 4f
                desenharPoligono(
                    centro, r, eixos.size,
                    cor = TextoFraco.copy(alpha = if (anel == 3) .35f else .16f),
                    preenchido = false,
                )
            }

            // Raios dos eixos.
            eixos.indices.forEach { i ->
                val a = anguloDe(i, eixos.size)
                drawLine(
                    TextoFraco.copy(alpha = .16f),
                    centro,
                    Offset(centro.x + cos(a) * raio, centro.y + sin(a) * raio),
                    strokeWidth = 1f,
                )
            }

            // Comparação primeiro, para ficar por baixo.
            comparacao?.let { outros ->
                desenharPerfil(centro, raio, outros, Alerta, alpha = .18f)
            }
            desenharPerfil(centro, raio, valores, Destaque, alpha = .30f)
        }

        // Rótulos com os números, que o radar sozinho não dá.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            eixos.forEachIndexed { i, arq ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(arq.rotulo.take(4).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextoFraco, fontSize = 8.sp)
                    Text("${valores[i]}",
                        style = MaterialTheme.typography.labelMedium,
                        color = corPorValor(valores[i]), fontSize = 12.sp)
                }
            }
        }
    }
}

private fun anguloDe(indice: Int, total: Int): Float =
    // Começa no topo: -90 graus.
    (-PI / 2 + 2 * PI * indice / total).toFloat()

private fun DrawScope.desenharPoligono(
    centro: Offset,
    raio: Float,
    lados: Int,
    cor: Color,
    preenchido: Boolean,
) {
    val caminho = Path()
    repeat(lados) { i ->
        val a = anguloDe(i, lados)
        val p = Offset(centro.x + cos(a) * raio, centro.y + sin(a) * raio)
        if (i == 0) caminho.moveTo(p.x, p.y) else caminho.lineTo(p.x, p.y)
    }
    caminho.close()
    if (preenchido) drawPath(caminho, cor)
    else drawPath(caminho, cor, style = Stroke(width = 1f))
}

private fun DrawScope.desenharPerfil(
    centro: Offset,
    raio: Float,
    valores: List<Int>,
    cor: Color,
    alpha: Float,
) {
    val caminho = Path()
    valores.forEachIndexed { i, v ->
        val a = anguloDe(i, valores.size)
        val r = raio * (v / 100f).coerceIn(0.04f, 1f)
        val p = Offset(centro.x + cos(a) * r, centro.y + sin(a) * r)
        if (i == 0) caminho.moveTo(p.x, p.y) else caminho.lineTo(p.x, p.y)
    }
    caminho.close()

    drawPath(caminho, cor.copy(alpha = alpha))
    drawPath(caminho, cor, style = Stroke(width = 2f))

    // Pontos nos vértices, para ler o valor exato de cada eixo.
    valores.forEachIndexed { i, v ->
        val a = anguloDe(i, valores.size)
        val r = raio * (v / 100f).coerceIn(0.04f, 1f)
        drawCircle(cor, 3f, Offset(centro.x + cos(a) * r, centro.y + sin(a) * r))
    }
}

/** Barra de tendência para o desenvolvimento: verde sobe, vermelho cai. */
@Composable
fun BarraVariacao(
    nome: String,
    antes: Int,
    agora: Int,
    modifier: Modifier = Modifier,
) {
    val delta = agora - antes
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nome, Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall, color = TextoMedio)

        Canvas(Modifier.weight(1f).height(8.dp)) {
            val largura = size.width
            val y = size.height / 2
            drawLine(TextoFraco.copy(alpha = .18f),
                Offset(0f, y), Offset(largura, y), strokeWidth = size.height)

            val xAntes = largura * (antes / 100f)
            val xAgora = largura * (agora / 100f)
            val cor = when {
                delta > 0 -> Destaque
                delta < 0 -> Erro
                else -> TextoFraco
            }
            drawLine(cor, Offset(minOf(xAntes, xAgora), y),
                Offset(maxOf(xAntes, xAgora), y), strokeWidth = size.height)
            drawCircle(Texto, size.height * 0.7f, Offset(xAgora, y))
        }

        Spacer(Modifier.width(10.dp))
        Text(
            if (delta == 0) "$agora" else "$agora ${if (delta > 0) "+$delta" else "$delta"}",
            Modifier.width(58.dp),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                delta > 0 -> Destaque
                delta < 0 -> Erro
                else -> TextoMedio
            },
            fontSize = 11.sp,
        )
    }
}
