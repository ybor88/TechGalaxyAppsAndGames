package com.romanopetroli.rpfidelity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RpColorScheme = lightColorScheme(
    primary = RpOrange,
    onPrimary = Color.White,
    primaryContainer = RpGold,
    onPrimaryContainer = RpNavyDark,
    secondary = RpNavy,
    onSecondary = Color.White,
    tertiary = RpGold,
    onTertiary = RpNavyDark,
    background = RpGrayBg,
    onBackground = RpTextDark,
    surface = Color.White,
    onSurface = RpTextDark,
    error = RpErrorText,
    onError = Color.White
)

@Composable
fun RPFidelityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RpColorScheme,
        typography = Typography,
        content = content
    )
}
