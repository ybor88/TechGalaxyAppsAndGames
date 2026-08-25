package com.example.playerbase.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.playerbase.data.PlayerPhotoStore
import com.example.playerbase.data.Sport
import com.example.playerbase.ui.theme.accentColor

/**
 * Immagine del giocatore: se è stata caricata una foto profilo la mostra
 * (ritagliata a cerchio); altrimenti un segnaposto con il colore dello sport
 * e l'iniziale del cognome, finché non viene caricata una foto vera.
 */
@Composable
fun PlayerPortrait(
    playerId: String,
    sport: Sport,
    modifier: Modifier = Modifier,
    initials: String = "",
    photoVersion: Int = 0
) {
    val context = LocalContext.current
    val photoStore = remember { PlayerPhotoStore(context) }
    val photoFile = remember(playerId, photoVersion) { photoStore.getPhotoFile(playerId) }

    if (photoFile != null) {
        AsyncImage(
            model = photoFile,
            contentDescription = "Foto giocatore",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .aspectRatio(1f)
                .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.DarkGray)
                .border(1.dp, Color(0x1F000000), CircleShape)
                .fillMaxSize()
        )
    } else {
        val accent = sport.accentColor()
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(lighten(accent, 0.25f), darken(accent, 0.25f)),
                        center = Offset(0.3f, 0.25f)
                    )
                )
                .border(1.dp, Color(0x1F000000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (initials.isNotBlank()) {
                Text(
                    initials.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                Text(sport.emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

private fun lighten(color: Color, amount: Float): Color = Color(
    red = (color.red + (1f - color.red) * amount).coerceIn(0f, 1f),
    green = (color.green + (1f - color.green) * amount).coerceIn(0f, 1f),
    blue = (color.blue + (1f - color.blue) * amount).coerceIn(0f, 1f),
    alpha = color.alpha
)

private fun darken(color: Color, amount: Float): Color = Color(
    red = (color.red * (1f - amount)).coerceIn(0f, 1f),
    green = (color.green * (1f - amount)).coerceIn(0f, 1f),
    blue = (color.blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = color.alpha
)
