// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Colori {
    val Navy = Color(0xFF0B2A66)
    val Blu = Color(0xFF1F5EEA)
    val BluChiaro = Color(0xFFE9F0FE)
    val Rosso = Color(0xFFD9364A)
    val RossoChiaro = Color(0xFFFDEBEE)
    val Verde = Color(0xFF1E9E57)
    val VerdeChiaro = Color(0xFFE6F6EC)
    val Viola = Color(0xFF7357F0)
    val Arancio = Color(0xFFE5820F)
    val ArancioChiaro = Color(0xFFFFF3E2)
    val Sfondo = Color(0xFFF4F7FC)
    val Card = Color(0xFFFFFFFF)
    val Testo = Color(0xFF16203A)
    val TestoSecondario = Color(0xFF68728C)
    val Bordo = Color(0xFFE3E8F2)
    val Traccia = Color(0xFFE8ECF4)
}

private val schema = lightColorScheme(
    primary = Colori.Blu,
    onPrimary = Color.White,
    primaryContainer = Colori.BluChiaro,
    onPrimaryContainer = Colori.Navy,
    secondary = Colori.Navy,
    background = Colori.Sfondo,
    onBackground = Colori.Testo,
    surface = Colori.Card,
    onSurface = Colori.Testo,
    surfaceVariant = Colori.Sfondo,
    onSurfaceVariant = Colori.TestoSecondario,
    outline = Colori.Bordo,
    error = Colori.Rosso,
)

@Composable
fun TemaPayRecap(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schema, content = content)
}
