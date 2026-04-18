package com.example.frigozero.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FrigoIceBlue,
    onPrimary = FrigoNavyDark,
    primaryContainer = FrigoNavy,
    onPrimaryContainer = FrigoIceBlue,
    secondary = FrigoIceBlueDark,
    onSecondary = FrigoNavyDark,
    tertiary = FrigoAccentGreen,
    onTertiary = Color.White,
    background = FrigoDarkBg,
    onBackground = FrigoColdWhite,
    surface = FrigoDarkSurface,
    onSurface = FrigoColdWhite
)

private val LightColorScheme = lightColorScheme(
    primary = FrigoNavy,
    onPrimary = Color.White,
    primaryContainer = FrigoIceBlue,
    onPrimaryContainer = FrigoNavy,
    secondary = FrigoIceBlueDark,
    onSecondary = FrigoNavyDark,
    tertiary = FrigoAccentGreen,
    onTertiary = Color.White,
    background = FrigoColdWhite,
    onBackground = FrigoNavyDark,
    surface = Color.White,
    onSurface = FrigoNavy
)

@Composable
fun FrigoZeroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}