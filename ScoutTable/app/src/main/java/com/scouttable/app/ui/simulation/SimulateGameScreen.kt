// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.simulation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.scouttable.app.data.Sport
import com.scouttable.app.data.simulation.MatchSimulator
import com.scouttable.app.data.simulation.SimEvent
import com.scouttable.app.data.simulation.SimPlayer
import com.scouttable.app.data.simulation.SimPlayerLine
import com.scouttable.app.data.simulation.SimResult
import com.scouttable.app.data.simulation.SimTeam
import com.scouttable.app.data.simulation.TeamSources
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.drawBasketCourt
import com.scouttable.app.ui.common.drawSoccerPitch
import com.scouttable.app.ui.common.fieldAspectRatio
import com.scouttable.app.ui.theme.ScoutGreen
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Durata reale dell'animazione di tutta la partita. */
private enum class SimSpeed(val label: String, val millis: Long) {
    VELOCE("Veloce · 15s", 15_000),
    NORMALE("Normale · 40s", 40_000),
    LENTA("Lenta · 90s", 90_000),
}

private val LedRed = Color(0xFFFF3D00)
private val LedGreen = Color(0xFF39FF14)
private val LedAmber = Color(0xFFFFB300)

/**
 * Simula partita (basket e calcio): due link squadra, uno per squadra, con le statistiche di ogni
 * giocatore; ogni giocatore sfida tutti i giocatori della squadra avversaria (vedi
 * [MatchSimulator]) e alla fine c'è il punteggio, con i duelli vinti da ciascuno. Durante la partita: tabellone LED con punteggio,
 * periodo e cronometro, barra di avanzamento della partita, campo 2D con le casacche (cerchi del
 * colore scelto per ogni squadra) e lo stemma della squadra di casa a centrocampo, cronaca degli
 * ultimi canestri/gol. L'utente sceglie chi gioca in casa, i colori, la velocità, e può rigiocare.
 */
