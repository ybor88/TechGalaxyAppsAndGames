package com.example.playerbase.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import java.io.File

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    val context = LocalContext.current
                    val logoStore = remember { TeamLogoStore(context) }
                    val logoHistory = remember(team) { logoStore.getLogoHistory(team) }

                    // Stemma/i sempre sulla riga sopra, mai accanto alla barra: così
                    // un mazzetto di più stemmi ha tutta la larghezza per sé e non
                    // viene mai tagliato per far posto alla barra, anche con un solo logo.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TeamBadge(teamName = team, accent = accentColor, logos = logoHistory, size = 34.dp)
                        Spacer(modifier = Modifier.width(if (logoHistory.size > 1) 18.dp else 10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                team,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (logoHistory.size > 1) {
                                Text(
                                    "${logoHistory.size} stemmi nel tempo",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
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
            }
        }
    }
}

/** Stemma squadra: badge singolo, oppure — se la squadra ha avuto più stemmi
 * diversi nel tempo — un mazzetto impilato con tutti (i più recenti sopra e
 * più in vista), invece di sceglierne uno arbitrariamente. */
@Composable
private fun TeamBadge(teamName: String, accent: Color, logos: List<File>, size: Dp) {
    if (logos.size > 1) {
        StackedTeamBadge(teamName = teamName, logos = logos, size = size)
    } else {
        SingleTeamBadge(teamName = teamName, logoFile = logos.firstOrNull(), accent = accent, size = size)
    }
}

@Composable
private fun SingleTeamBadge(teamName: String, logoFile: File?, accent: Color, size: Dp) {
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

@Composable
private fun StackedTeamBadge(teamName: String, logos: List<File>, size: Dp) {
    val shown = logos.take(3)
    val extra = logos.size - shown.size
    val badgeSize = size * 0.8f
    val step = size * 0.32f

    Box(
        modifier = Modifier
            .height(size)
            .width(size + step * (shown.size - 1)),
        contentAlignment = Alignment.CenterStart
    ) {
        // Disegnati dal più vecchio al più recente: l'ultimo caricato finisce
        // sopra gli altri e più in evidenza, gli stemmi precedenti fanno capolino dietro.
        shown.asReversed().forEachIndexed { orderFromOldest, file ->
            AsyncImage(
                model = file,
                contentDescription = "Logo $teamName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = step * orderFromOldest)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize * 0.55f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+$extra",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
