package com.example.playerbase.ui.theme

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

@Composable
fun PlayerBaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val navy = BrandColors.navy
    val gold = BrandColors.gold
    val basket = BrandColors.basket

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = gold,
            onPrimary = navy.darken(0.35f),
            primaryContainer = navy.lighten(0.25f),
            onPrimaryContainer = gold,
            secondary = basket,
            onSecondary = Color.White,
            tertiary = SoccerWhite,
            onTertiary = SoccerBlack,
            background = navy.darken(0.35f),
            onBackground = SoccerWhite,
            surface = PlayerSurfaceDark,
            onSurface = SoccerWhite
        )

        else -> lightColorScheme(
            primary = navy,
            onPrimary = Color.White,
            primaryContainer = gold,
            onPrimaryContainer = navy.darken(0.35f),
            secondary = basket,
            onSecondary = Color.White,
            tertiary = gold,
            onTertiary = navy.darken(0.35f),
            background = SoccerWhite,
            onBackground = navy.darken(0.35f),
            surface = PlayerSurfaceLight,
            onSurface = navy
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
