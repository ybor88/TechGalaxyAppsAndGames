// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteMetric
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.labelFor
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.theme.ScoutGreen

private val Turf = Color(0xFF2E7D32)
private val TurfDark = Color(0xFF276B2A)
private val Ice = Color(0xFFEAF4FB)
private val LineWhite = Color(0xFFF5F5F5)
private val HockeyRed = Color(0xFFD32F2F)
private val HockeyBlue = Color(0xFF1565C0)

/**
 * Righe della formazione, dalla prima linea all'ultima: il migliore in classifica va nella prima
 * casella, poi gli altri riga per riga (le posizioni sono indicative, i ruoli non vengono imposti).
 * - Football: 11 in campo (linea 5, 3 dietro, 3 in fondo);
 * - Rugby: i 15 (prima linea, seconda linea, terza linea, mediani, centri, ali ed estremo);
 * - Hockey: 6 (tre attaccanti, due difensori, il portiere).
 */
private fun formation(sport: Sport): List<Int> = when (sport) {
    Sport.FOOTBALL -> listOf(5, 3, 3)
    Sport.RUGBY -> listOf(3, 2, 3, 2, 2, 3)
    else -> listOf(3, 2, 1)
}

private fun fieldTitle(sport: Sport): String = when (sport) {
    Sport.FOOTBALL -> "Il campo da football"
    Sport.RUGBY -> "Il campo da rugby"
    else -> "La pista"
}

/**
 * "Formazione" di Football americano, Rugby e Hockey: i migliori della lista per la classifica scelta
 * schierati sul campo disegnato (campo con le yard, campo da rugby con le linee dei 22 o pista di
 * ghiaccio con linee blu e cerchi d'ingaggio); gli altri in panchina in ordine di classifica.
 */
