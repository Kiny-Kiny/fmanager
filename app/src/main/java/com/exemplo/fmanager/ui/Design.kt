package com.exemplo.fmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/*
 * SISTEMA DE DESIGN.
 *
 * A direção é a de uma prancheta de técnico à noite: fundo quase preto
 * com um verde-piscina frio, e o número em destaque sempre pesado. App de
 * esporte vive de número grande — a hierarquia é construída por contraste
 * de peso e espaçamento, não por cor.
 *
 * Sem fonte customizada de propósito: arquivo de fonte pesa no APK e o
 * contraste de peso já dá a personalidade. O que faz a diferença é o
 * espaçamento negativo nos números grandes e o positivo nos rótulos
 * pequenos em caixa alta.
 */

// ------------------------------------------------------------- CORES

val Fundo = Color(0xFF07100F)
val Superficie = Color(0xFF0C1817)
val SuperficieAlta = Color(0xFF132523)
val SuperficieTopo = Color(0xFF1B302E)

val Destaque = Color(0xFF3DDC97)
val DestaqueEscuro = Color(0xFF1F8A63)
val Alerta = Color(0xFFF2A65A)
val Erro = Color(0xFFE0555C)
val Info = Color(0xFF5AA9E6)

val Texto = Color(0xFFEDEAE3)
val TextoMedio = Color(0xFFA3B5B2)
val TextoFraco = Color(0xFF6B8380)

val Bronze = Color(0xFFB4703A)
val Prata = Color(0xFFA8B0B5)
val Ouro = Color(0xFFD9B65C)
val Especial = Color(0xFF2E2A3B)

// -------------------------------------------------------- GRADIENTES

val GradienteHero = Brush.verticalGradient(
    listOf(Color(0xFF1B302E), Color(0xFF0C1817)),
)

fun gradienteClube(cor: Color) = Brush.linearGradient(
    listOf(cor.copy(alpha = .32f), Superficie),
)

/**
 * Identidade visual do clube.
 *
 * Não existe cor real no dataset, então derivo do nome: o mesmo clube
 * sempre recebe a mesma cor, e times diferentes ficam distinguíveis.
 * É um hash estável mapeado numa paleta curada — nenhuma cor sai feia
 * porque nenhuma é sorteada livremente.
 */
private val PALETA_CLUBES = listOf(
    Color(0xFF3DDC97), Color(0xFF5AA9E6), Color(0xFFE0555C),
    Color(0xFFF2A65A), Color(0xFFB48EF2), Color(0xFF4ECDC4),
    Color(0xFFE86AA6), Color(0xFF7FB069), Color(0xFFD9B65C),
    Color(0xFF6C8EBF), Color(0xFFC97B84), Color(0xFF5DC8A8),
)

fun corDoClube(nome: String): Color =
    PALETA_CLUBES[(nome.hashCode().absoluteValue) % PALETA_CLUBES.size]

/** Faixa da carta pelo overall, no espírito das cartas de coleção. */
enum class Faixa(val cor: Color, val rotulo: String) {
    BRONZE(Bronze, "Bronze"),
    PRATA(Prata, "Prata"),
    OURO(Ouro, "Ouro"),
    ESPECIAL(Destaque, "Elite");

    val gradiente: Brush
        get() = Brush.verticalGradient(
            listOf(cor.copy(alpha = .85f), cor.copy(alpha = .35f)),
        )
}

fun faixaDe(overall: Int): Faixa = when {
    overall >= 84 -> Faixa.ESPECIAL
    overall >= 75 -> Faixa.OURO
    overall >= 65 -> Faixa.PRATA
    else -> Faixa.BRONZE
}

/** Verde, laranja ou vermelho conforme o valor de 0 a 100. */
fun corPorValor(v: Int): Color = when {
    v >= 78 -> Destaque
    v >= 62 -> Alerta
    else -> Erro
}

// ------------------------------------------------------- TIPOGRAFIA

/** Número gigante de placar. O espaçamento negativo aperta os dígitos. */
val EstiloPlacar = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontSize = 42.sp,
    lineHeight = 44.sp,
    letterSpacing = (-2).sp,
)

/** Número de destaque em cartão: overall, posição, pontos. */
val EstiloNumero = TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 28.sp,
    lineHeight = 30.sp,
    letterSpacing = (-1.2).sp,
)

val EstiloNumeroPequeno = TextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = 17.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.5).sp,
)

/** Rótulo de seção. Caixa alta com espaçamento aberto. */
val EstiloRotulo = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.4.sp,
)

val EstiloTituloTela = TextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.8).sp,
)

val EstiloCartaOverall = TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 13.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.4).sp,
    textAlign = TextAlign.Center,
)

private val tipografia = Typography(
    displayLarge = EstiloPlacar,
    headlineMedium = EstiloTituloTela,
    headlineSmall = TextStyle(
        fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
        lineHeight = 25.sp, letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = EstiloRotulo,
)

private val esquema = darkColorScheme(
    primary = Destaque,
    onPrimary = Color(0xFF04100C),
    primaryContainer = DestaqueEscuro,
    secondary = Alerta,
    background = Fundo,
    onBackground = Texto,
    surface = Superficie,
    onSurface = Texto,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoMedio,
    outline = TextoFraco,
    error = Erro,
)

@Composable
fun TemaFManager(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, typography = tipografia, content = conteudo)
}
