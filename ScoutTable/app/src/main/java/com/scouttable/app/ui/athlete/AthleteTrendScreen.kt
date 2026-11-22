// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.theme.ScoutGreen
import com.scouttable.app.ui.trend.TrendPieCard
import com.scouttable.app.ui.trend.colorize
import com.scouttable.app.ui.trend.decadeSlices
import kotlin.math.abs

/**
 * Andamento degli sport individuali: per ogni atleta il distacco del suo rapporto (punti per gara
 * o % vittorie) dalla media del gruppo, positivo = freccia su, negativo = giù, con un grafico a
 * barre divergenti; poi le torte per decennio di nascita, nazione e scuderia/categoria.
 */
@Composable
fun AthleteTrendScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val all by repository.observePlayers(sport).collectAsState(initial = emptyList())
    var female by rememberSaveable(sport) { mutableStateOf(false) }
    val group = remember(all, female) { AthleteScoring.sameGroup(all, sport, female).filter(AthleteScoring::hasStats) }
    val rows = remember(group, all) {
        group.mapNotNull { p -> AthleteScoring.distacco(p, all)?.let { p to it } }.sortedByDescending { it.second }
    }
    val average = remember(group) { AthleteScoring.averageRatio(group) }
    val metric = AthleteScoring.ratioMetric(sport)
    val maxAbs = rows.maxOfOrNull { abs(it.second) }?.takeIf { it > 0 } ?: 1.0

    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Andamento per freccia", style = MaterialTheme.typography.titleMedium)
        Text(
            "Freccia su se ${metric.label.lowercase()} dell'atleta supera la media" +
                " del suo settore (maschile o femminile)" + ", giù se è sotto: il numero è il distacco (positivo o negativo).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        GenderSelector(female, { female = it })
        if (rows.size < 2) {
            Text("Servono almeno due ${AthleteScoring.athleteWord(sport, true)} con statistiche.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val up = rows.count { it.second > 0 }
        val down = rows.count { it.second < 0 }
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Media ${metric.label.lowercase()}", style = MaterialTheme.typography.labelMedium)
                    Text(AthleteScoring.format(metric, average), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrendArrow(1)
                        Text(" $up (${up * 100 / rows.size}%)", color = ScoutGreen, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrendArrow(-1)
                        Text(" $down (${down * 100 / rows.size}%)", color = DownRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Barre divergenti: a destra (verde) sopra la media, a sinistra (rosso) sotto.
        rows.forEach { (p, d) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpenPlayer(p.id) }.padding(vertical = 3.dp),
            ) {
                TrendArrow(if (d > 0) 1 else if (d < 0) -1 else 0, size = 14.dp)
                Text(
                    p.nome,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp).padding(start = 4.dp),
                )
                Row(modifier = Modifier.weight(1f).height(14.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        if (d < 0) Bar(fraction = (abs(d) / maxAbs).toFloat(), color = DownRed)
                    }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        if (d > 0) Bar(fraction = (d / maxAbs).toFloat(), color = ScoutGreen)
                    }
                }
                Text(
                    AthleteScoring.formatDistacco(sport, d),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (d >= 0) ScoutGreen else DownRed,
                    modifier = Modifier.width(78.dp).padding(start = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.size(24.dp))
        val withYear = group.filter { it.anno > 0 }
        if (withYear.isNotEmpty()) {
            TrendPieCard("Per decennio di nascita", decadeSlices(withYear))
            Spacer(modifier = Modifier.size(20.dp))
        }
        TrendPieCard("Per nazione", countSlices(group) { it.nazione.ifBlank { "Non indicata" } })
        Spacer(modifier = Modifier.size(20.dp))
        TrendPieCard(
            when {
                sport.isMotor -> "Per scuderia principale"
                sport.isTeamSport -> "Per squadra principale"
                else -> "Per categoria"
            },
            countSlices(group) { it.carrieraMigliore.ifBlank { "Non indicata" } },
        )
    }
}

@Composable
private fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/** Fette per valore di [key], le 7 più numerose più "Altre". */
private fun countSlices(players: List<Player>, key: (Player) -> String) =
    players.groupingBy(key).eachCount().entries.sortedByDescending { it.value }.let { sorted ->
        val top = sorted.take(7).map { it.key to it.value }
        val rest = sorted.drop(7).sumOf { it.value }
        colorize(if (rest > 0) top + ("Altre" to rest) else top)
    }
