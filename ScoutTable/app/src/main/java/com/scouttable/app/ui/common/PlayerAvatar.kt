package com.scouttable.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlin.math.abs

/**
 * Foto/stemma del giocatore se disponibile; altrimenti (o finché non è caricata, o se il
 * caricamento fallisce) un avatar con gradiente + iniziali, cosi' la riga non è mai senza
 * immagine nemmeno per un istante. Il colore del gradiente è deterministico sul nome, cosi' lo
 * stesso giocatore ha sempre lo stesso avatar. Usa SubcomposeAsyncImage (Coil) proprio perché,
 * a differenza di AsyncImage, permette di definire cosa mostrare durante il caricamento e in
 * caso di errore invece di lasciare un cerchio vuoto.
 */
@Composable
fun PlayerAvatar(name: String, logoPath: String?, size: Dp, modifier: Modifier = Modifier) {
    if (logoPath.isNullOrBlank()) {
        GradientInitials(name, size, modifier)
    } else {
        SubcomposeAsyncImage(
            model = logoPath,
            contentDescription = name,
            modifier = modifier.size(size).clip(CircleShape),
            loading = { GradientInitials(name, size) },
            error = { GradientInitials(name, size) },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
private fun GradientInitials(name: String, size: Dp, modifier: Modifier = Modifier) {
    val (start, end) = gradientFor(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.4).sp,
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private val gradientPalette = listOf(
    Color(0xFF2E7DFF) to Color(0xFF29D19A),
    Color(0xFF7B5CFF) to Color(0xFF2ED1D1),
    Color(0xFFFF6B6B) to Color(0xFFFFA36B),
    Color(0xFF29B6F6) to Color(0xFF66BB6A),
    Color(0xFFEC407A) to Color(0xFF7E57C2),
    Color(0xFFFFA726) to Color(0xFFEF5350),
)

private fun gradientFor(name: String): Pair<Color, Color> =
    gradientPalette[abs(name.hashCode()) % gradientPalette.size]
