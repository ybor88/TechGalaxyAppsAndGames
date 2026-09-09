package com.example.sentinelai.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SentinelDarkColorScheme = darkColorScheme(
    primary = SentinelBlue,
    onPrimary = SentinelText,
    primaryContainer = SentinelPanel2,
    onPrimaryContainer = SentinelTeal,
    secondary = SentinelTeal,
    onSecondary = SentinelBgSidebar,
    tertiary = SentinelPurple,
    onTertiary = SentinelText,
    background = SentinelBg,
    onBackground = SentinelText,
    surface = SentinelPanel,
    onSurface = SentinelText,
    surfaceVariant = SentinelPanel2,
    onSurfaceVariant = SentinelTextDim,
    outline = SentinelPanelBorder,
    error = SentinelDanger,
    onError = SentinelText
)

@Composable
fun SentinelAITheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = SentinelDarkColorScheme,
        typography = Typography,
        content = content
    )
}
