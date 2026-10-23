// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.nbaintl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.nbaintl.IntlMatch
import com.scouttable.app.data.nbaintl.IntlTeam
import com.scouttable.app.data.nbaintl.IntlTeamRecord
import com.scouttable.app.data.nbaintl.NbaIntlSnapshot
import com.scouttable.app.data.nbaintl.NbaInternationalRepository
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.theme.ScoutGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val LossRed = Color(0xFFE05C5C)

/**
 * Basket · NBA vs squadre internazionali: per ogni squadra il bilancio delle partite giocate contro
 * l'altra parte (squadre NBA contro club/nazionali del resto del mondo, e viceversa) con lo stemma,
 * ordinate per vittorie; in alto le due squadre con il numero massimo di vittorie. Toccando una
 * squadra si apre il suo albo d'oro: tutte le singole partite con data, luogo e punteggio.
 * Fonte: en.wikipedia.org, scaricata alla prima apertura e poi solo con "Aggiorna".
 */
@Composable
fun NbaInternationalScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember { NbaInternationalRepository(context) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<NbaIntlSnapshot?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showNba by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    fun refresh() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val fresh = runCatching { repository.refresh() }.getOrNull()
            busy = false
            if (fresh == null) message = "Aggiornamento non riuscito (Wikipedia non raggiungibile): restano i dati salvati."
            else snapshot = fresh
        }
    }

    LaunchedEffect(Unit) {
        snapshot = repository.load()
        loaded = true
        if (snapshot == null) refresh()
    }

    val records = snapshot?.records.orEmpty()
    val topNba = remember(records) { records.filter { it.isNba }.maxByOrNull { it.wins } }
    val topIntl = remember(records) { records.filter { !it.isNba }.maxByOrNull { it.wins } }
    // Bilancio complessivo NBA contro resto del mondo, dall'elenco completo delle partite.
    val matches = snapshot?.matches.orEmpty()
    val nbaWins = remember(matches) { matches.count { it.nbaWon } }
    val visible = remember(records, showNba, query) {
        val q = query.trim()
        records.filter { it.isNba == showNba && (q.isBlank() || it.team.name.contains(q, ignoreCase = true)) }
            .sortedWith(compareByDescending<IntlTeamRecord> { it.wins }.thenBy { it.losses }.thenBy { it.team.name })
    }

    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "header") {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NBA vs squadre internazionali", style = MaterialTheme.typography.titleMedium)
                        val current = snapshot
                        Text(
                            if (current == null) "Download dei dati da Wikipedia in corso…"
                            else "Aggiornato il ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(Date(current.updatedAt))} · " +
                                "${current.matches.size} partite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(enabled = !busy, onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aggiorna")
                    }
                }
                if (busy) LoadingBar(label = "Lettura partite e stemmi da Wikipedia…", modifier = Modifier.padding(top = 12.dp))
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (!loaded) {
            item(key = "loading") { LoadingBar(label = "Caricamento…", modifier = Modifier.padding(top = 16.dp)) }
            return@LazyColumn
        }

        if (topNba != null || topIntl != null) {
            item(key = "records") {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Record di vittorie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (matches.isNotEmpty()) NbaVsWorldCard(nbaWins, matches.size - nbaWins)
                    topNba?.let { RecordHolderCard("Squadra NBA con più vittorie", it) }
                    topIntl?.let { RecordHolderCard("Squadra internazionale con più vittorie contro la NBA", it) }
                }
            }
        }

        item(key = "filters") {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row {
                    FilterChip(selected = !showNba, onClick = { showNba = false; expanded = null }, label = { Text("Squadre internazionali") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = showNba, onClick = { showNba = true; expanded = null }, label = { Text("Squadre NBA") })
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cerca squadra") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Tocca una squadra per vedere l'albo d'oro delle sue partite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }

        val positions = IntArray(visible.size).also { pos ->
            visible.indices.forEach { i -> pos[i] = if (i > 0 && visible[i].wins == visible[i - 1].wins) pos[i - 1] else i + 1 }
        }
        items(visible.size, key = { "team_${showNba}_${visible[it].team.name}" }) { i ->
            val record = visible[i]
            val key = record.team.name
            TeamRecordCard(
                position = positions[i],
                record = record,
                expanded = expanded == key,
                matches = if (expanded == key) snapshot?.matchesOf(record).orEmpty() else emptyList(),
                onClick = { expanded = if (expanded == key) null else key },
            )
        }
        if (snapshot != null && visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nessuna squadra corrisponde alla ricerca.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
        }
        item(key = "footer") {
            Text(
                "Fonte: en.wikipedia.org · ${NbaInternationalRepository.PAGE_TITLE} · © Roberto Di Flumeri",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

private fun teamTitle(team: IntlTeam, isNba: Boolean): String {
    val flag = if (!isNba) team.country?.let { flagEmojiFor(it) }.orEmpty() else ""
    return if (flag.isNotBlank()) "$flag ${team.name}" else team.name
}

/** NBA vs Mondo: vittorie totali delle squadre NBA e delle squadre internazionali, con barra proporzionale. */
@Composable
private fun NbaVsWorldCard(nbaWins: Int, worldWins: Int) {
    val total = (nbaWins + worldWins).coerceAtLeast(1)
    // Arrotondate in modo che la somma faccia sempre 100%.
    val nbaPct = (nbaWins * 100.0 / total).roundToInt()
    val worldPct = 100 - nbaPct
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1D428A), Color(0xFF7C2D12))))
                .padding(12.dp),
        ) {
            Text("NBA vs Mondo · $total partite", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NBA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("$nbaPct% vittorie", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Text("$nbaWins", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ScoutGreen)
                Text(" – ", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("$worldWins", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("🌍 Mondo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("$worldPct% vittorie", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
                if (nbaWins > 0) Spacer(modifier = Modifier.weight(nbaWins.toFloat()).fillMaxHeight().background(ScoutGreen))
                if (worldWins > 0) Spacer(modifier = Modifier.weight(worldWins.toFloat()).fillMaxHeight().background(Color(0xFFFFB300)))
            }
        }
    }
}

@Composable
private fun RecordHolderCard(title: String, record: IntlTeamRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0F766E))))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(name = record.team.name, logoPath = record.logoUrl, size = 56.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Text(teamTitle(record.team, record.isNba), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${record.games} partite · ${record.losses} sconfitte", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${record.wins}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ScoutGreen)
                Text(if (record.wins == 1) "vittoria" else "vittorie", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun TeamRecordCard(position: Int, record: IntlTeamRecord, expanded: Boolean, matches: List<IntlMatch>, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$position°",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(44.dp),
                )
                PlayerAvatar(name = record.team.name, logoPath = record.logoUrl, size = 40.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        teamTitle(record.team, record.isNba),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${record.games} partite · ${record.wins} V – ${record.losses} S",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${record.wins}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
                    Text(if (record.wins == 1) "vittoria" else "vittorie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Chiudi albo d'oro" else "Apri albo d'oro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Albo d'oro", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (matches.isEmpty()) {
                        Text("Partite non disponibili.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    matches.forEach { MatchLine(it, record.isNba) }
                }
            }
        }
    }
}

/** Una partita dal punto di vista della squadra aperta: V/S, avversaria, punteggio, data e luogo. */
@Composable
private fun MatchLine(match: IntlMatch, forNba: Boolean) {
    val won = match.nbaWon == forNba
    val opponent = if (forNba) match.intl else match.nba
    val own = if (forNba) match.nbaScore else match.intlScore
    val other = if (forNba) match.intlScore else match.nbaScore
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (won) "V" else "S",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (won) ScoutGreen else LossRed)
                .padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                "vs ${teamTitle(opponent, !forNba)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(match.date, match.place).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "$own–$other" + if (match.overtime) " dts" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (won) ScoutGreen else LossRed,
        )
    }
}
