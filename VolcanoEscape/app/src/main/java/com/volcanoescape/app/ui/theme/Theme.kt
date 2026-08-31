package com.volcanoescape.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LavaOrange,
    onPrimary = Color.White,
    primaryContainer = SunsetAmber,
    onPrimaryContainer = CharcoalGrey,
    secondary = DeepNavy,
    onSecondary = Color.White,
    secondaryContainer = SkyBlue,
    onSecondaryContainer = Color.White,
    tertiary = EruptionRed,
    onTertiary = Color.White,
    tertiaryContainer = EmberYellow,
    onTertiaryContainer = CharcoalGrey,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFFCE8D8),
    onSurfaceVariant = CharcoalGrey,
    onBackground = CharcoalGrey,
    onSurface = CharcoalGrey,
)

private val DarkColors = darkColorScheme(
    primary = LavaOrange,
    onPrimary = SurfaceDark,
    primaryContainer = Color(0xFF7A3A12),
    onPrimaryContainer = SunsetAmber,
    secondary = SkyBlue,
    onSecondary = SurfaceDark,
    secondaryContainer = DeepNavy,
    onSecondaryContainer = SkyBlue,
    tertiary = EmberYellow,
    onTertiary = SurfaceDark,
    tertiaryContainer = Color(0xFF7A1F1F),
    onTertiaryContainer = EmberYellow,
    background = SurfaceDark,
    surface = CharcoalGrey,
    onBackground = SurfaceLight,
    onSurface = SurfaceLight,
)

@Composable
fun VolcanoEscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VolcanoEscapeTypography,
        content = content,
    )
}