@Composable
fun TeamFieldScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val loaded by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val metrics = AthleteMetric.forSport(sport).take(4)
    var metricName by rememberSaveable(sport) { mutableStateOf(metrics.first().name) }
    val metric = AthleteMetric.valueOf(metricName)
    var female by rememberSaveable(sport) { mutableStateOf(false) }
    val ranked = remember(loaded, metric, female) { AthleteScoring.ranking(AthleteScoring.sameGroup(loaded.orEmpty(), sport, female), metric) }
    val rows = formation(sport)
    val onField = ranked.take(rows.sum())
    val bench = ranked.drop(rows.sum())

    Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(fieldTitle(sport), style = MaterialTheme.typography.titleMedium)
        Text(
            "In campo i ${rows.sum()} migliori per la classifica scelta, il 1° in prima linea; gli altri in panchina. " +
                "Tocca un giocatore per aprirlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            metrics.forEach { m ->
                FilterChip(
                    selected = m == metric,
                    onClick = { metricName = m.name },
                    label = { Text(m.labelFor(sport)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        GenderSelector(female, { female = it })
        when {
            loaded == null -> LoadingBar(label = "Caricamento campo…")
            onField.isEmpty() -> Text(
                "Nessun ${AthleteScoring.athleteWord(sport)} con statistiche: usa \"Genera\" con i link Wikipedia.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Box {
                Pitch(sport, onField, rows, metric, onOpenPlayer)
                com.scouttable.app.ui.common.LeagueBadges(sport, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        if (bench.isNotEmpty()) {
            Text("In panchina", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            bench.forEachIndexed { i, p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPlayer(p.id) }.padding(vertical = 4.dp),
                ) {
                    Text("${onField.size + i + 1}°", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    PlayerAvatar(name = p.nome, logoPath = p.logoPath, size = 28.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(p.nome)
                        if (p.ruolo.isNotBlank()) {
                            Text(p.ruolo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(AthleteScoring.formatValue(p, metric), color = ScoutGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "Fonte: Wikipedia · © Roberto Di Flumeri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
private fun Pitch(sport: Sport, players: List<Player>, rows: List<Int>, metric: AthleteMetric, onOpenPlayer: (String) -> Unit) {
    val onIce = sport == Sport.HOCKEY
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (onIce) 40.dp else 16.dp))
            .drawBehind {
                when (sport) {
                    Sport.FOOTBALL -> drawFootballField()
                    Sport.RUGBY -> drawRugbyPitch()
                    else -> drawRink()
                }
            }
            .padding(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var index = 0
        rows.forEach { count ->
            val line = players.drop(index).take(count)
            val start = index
            index += count
            if (line.isEmpty()) return@forEach
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                line.forEachIndexed { i, p ->
                    FieldSlot(start + i + 1, p, metric, textColor = if (onIce) Color(0xFF1A1A1A) else LineWhite) { onOpenPlayer(p.id) }
                }
            }
        }
    }
}

@Composable
private fun FieldSlot(position: Int, p: Player, metric: AthleteMetric, textColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).clickable(onClick = onClick),
    ) {
        Box {
            PlayerAvatar(name = p.nome, logoPath = p.logoPath, size = 44.dp)
            Text(
                "$position",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.6f)) }
                    .padding(horizontal = 4.dp),
            )
        }
        Text(
            p.nome.substringAfterLast(' ').ifBlank { p.nome },
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${flagEmojiFor(p.nazione)} ${AthleteScoring.formatValue(p, metric)}".trim(),
            color = if (textColor == LineWhite) Color(0xFFB9F6CA) else ScoutGreen,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** Campo da football visto dall'alto: erba a strisce, end zone colorate, linee ogni 10 yard. */
private fun DrawScope.drawFootballField() {
    drawRect(Turf)
    val endZone = size.height * 0.08f
    val playH = size.height - 2 * endZone
    // Strisce dell'erba ogni 10 yard.
    for (i in 0 until 10) {
        if (i % 2 == 1) drawRect(TurfDark, topLeft = Offset(0f, endZone + playH * i / 10), size = Size(size.width, playH / 10))
    }
    drawRect(Color(0xFF1B5E20), topLeft = Offset.Zero, size = Size(size.width, endZone))
    drawRect(Color(0xFF1B5E20), topLeft = Offset(0f, size.height - endZone), size = Size(size.width, endZone))
    val stroke = 2.dp.toPx()
    for (i in 0..10) {
        val y = endZone + playH * i / 10
        drawLine(LineWhite.copy(alpha = if (i == 0 || i == 10) 1f else 0.7f), Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        // Hash mark al centro di ogni yard line.
        if (i in 1..9) {
            drawLine(LineWhite, Offset(size.width * 0.38f, y), Offset(size.width * 0.42f, y), strokeWidth = stroke)
            drawLine(LineWhite, Offset(size.width * 0.58f, y), Offset(size.width * 0.62f, y), strokeWidth = stroke)
        }
    }
    drawRect(LineWhite, style = Stroke(width = 3.dp.toPx()))
}

/** Campo da rugby: linee di meta, dei 22 metri, di metà campo (tratteggiate quelle dei 10 metri) e i pali ad H. */
private fun DrawScope.drawRugbyPitch() {
    drawRect(Turf)
    val inGoal = size.height * 0.07f
    val playH = size.height - 2 * inGoal
    for (i in 0 until 8) {
        if (i % 2 == 1) drawRect(TurfDark, topLeft = Offset(0f, inGoal + playH * i / 8), size = Size(size.width, playH / 8))
    }
    val stroke = 2.dp.toPx()
    fun line(fraction: Float, dashed: Boolean = false) {
        val y = inGoal + playH * fraction
        if (!dashed) {
            drawLine(LineWhite, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        } else {
            var x = 0f
            val dash = 10.dp.toPx()
            while (x < size.width) {
                drawLine(LineWhite, Offset(x, y), Offset(minOf(x + dash, size.width), y), strokeWidth = stroke)
                x += dash * 2
            }
        }
    }
    line(0f); line(1f)
    line(0.22f); line(0.78f)
    line(0.5f)
    line(0.4f, dashed = true); line(0.6f, dashed = true)
    // Pali ad H sulle linee di meta.
    val postGap = size.width * 0.1f
    val cx = size.width / 2
    listOf(inGoal to -1f, size.height - inGoal to 1f).forEach { (y, dir) ->
        drawLine(LineWhite, Offset(cx - postGap / 2, y), Offset(cx - postGap / 2, y + dir * inGoal * 0.8f), strokeWidth = 3.dp.toPx())
        drawLine(LineWhite, Offset(cx + postGap / 2, y), Offset(cx + postGap / 2, y + dir * inGoal * 0.8f), strokeWidth = 3.dp.toPx())
        drawLine(LineWhite, Offset(cx - postGap / 2, y + dir * inGoal * 0.3f), Offset(cx + postGap / 2, y + dir * inGoal * 0.3f), strokeWidth = 3.dp.toPx())
    }
    drawRect(LineWhite, style = Stroke(width = 3.dp.toPx()))
}

/** Pista da hockey: ghiaccio, linea rossa centrale, linee blu, cerchi d'ingaggio, porte e aree del portiere. */
private fun DrawScope.drawRink() {
    val corner = CornerRadius(40.dp.toPx())
    drawRoundRect(Ice, cornerRadius = corner)
    val stroke = 3.dp.toPx()
    val h = size.height
    val w = size.width
    drawLine(HockeyRed, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = stroke * 1.5f)
    drawLine(HockeyBlue, Offset(0f, h * 0.33f), Offset(w, h * 0.33f), strokeWidth = stroke * 1.5f)
    drawLine(HockeyBlue, Offset(0f, h * 0.67f), Offset(w, h * 0.67f), strokeWidth = stroke * 1.5f)
    // Linee di porta.
    drawLine(HockeyRed, Offset(w * 0.06f, h * 0.07f), Offset(w * 0.94f, h * 0.07f), strokeWidth = stroke * 0.6f)
    drawLine(HockeyRed, Offset(w * 0.06f, h * 0.93f), Offset(w * 0.94f, h * 0.93f), strokeWidth = stroke * 0.6f)
    // Cerchio centrale e cerchi d'ingaggio.
    val r = w * 0.12f
    drawCircle(HockeyBlue, radius = r, center = Offset(w / 2, h / 2), style = Stroke(width = stroke * 0.7f))
    listOf(0.17f, 0.83f).forEach { fy ->
        listOf(0.25f, 0.75f).forEach { fx ->
            drawCircle(HockeyRed, radius = r, center = Offset(w * fx, h * fy), style = Stroke(width = stroke * 0.7f))
            drawCircle(HockeyRed, radius = 4.dp.toPx(), center = Offset(w * fx, h * fy))
        }
    }
    // Aree del portiere (semicerchi azzurri) e porte.
    val crease = w * 0.08f
    drawArc(Color(0xFF90CAF9), 0f, 180f, true, topLeft = Offset(w / 2 - crease, h * 0.07f - crease), size = Size(crease * 2, crease * 2))
    drawArc(Color(0xFF90CAF9), 180f, 180f, true, topLeft = Offset(w / 2 - crease, h * 0.93f - crease), size = Size(crease * 2, crease * 2))
    val goalW = w * 0.1f
    drawRect(HockeyRed, topLeft = Offset(w / 2 - goalW / 2, h * 0.07f - 8.dp.toPx()), size = Size(goalW, 8.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
    drawRect(HockeyRed, topLeft = Offset(w / 2 - goalW / 2, h * 0.93f), size = Size(goalW, 8.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
    drawRoundRect(Color(0xFFB0BEC5), cornerRadius = corner, style = Stroke(width = 4.dp.toPx()))
}
