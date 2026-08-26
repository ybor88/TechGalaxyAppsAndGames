package com.example.turbobrothers.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TurboColorScheme = darkColorScheme(
    primary = TurboGold,
    onPrimary = TurboNavy,
    primaryContainer = TurboNavyLight,
    onPrimaryContainer = TurboGold,
    secondary = TurboOrange,
    onSecondary = TurboNavy,
    tertiary = LelioBlue,
    onTertiary = Color.White,
    background = TurboNavy,
    onBackground = TurboCream,
    surface = TurboNavyLight,
    onSurface = TurboCream
)

@Composable
fun TurboBrothersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TurboColorScheme,
        typography = Typography,
        content = content
    )
}
