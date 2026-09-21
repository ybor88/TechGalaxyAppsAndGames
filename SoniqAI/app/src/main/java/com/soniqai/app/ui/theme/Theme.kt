// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

val SoniqShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Gradiente ciano -> magenta -> arancio della nota musicale del logo: usato per header, bottoni
// primari e la card del ritornello (sezione più "energica" del brano).
val SoniqGradient = Brush.linearGradient(listOf(SoniqCyan, SoniqPurple, SoniqMagenta, SoniqOrange))
val SoniqGradientSubtle = Brush.linearGradient(listOf(SoniqPurple, SoniqMagenta))

private val DarkColors = darkColorScheme(
    primary = SoniqCyan,
    onPrimary = SoniqNavy,
    secondary = SoniqMagenta,
    onSecondary = SoniqWhite,
    tertiary = SoniqOrange,
    background = SoniqNavy,
    onBackground = SoniqWhite,
    surface = SoniqSurface,
    onSurface = SoniqWhite,
    surfaceVariant = SoniqSurfaceVariant,
    onSurfaceVariant = SoniqGray,
)

@Composable
fun SoniqAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, shapes = SoniqShapes, content = content)
}