@Composable
fun SimulateGameScreen(sport: Sport, padding: PaddingValues) {
    val basket = sport.isBasket
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlA by rememberSaveable(sport) { mutableStateOf("") }
    var urlB by rememberSaveable(sport) { mutableStateOf("") }
    var teamA by remember(sport) { mutableStateOf<SimTeam?>(null) }
    var teamB by remember(sport) { mutableStateOf<SimTeam?>(null) }
    var loading by remember(sport) { mutableStateOf(false) }
    var error by remember(sport) { mutableStateOf<String?>(null) }
    var homeIsA by rememberSaveable(sport) { mutableStateOf(true) }
    var colorA by remember(sport) { mutableStateOf(JerseyPalette[0]) }
    var colorB by remember(sport) { mutableStateOf(JerseyPalette[5]) }
    var speed by rememberSaveable(sport) { mutableStateOf(SimSpeed.NORMALE) }
    var result by remember(sport) { mutableStateOf<SimResult?>(null) }
    var elapsed by remember(sport) { mutableFloatStateOf(0f) }
    var playToken by remember(sport) { mutableIntStateOf(0) }
    var skip by remember(sport) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // Posizione del tabellone nella colonna: all'avvio della partita la schermata ci scorre sopra.
    var boardY by remember(sport) { mutableIntStateOf(0) }
    // Squadra (1 o 2) la cui pagina FBref va aperta per la verifica anti-bot a mano.
    var verifyTarget by remember(sport) { mutableStateOf<Int?>(null) }

    val home = if (homeIsA) teamA else teamB
    val away = if (homeIsA) teamB else teamA
    val homeColor = if (homeIsA) colorA else colorB
    val awayColor = distinctAwayColor(homeColor, if (homeIsA) colorB else colorA)

    fun isValid(url: String) = if (basket) TeamSources.isBasketUrl(url) else TeamSources.isSoccerUrl(url)

    suspend fun applyColor(target: Int, team: SimTeam?) {
        val c = team?.let { dominantLogoColor(context, it.logoUrl) } ?: return
        if (target == 1) colorA = c else colorB = c
    }

    // Squadre mancanti dopo il caricamento: errore, e per FBref la verifica anti-bot a mano.
    fun afterLoad() {
        error = listOfNotNull(
            if (teamA == null) "Squadra 1 non letta (link non valido, pagina senza statistiche o verifica anti-bot)." else null,
            if (teamB == null) "Squadra 2 non letta (link non valido, pagina senza statistiche o verifica anti-bot)." else null,
        ).joinToString(" ").ifBlank { null }
        if (!basket) verifyTarget = when {
            teamA == null && TeamSources.isFbrefUrl(urlA.trim()) -> 1
            teamB == null && TeamSources.isFbrefUrl(urlB.trim()) -> 2
            else -> null
        }
        scope.launch { applyColor(1, teamA); applyColor(2, teamB) }
    }

    fun onVerified(target: Int, html: String) {
        verifyTarget = null
        loading = true
        scope.launch {
            val url = (if (target == 1) urlA else urlB).trim()
            val team = TeamSources.parseHtml(url, html)
            if (target == 1) teamA = team else teamB = team
            // Con il cookie della verifica l'altra squadra di solito si legge senza chiedere di nuovo.
            if (teamA == null && target != 1) teamA = TeamSources.fetch(urlA.trim())
            if (teamB == null && target != 2) teamB = TeamSources.fetch(urlB.trim())
            loading = false
            afterLoad()
        }
    }

    fun load() {
        val a = urlA.trim()
        val b = urlB.trim()
        if (!isValid(a) || !isValid(b)) {
            error = if (basket) "Servono due link squadra di Proballers o Basketball-Reference." else "Servono due link squadra di Transfermarkt o FBref."
            return
        }
        loading = true
        error = null
        result = null
        scope.launch {
            // In sequenza: due WebView in parallelo rallentano la verifica anti-bot di FBref.
            val ta = TeamSources.fetch(a)
            val tb = TeamSources.fetch(b)
            loading = false
            teamA = ta
            teamB = tb
            afterLoad()
        }
    }

    fun play() {
        val h = home ?: return
        val a = away ?: return
        val seed = System.nanoTime()
        result = if (basket) MatchSimulator.simulateBasket(h, a, seed) else MatchSimulator.simulateSoccer(h, a, seed)
        elapsed = 0f
        skip = false
        playToken++
    }

    // Animazione: il tempo di gioco avanza in proporzione al tempo reale scelto con la velocità.
    LaunchedEffect(playToken, skip) {
        val r = result ?: return@LaunchedEffect
        if (skip) { elapsed = r.totalSeconds.toFloat(); return@LaunchedEffect }
        if (elapsed == 0f) launch { scrollState.animateScrollTo(boardY) }
        val start = withFrameMillis { it }
        val startElapsed = elapsed
        val remainingFraction = 1f - startElapsed / r.totalSeconds
        val duration = (speed.millis * remainingFraction).coerceAtLeast(1f)
        while (elapsed < r.totalSeconds) {
            val now = withFrameMillis { it }
            val f = ((now - start) / duration).coerceIn(0f, 1f)
            elapsed = startElapsed + (r.totalSeconds - startElapsed) * f
            if (f >= 1f) break
        }
    }

    verifyTarget?.let { target ->
        VerifyPageDialog(
            url = (if (target == 1) urlA else urlB).trim(),
            onHtml = { html -> onVerified(target, html) },
            onDismiss = { verifyTarget = null },
        )
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text("Simula partita", style = MaterialTheme.typography.titleMedium)
        Text(
            (if (basket) "Incolla il link di due squadre (Proballers o Basketball-Reference)" else "Incolla il link di due squadre (Transfermarkt, consigliato, o FBref)") +
                ": ogni giocatore sfida tutti i giocatori dell'altra squadra confrontando le statistiche, e alla fine " +
                "c'è il risultato con i duelli vinti da ciascuno.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val example = if (basket) "https://www.proballers.com/it/pallacanestro/squadra/101/boston-celtics"
        else "https://www.transfermarkt.it/atalanta-bergamo/startseite/verein/800"
        OutlinedTextField(
            value = urlA,
            onValueChange = { urlA = it },
            label = { Text("Link squadra 1") },
            placeholder = { Text(example, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = urlB,
            onValueChange = { urlB = it },
            label = { Text("Link squadra 2") },
            placeholder = {
                Text(if (basket) "https://www.basketball-reference.com/teams/CHI/2025.html" else "https://fbref.com/en/squads/922493f3/Atalanta-Stats", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            enabled = !loading && urlA.isNotBlank() && urlB.isNotBlank(),
            onClick = { load() },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Carica squadre") }
        if (loading) LoadingBar(label = "Lettura delle statistiche delle due squadre…", modifier = Modifier.padding(top = 12.dp))
        error?.let { Text(it, color = Color(0xFFE05C5C), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }

        val ta = teamA
        val tb = teamB
        if (ta != null && tb != null) {
            Text("Chi gioca in casa? Tocca un colore per cambiare la casacca.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            TeamSetupCard(ta, isHome = homeIsA, color = colorA, onHome = { homeIsA = true }, onColor = { colorA = it })
            TeamSetupCard(tb, isHome = !homeIsA, color = colorB, onHome = { homeIsA = false }, onColor = { colorB = it })
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SimSpeed.entries.forEach { s ->
                    FilterChip(selected = speed == s, onClick = { speed = s }, label = { Text(s.label, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Button(onClick = { play() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (result == null) "Simula partita" else "Rigioca")
            }
        }

        val r = result
        if (r != null && home != null && away != null) {
            val finished = elapsed >= r.totalSeconds
            val shown = r.events.filter { it.second <= elapsed }
            val homeScore = shown.filter { it.team == 0 }.sumOf { it.points }
            val awayScore = shown.filter { it.team == 1 }.sumOf { it.points }
            Spacer(modifier = Modifier.height(16.dp).onGloballyPositioned { boardY = it.positionInParent().y.toInt() })
            LedScoreboard(
                homeName = home.name,
                awayName = away.name,
                homeScore = homeScore,
                awayScore = awayScore,
                period = periodLabel(r, elapsed, basket, finished),
                clock = clockLabel(r, elapsed, basket, finished),
                homeColor = homeColor,
                awayColor = awayColor,
            )
            val progress = (elapsed / r.totalSeconds).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = LedAmber,
                    trackColor = Color(0xFF2A2A2A),
                    modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            SimField(basket, r, elapsed, home, homeColor, awayColor)
            if (!finished) {
                OutlinedButton(onClick = { skip = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vai al risultato finale")
                }
            }
            EventFeed(shown.takeLast(6).reversed(), r, basket, homeColor, awayColor)
            if (finished) {
                FinalSummary(r, home, away, basket, homeColor, awayColor)
                OutlinedButton(onClick = { play() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rigioca la partita")
                }
            }
        }
        Text(
            (if (basket) "Fonti: proballers.com, basketball-reference.com" else "Fonti: transfermarkt, fbref.com") + " · © Roberto Di Flumeri",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun TeamSetupCard(team: SimTeam, isHome: Boolean, color: Color, onHome: () -> Unit, onColor: (Color) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isHome) BorderStroke(2.dp, ScoutGreen) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onHome)) {
                PlayerAvatar(name = team.name, logoPath = team.logoUrl, size = 44.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(team.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val top = team.players.maxByOrNull { it.points + it.goals }
                    Text(
                        "${team.players.size} giocatori" + (top?.let { " · miglior realizzatore: ${it.name}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RadioButton(selected = isHome, onClick = onHome)
                Text(if (isHome) "Casa" else "Ospite", style = MaterialTheme.typography.labelMedium)
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JerseyPalette.forEach { c ->
                    val selected = colorDistance(c, color) < 0.02f
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (selected) 3.dp else 1.dp, if (selected) ScoutGreen else Color.Gray, CircleShape)
                            .clickable { onColor(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = contentColorFor(c), modifier = Modifier.size(14.dp))
                    }
                }
                if (JerseyPalette.none { colorDistance(it, color) < 0.02f }) {
                    // Colore ricavato dallo stemma, non presente nella tavolozza.
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(color).border(3.dp, ScoutGreen, CircleShape))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tabellone

private fun ledStyle(color: Color, size: Int) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    color = color,
    shadow = Shadow(color = color.copy(alpha = 0.9f), blurRadius = 18f),
)

/** Cifre a LED con gli "8" spenti dietro (stesso numero di cifre), come un tabellone da palazzetto. */
@Composable
private fun LedDigits(value: String, ghost: String, color: Color, size: Int) {
    Box(contentAlignment = Alignment.CenterEnd) {
        Text(ghost, style = ledStyle(color, size).copy(color = color.copy(alpha = 0.10f), shadow = null))
        Text(value, style = ledStyle(color, size))
    }
}

@Composable
private fun LedScoreboard(
    homeName: String,
    awayName: String,
    homeScore: Int,
    awayScore: Int,
    period: String,
    clock: String,
    homeColor: Color,
    awayColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF050505))
            .border(3.dp, Color(0xFF3A3A3A), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamTag(homeName, homeColor, "CASA", Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(period, style = ledStyle(LedAmber, 14))
                LedDigits(clock, clock.map { if (it.isDigit()) '8' else it }.joinToString(""), LedGreen, 20)
            }
            TeamTag(awayName, awayColor, "OSPITE", Modifier.weight(1f), alignEnd = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { LedDigits("$homeScore", "8".repeat("$homeScore".length), LedRed, 44) }
            Text(":", style = ledStyle(LedRed, 32))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { LedDigits("$awayScore", "8".repeat("$awayScore".length), LedRed, 44) }
        }
    }
}

@Composable
private fun TeamTag(name: String, color: Color, role: String, modifier: Modifier, alignEnd: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!alignEnd) Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color).border(1.dp, Color.White, CircleShape))
            Text(
                role,
                style = ledStyle(LedAmber, 10),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (alignEnd) Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color).border(1.dp, Color.White, CircleShape))
        }
        Text(
            name.uppercase(),
            style = ledStyle(Color.White, 12).copy(shadow = null),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}

private fun periodLabel(r: SimResult, elapsed: Float, basket: Boolean, finished: Boolean): String {
    if (finished) return if (r.overtimes > 0) "FINALE d.t.s." else "FINALE"
    val regulation = r.periodSeconds * r.regulationPeriods
    if (elapsed >= regulation) return "OT${((elapsed - regulation) / (5 * 60)).toInt() + 1}"
    val n = (elapsed / r.periodSeconds).toInt() + 1
    return if (basket) "Q$n" else "${n}° TEMPO"
}

private fun clockLabel(r: SimResult, elapsed: Float, basket: Boolean, finished: Boolean): String {
    if (!basket) {
        val minute = if (finished) 90 else (elapsed / 60).toInt() + 1
        return "%02d'".format(minute.coerceAtMost(90))
    }
    if (finished) return "00:00"
    val regulation = r.periodSeconds * r.regulationPeriods
    val period = if (elapsed >= regulation) 5 * 60 else r.periodSeconds
    val inPeriod = if (elapsed >= regulation) (elapsed - regulation) % period else elapsed % period
    val left = (period - inPeriod).toInt().coerceAtLeast(0)
    return "%02d:%02d".format(left / 60, left % 60)
}

// ---------------------------------------------------------------- campo 2D

/** Posizioni (0..1 nel campo verticale) della squadra che attacca il canestro/la porta in alto. */
private val basketOffense = listOf(Offset(0.5f, 0.40f), Offset(0.18f, 0.28f), Offset(0.82f, 0.28f), Offset(0.30f, 0.13f), Offset(0.64f, 0.10f))
private val basketHoopTop = Offset(0.5f, 0.085f)

private fun basketDefense(spot: Offset) = Offset(spot.x + (basketHoopTop.x - spot.x) * 0.3f + 0.03f, spot.y + (basketHoopTop.y - spot.y) * 0.3f)

// 4-4-2 della squadra di casa (metà campo in basso, attacca verso l'alto).
private val soccerHome = listOf(
    Offset(0.5f, 0.95f),
    Offset(0.15f, 0.80f), Offset(0.38f, 0.83f), Offset(0.62f, 0.83f), Offset(0.85f, 0.80f),
    Offset(0.15f, 0.66f), Offset(0.38f, 0.68f), Offset(0.62f, 0.68f), Offset(0.85f, 0.66f),
    Offset(0.38f, 0.56f), Offset(0.62f, 0.56f),
)

private fun mirror(o: Offset) = Offset(1f - o.x, 1f - o.y)

/** Ordina gli undici per reparto (portiere, difensori, centrocampisti, attaccanti) come nel 4-4-2. */
private fun soccerSlots(lineup: List<SimPlayer>): List<SimPlayer> {
    if (lineup.isEmpty()) return lineup
    fun group(p: SimPlayer) = when (p.position.uppercase().take(2)) {
        "GK" -> 0; "DF" -> 1; "MF" -> 2; "FW" -> 3; else -> 2
    }
    val gk = lineup.firstOrNull { group(it) == 0 } ?: lineup.first()
    val rest = lineup.filter { it !== gk }.toMutableList()
    val out = mutableListOf(gk)
    // Reparto per reparto; se mancano giocatori di quel ruolo si completa con chi resta.
    for ((g, count) in listOf(1 to 4, 2 to 4, 3 to 2)) {
        val picked = rest.filter { group(it) == g }.take(count).toMutableList()
        rest.removeAll { p -> picked.any { it === p } }
        while (picked.size < count && rest.isNotEmpty()) picked += rest.removeAt(0)
        out += picked
    }
    return out
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Composable
private fun SimField(basket: Boolean, r: SimResult, elapsed: Float, home: SimTeam, homeColor: Color, awayColor: Color) {
    val next = r.events.firstOrNull { it.second > elapsed }
    val last = r.events.lastOrNull { it.second <= elapsed }
    // Squadra in attacco: quella che segnerà il prossimo canestro/gol.
    val attackHome by animateFloatAsState(if ((next ?: last)?.team == 0) 1f else 0f, tween(600), label = "possesso")
    val measurer = rememberTextMeasurer()
    val homeLine = if (basket) r.homeLineup else soccerSlots(r.homeLineup)
    val awayLine = if (basket) r.awayLineup else soccerSlots(r.awayLineup)

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(fieldAspectRatio(basket)).clip(RoundedCornerShape(16.dp))) {
        Canvas(modifier = Modifier.fillMaxSize()) { if (basket) drawBasketCourt() else drawSoccerPitch() }
        // Stemma della squadra di casa a centrocampo.
        AsyncImage(
            model = home.logoUrl,
            contentDescription = "Stemma ${home.name}",
            modifier = Modifier.align(Alignment.Center).size(if (basket) 64.dp else 72.dp).alpha(0.9f),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = elapsed / 6f
            val radius = size.width * (if (basket) 0.045f else 0.035f)
            fun jitter(i: Int) = Offset(0.012f * sin(t + i * 1.7f), 0.010f * sin(t * 1.3f + i))
            // Posizioni di casa/ospite interpolate tra "casa attacca" e "ospite attacca".
            fun basketPos(team: Int, i: Int): Offset {
                val spot = basketOffense[i % 5]
                val homeAttacking = if (team == 0) spot else basketDefense(spot)
                val awayAttacking = if (team == 1) mirror(spot) else mirror(basketDefense(spot))
                return Offset(
                    awayAttacking.x + (homeAttacking.x - awayAttacking.x) * attackHome,
                    awayAttacking.y + (homeAttacking.y - awayAttacking.y) * attackHome,
                ) + jitter(i + team * 5)
            }
            fun soccerPos(team: Int, i: Int): Offset {
                val base = if (team == 0) soccerHome[i % 11] else mirror(soccerHome[i % 11])
                if (i == 0) return base
                // Tutta la squadra sale di qualche metro quando attacca.
                val push = 0.07f * (if (team == 0) attackHome else 1f - attackHome) * (if (team == 0) -1f else 1f)
                return Offset(base.x, base.y + push) + jitter(i + team * 11)
            }
            fun pos(team: Int, i: Int) = if (basket) basketPos(team, i) else soccerPos(team, i)
            fun px(o: Offset) = Offset(o.x * size.width, o.y * size.height)

            listOf(0 to homeLine, 1 to awayLine).forEach { (team, line) ->
                val jersey = if (team == 0) homeColor else awayColor
                line.forEachIndexed { i, p ->
                    val c = px(pos(team, i))
                    val scorer = last?.let { it.player == p.name && elapsed - it.second < 25 } == true
                    if (scorer) drawCircle(LedAmber.copy(alpha = 0.55f), radius * 1.6f, c)
                    drawCircle(jersey, radius, c)
                    drawCircle(Color.White, radius, c, style = Stroke(width = 2.dp.toPx()))
                    val label = measurer.measure(initials(p.name), TextStyle(color = contentColorFor(jersey), fontSize = (radius / 2.2f).toSp(), fontWeight = FontWeight.Bold))
                    drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
                }
            }
            // Palla: col prossimo marcatore, che nell'ultimo tratto prima del canestro/gol la porta
            // verso il canestro/la porta attaccata.
            if (next != null) {
                val line = if (next.team == 0) homeLine else awayLine
                val idx = line.indexOfFirst { it.name == next.player }.let { if (it < 0) 0 else it }
                val holder = px(pos(next.team, idx))
                val target = px(if (next.team == 0) (if (basket) basketHoopTop else Offset(0.5f, 0.02f)) else mirror(if (basket) basketHoopTop else Offset(0.5f, 0.02f)))
                val prev = last?.second ?: 0
                val span = (next.second - prev).coerceAtLeast(1)
                val phase = ((elapsed - prev) / span).coerceIn(0f, 1f)
                val shot = ((phase - 0.8f) / 0.2f).coerceIn(0f, 1f)
                val ball = Offset(holder.x + (target.x - holder.x) * shot + radius * 0.8f * (1 - shot), holder.y + (target.y - holder.y) * shot)
                drawCircle(if (basket) Color(0xFFFF6D00) else Color.White, radius * 0.45f, ball)
                drawCircle(Color.Black, radius * 0.45f, ball, style = Stroke(width = 1.dp.toPx()))
            }
        }
    }
}

// ---------------------------------------------------------------- cronaca e riepilogo

private fun eventTime(e: SimEvent, r: SimResult, basket: Boolean): String {
    if (!basket) return "${(e.second / 60 + 1).coerceAtMost(90)}'"
    val regulation = r.periodSeconds * r.regulationPeriods
    return if (e.second >= regulation) "OT${(e.second - regulation) / 300 + 1}"
    else "Q${e.second / r.periodSeconds + 1}"
}

@Composable
private fun EventFeed(events: List<SimEvent>, r: SimResult, basket: Boolean, homeColor: Color, awayColor: Color) {
    if (events.isEmpty()) return
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text("Cronaca", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        events.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (e.team == 0) homeColor else awayColor).border(1.dp, Color.White, CircleShape))
                Text(eventTime(e, r, basket), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp).padding(start = 6.dp))
                Text(
                    (if (basket) "+${e.points} " else "⚽ ") + e.player + (e.assist?.let { " (assist $it)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FinalSummary(r: SimResult, home: SimTeam, away: SimTeam, basket: Boolean, homeColor: Color, awayColor: Color) {
    val winner = when {
        r.homeScore > r.awayScore -> home.name
        r.awayScore > r.homeScore -> away.name
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Risultato finale", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${home.name} ${r.homeScore} – ${r.awayScore} ${away.name}" + if (r.overtimes > 0) " (d.t.s.)" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                winner?.let { "Vince $it" } ?: "Pareggio",
                color = ScoutGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            // Ogni giocatore ha affrontato tutti gli avversari: duelli vinti per squadra e migliore in campo.
            Text(
                "Duelli giocatore contro giocatore: ${r.homeDuelsWon} – ${r.awayDuelsWon} (${r.duels.size} in totale)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            r.lines.maxWithOrNull(compareBy<SimPlayerLine> { it.duelsWon * 1000 / it.duelsPlayed.coerceAtLeast(1) }.thenBy { it.points })?.let { mvp ->
                Text(
                    "Migliore in campo: ${mvp.player.name} (${if (mvp.team == 0) home.name else away.name}) · " +
                        "${mvp.duelsWon}/${mvp.duelsPlayed} duelli vinti",
                    style = MaterialTheme.typography.bodySmall,
                    color = ScoutGreen,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            listOf(0 to home, 1 to away).forEach { (team, t) ->
                Text(
                    t.name + if (basket) " · punti e duelli vinti contro tutti" else " · gol e duelli vinti contro tutti",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                r.lines.filter { it.team == team }.forEach { line -> PlayerLineRow(line, if (team == 0) homeColor else awayColor, basket) }
            }
            DuelGrid(r, homeColor, awayColor)
        }
    }
}

/** Riga di un giocatore: nome, punti/gol, duelli vinti su tutti quelli giocati con barra. */
@Composable
private fun PlayerLineRow(line: SimPlayerLine, color: Color, basket: Boolean) {
    val rate = line.duelsWon.toFloat() / line.duelsPlayed.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            line.player.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Text(
            if (basket) "${line.points} pt" else "${line.points} gol",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
        )
        Box(modifier = Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3A3A3A))) {
            Box(modifier = Modifier.fillMaxWidth(rate).height(6.dp).background(if (rate >= 0.5f) ScoutGreen else Color(0xFFE05C5C)))
        }
        Text(
            "${line.duelsWon}/${line.duelsPlayed}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/**
 * Tabella di tutti i duelli: righe = giocatori di casa, colonne = giocatori ospiti; ogni casella
 * ha il colore della squadra che ha vinto quel duello.
 */
@Composable
private fun DuelGrid(r: SimResult, homeColor: Color, awayColor: Color) {
    val homePlayers = r.lines.filter { it.team == 0 }.map { it.player }
    val awayPlayers = r.lines.filter { it.team == 1 }.map { it.player }
    if (homePlayers.isEmpty() || awayPlayers.isEmpty()) return
    val won = r.duels.associate { (it.home.name to it.away.name) to it.homeWon }
    Text(
        "Tutti i duelli (righe: casa, colonne: ospiti)",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    val cell = 20.dp
    Row(modifier = Modifier.padding(start = 34.dp)) {
        awayPlayers.forEach { p ->
            Text(initials(p.name), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.width(cell + 2.dp))
        }
    }
    homePlayers.forEach { h ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
            Text(initials(h.name), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
            awayPlayers.forEach { a ->
                val homeWon = won[h.name to a.name] == true
                Box(
                    modifier = Modifier
                        .padding(1.dp)
                        .size(cell)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (homeWon) homeColor else awayColor)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}
