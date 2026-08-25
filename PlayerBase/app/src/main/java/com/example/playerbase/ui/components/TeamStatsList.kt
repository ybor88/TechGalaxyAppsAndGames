package com.example.playerbase.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.playerbase.data.TeamLogoStore

/**
 * Classifica squadre per numero di giocatori: stemma (se caricato) o iniziale
 * colorata, nome squadra, barra proporzionale e conteggio — al posto delle
 * sole etichette testuali sotto un grafico a barre.
 */
@Composable
fun TeamStatsList(teams: List<Pair<String, Int>>, accentColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (teams.isEmpty()) {
            Text(
                "Nessun dato disponibile",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        } else {
            val maxValue = teams.maxOf { it.second }.coerceAtLeast(1)
            teams.forEach { (team, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    TeamBadge(teamName = team, accent = accentColor, size = 34.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            team,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(count.toFloat() / maxValue)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(accentColor)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TeamBadge(teamName: String, accent: Color, size: Dp) {
    val context = LocalContext.current
    val logoStore = remember { TeamLogoStore(context) }
    val logoFile = remember(teamName) { logoStore.getLogoFile(teamName) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (logoFile != null) Color.White else accent.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        if (logoFile != null) {
            AsyncImage(
                model = logoFile,
                contentDescription = "Logo $teamName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
        } else {
            Text(
                teamName.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
        }
    }
}
