package com.scouttable.app.ui.bestclub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.theme.ScoutGradient

@Composable
fun BestClubScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val clubs by repository.observeBestClubs(sport).collectAsState(initial = emptyList())
    val players by repository.observePlayers(sport).collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf<String?>(null) }

    val maxCount = clubs.maxOfOrNull { it.count } ?: 1

    Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
        Text(
            "Classifica per carriera migliore / club",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (clubs.isEmpty()) {
            Text(
                "Nessun dato. Importa una lista per vedere la classifica dei club.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(clubs, key = { it.club }) { clubCount ->
                    val fraction = clubCount.count.toFloat() / maxCount.toFloat()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                expanded = if (expanded == clubCount.club) null else clubCount.club
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    clubCount.club.ifBlank { "(non specificato)" },
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text("${clubCount.count} giocatori", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ScoutGradient),
                                )
                            }

                            if (expanded == clubCount.club) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    players.filter { it.carrieraMigliore == clubCount.club }.forEach { p ->
                                        Text(
                                            "• ${p.nome} (${p.nazione})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
