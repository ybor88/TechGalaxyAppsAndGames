package com.opticpro.suite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LogoBlack,
    onPrimary = LogoWhite,
    secondary = LogoGray,
    background = LogoWhite,
    onBackground = LogoBlack,
    surface = LogoWhite,
    onSurface = LogoBlack,
    surfaceVariant = Color(0xFFE8E8E8),
)

private val DarkColors = darkColorScheme(
    primary = LogoWhite,
    onPrimary = LogoBlack,
    secondary = LogoGray,
    background = LogoBlack,
    onBackground = LogoWhite,
    surface = LogoSurface,
    onSurface = LogoWhite,
    surfaceVariant = LogoSurfaceVariant,
    onSurfaceVariant = LogoGray,
)

// Stile monocromatico bianco su nero ispirato al logo dell'app: tema scuro di default.
@Composable
fun OpticProSuiteTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
