package com.exemplo.fmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Fundo = Color(0xFF0B1416)
val Superficie = Color(0xFF0D1B1E)
val SuperficieAlta = Color(0xFF122326)
val Destaque = Color(0xFF4FD1C5)
val Texto = Color(0xFFE8E3D9)
val TextoFraco = Color(0xFF7C9296)
val Alerta = Color(0xFFF2A65A)
val Erro = Color(0xFFE05C5C)

private val esquema = darkColorScheme(
    primary = Destaque,
    onPrimary = Superficie,
    secondary = Alerta,
    background = Fundo,
    onBackground = Texto,
    surface = Superficie,
    onSurface = Texto,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TextoFraco,
    error = Erro,
)

@Composable
fun TemaFManager(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, content = conteudo)
}
