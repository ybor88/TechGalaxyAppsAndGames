package com.scouttable.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Il logo usa una card "squircle" molto arrotondata: la riprendiamo per card e bottoni.
val ScoutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Gradiente blu -> verde del wordmark/tabella del logo, per bottoni e header primari.
val ScoutGradient = Brush.linearGradient(listOf(ScoutBlue, ScoutGreen))

private val LightColors = lightColorScheme(
    primary = ScoutBlue,
    onPrimary = ScoutWhite,
    secondary = ScoutGreen,
    onSecondary = ScoutNavy,
    background = ScoutWhite,
    onBackground = ScoutNavy,
    surface = ScoutWhite,
    onSurface = ScoutNavy,
    surfaceVariant = Color(0xFFE4E9F2),
)

private val DarkColors = darkColorScheme(
    primary = ScoutBlue,
    onPrimary = ScoutWhite,
    secondary = ScoutGreen,
    onSecondary = ScoutNavy,
    background = ScoutNavy,
    onBackground = ScoutWhite,
    surface = ScoutSurface,
    onSurface = ScoutWhite,
    surfaceVariant = ScoutSurfaceVariant,
    onSurfaceVariant = ScoutGray,
)

// Tema scuro di default, coerente con lo sfondo blu notte del logo.
@Composable
fun ScoutTableTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = ScoutShapes, content = content)
}
