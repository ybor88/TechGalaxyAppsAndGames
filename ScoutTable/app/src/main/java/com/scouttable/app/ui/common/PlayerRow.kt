package com.scouttable.app.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.ui.theme.ScoutGreen

@Composable
fun PlayerRow(player: Player, sport: Sport, onClick: () -> Unit = {}) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(name = player.nome, logoPath = player.logoPath, size = 48.dp)

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(player.nome, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                val flag = flagEmojiFor(player.nazione)
                Text(
                    "${player.anno} · ${if (flag.isNotBlank()) "$flag " else ""}${player.nazione}" +
                        if (player.ruolo.isNotBlank()) " · ${player.ruolo}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    player.carrieraMigliore.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoutGreen,
                )
                Text(
                    player.stato,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (player.presenze > 0 || player.punteggio > 0 || player.assist > 0) {
                    val puntiLabel = if (sport == Sport.BASKET) "punti" else "gol"
                    // L'assist non è tracciato dall'infobox Wikipedia per il calcio: mostrarlo
                    // per quello sport significherebbe sempre "0 assist", fuorviante.
                    val assistPart = if (sport == Sport.BASKET) " · ${player.assist} assist" else ""
                    val presenzePart = if (player.presenze > 0) "${player.presenze} presenze · " else ""
                    Text(
                        "$presenzePart${player.punteggio} $puntiLabel$assistPart" +
                            if (player.competizione.isNotBlank()) " · ${player.competizione}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = {
                val query = Uri.encode("${player.nome} ${sport.label} highlights")
                val url = "https://www.google.com/search?q=$query&tbm=vid"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) {
                Icon(Icons.Default.PlayCircle, contentDescription = "Visiona", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
