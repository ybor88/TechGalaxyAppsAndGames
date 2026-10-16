package com.matchmind.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Stile MatchMind ricavato dal logo: fondo blu-notte, verde neon del profilo e del grafico,
 * arancione del pallone da basket, bianco del pallone da calcio.
 *
 * © Roberto Di Flumeri
 */
object Colori {
    val Sfondo = Color(0xFF11181F)
    val Superficie = Color(0xFF1A242C)
    val SuperficieAlta = Color(0xFF223039)
    val Bordo = Color(0xFF2C3A44)
    val Verde = Color(0xFF4CE070)
    val VerdeScuro = Color(0xFF2FA84F)
    val Arancio = Color(0xFFF2842C)
    val Rosso = Color(0xFFF0524F)
    val Testo = Color(0xFFF2F5F7)
    val Tenue = Color(0xFF9AA8B2)
    val Grigio = Color(0xFF5E6E79)
}

private val schema = darkColorScheme(
    primary = Colori.Verde,
    onPrimary = Color(0xFF07210F),
    primaryContainer = Colori.VerdeScuro,
    onPrimaryContainer = Color.White,
    secondary = Colori.Arancio,
    onSecondary = Color(0xFF2A1203),
    tertiary = Colori.Arancio,
    background = Colori.Sfondo,
    onBackground = Colori.Testo,
    surface = Colori.Superficie,
    onSurface = Colori.Testo,
    surfaceVariant = Colori.SuperficieAlta,
    onSurfaceVariant = Colori.Tenue,
    surfaceContainer = Colori.Superficie,
    surfaceContainerHigh = Colori.SuperficieAlta,
    surfaceContainerHighest = Colori.SuperficieAlta,
    outline = Colori.Bordo,
    outlineVariant = Colori.Bordo,
    error = Colori.Rosso,
)

private val tipografia = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

@Composable
fun TemaMatchMind(contenuto: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schema, typography = tipografia, content = contenuto)
}
