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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
            if (player.logoPath.isNullOrBlank()) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    model = player.logoPath,
                    contentDescription = player.nome,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(player.nome, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${player.anno} · ${player.nazione}" + if (player.ruolo.isNotBlank()) " · ${player.ruolo}" else "",
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
                if (player.presenze > 0) {
                    val puntiLabel = if (sport == Sport.BASKET) "punti" else "gol"
                    Text(
                        "${player.presenze} presenze · ${player.punteggio} $puntiLabel · ${player.assist} assist" +
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
