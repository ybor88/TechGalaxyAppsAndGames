// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteMetric
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.labelFor
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.review.ChangesTable
import com.scouttable.app.ui.review.FieldChange
import com.scouttable.app.ui.review.playerChanges
import com.scouttable.app.ui.theme.ScoutGreen
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.scouttable.app.data.competitions.AwardRepository
import com.scouttable.app.data.competitions.AwardData
import com.scouttable.app.data.competitions.AwardCatalog
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Detentore di un record: gli atleti in testa (anche a pari merito) e il valore. */
private data class Holder(val metric: AthleteMetric, val leaders: List<Player>, val value: Double?, val runnersUp: List<Player>)

private fun holders(players: List<Player>, sport: Sport, female: Boolean): List<Holder> {
    val group = AthleteScoring.sameGroup(players, sport, female)
    return AthleteMetric.forSport(sport).mapNotNull { m ->
        val ranked = AthleteScoring.ranking(group, m)
        val top = ranked.firstOrNull() ?: return@mapNotNull null
        val best = AthleteScoring.value(top, m)
        val leaders = ranked.takeWhile { AthleteScoring.value(it, m) == best }
        Holder(m, leaders, best, ranked.drop(leaders.size).take(2))
    }
}

/** Esito di "Aggiorna": cosa è cambiato per ogni atleta e per ogni record. */
private data class UpdateReport(
    val changed: List<Pair<String, List<FieldChange>>>,
    val unchanged: List<String>,
    val failed: List<String>,
    val holderChanges: List<FieldChange>,
    val finishedAt: Long,
    /** Albo d'oro da Wikipedia: ultimo campione e più titolati, prima/dopo. */
    val alboChanges: List<FieldChange> = emptyList(),
    val alboChecked: Int = 0,
)

/** Testo confrontato per l'albo: ultimo campione e chi ha più titoli. */
private fun alboText(d: AwardData?): String {
    val latest = d?.latest ?: return "—"
    val most = d.mostWins?.let { (names, n) -> " · più titoli: ${names.joinToString(", ")} ($n)" }.orEmpty()
    return "${latest.season}: ${latest.player}$most"
}

/**
 * Detentori degli sport individuali: per ogni statistica chi detiene il record nella tua lista
 * (più mondiali, più punti, più podi, miglior punti/gara... o, nella lotta, miglior % di vittorie,
 * più KO, più titoli), con i due inseguitori. "Aggiorna" rilegge da Wikipedia tutti gli atleti
 * e mostra subito i dati aggiornati: per ogni atleta i valori prima/dopo e i record cambiati.
 */
@Composable
fun AthleteHoldersScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val loaded by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val players = loaded.orEmpty()
    var female by rememberSaveable(sport) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(0f) }
    var report by remember { mutableStateOf<UpdateReport?>(null) }
    val records = remember(players, female) { holders(players, sport, female) }
    // Albo d'oro letto da Wikipedia (campioni del mondo), salvato come i Premi di basket/calcio.
    val context = LocalContext.current
    val awardRepository = remember { AwardRepository(context) }
    val alboDefs = remember(sport) { AwardCatalog.forSport(sport) }
    var albo by remember(sport) { mutableStateOf<Map<String, AwardData>>(emptyMap()) }

    suspend fun refreshAlbo() {
        albo = runCatching {
            awardRepository.refresh(sport) { done, total ->
                withContext(Dispatchers.Main) {
                    progress = "Albo d'oro da Wikipedia (${minOf(done + 1, total)} di $total)"
                    fraction = done.toFloat() / total / 4
                }
            }
        }.getOrDefault(albo)
    }

    LaunchedEffect(sport) {
        albo = awardRepository.load(sport)
        // Prima apertura: l'albo si scarica subito, senza aspettare "Aggiorna".
        if (awardRepository.missesCatalog(sport, albo) && !busy) {
            busy = true
            refreshAlbo()
            busy = false
            progress = ""
        }
    }

    fun refreshAll() {
        if (busy) return
        busy = true
        report = null
        scope.launch {
            val alboBefore = albo
            refreshAlbo()
            val alboChanges = alboDefs.mapNotNull { def ->
                val b = alboText(alboBefore[def.id])
                val a = alboText(albo[def.id])
                if (a != b) FieldChange(def.label, b, a) else null
            }
            val before = repository.exportPlayers(sport)
            val rows = mutableListOf<PlayerImportRow>()
            val changed = mutableListOf<Pair<String, List<FieldChange>>>()
            val unchanged = mutableListOf<String>()
            val failed = mutableListOf<String>()
            before.forEachIndexed { i, p ->
                progress = "Aggiornamento ${i + 1}/${before.size}: ${p.nome}"
                fraction = 0.25f + 0.75f * i / before.size
                val result = PlayerLookupService.lookupWithRetry(p.nome, sport, existingId = p.id, extraUrl = p.proballersUrl)
                if (result is PlayerLookupService.LookupResult.Found) {
                    rows += result.row
                    val (existing, updated) = repository.previewUpdate(sport, result.row)
                    val diff = playerChanges(existing ?: p, updated, sport)
                    if (diff.isEmpty()) unchanged += p.nome else changed += p.nome to diff
                } else {
                    failed += p.nome + ((result as? PlayerLookupService.LookupResult.Error)?.message?.let { " ($it)" } ?: "")
                }
                if (i < before.lastIndex) delay(700)
            }
            fraction = 1f
            if (rows.isNotEmpty()) repository.updateList(sport, rows)
            val after = repository.exportPlayers(sport)
            report = UpdateReport(
                changed, unchanged, failed, holderChanges(before, after, sport), System.currentTimeMillis(),
                alboChanges = alboChanges, alboChecked = alboDefs.size,
            )
            busy = false
            progress = ""
        }
    }

    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "header") {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Detentori ${sport.label}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (alboDefs.isEmpty()) "Record tra i ${players.size} " else "Albo d'oro dei campioni del mondo da Wikipedia e record tra i ${players.size} ") +
                                "${AthleteScoring.athleteWord(sport, true)} in lista.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(enabled = !busy, onClick = { refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aggiorna")
                    }
                }
                if (busy) {
                    LoadingBar(label = progress, progress = fraction, detail = "Rilegge da Wikipedia l'albo d'oro e tutti gli atleti in lista.", modifier = Modifier.padding(top = 12.dp))
                }
                GenderSelector(female, { female = it }, modifier = Modifier.padding(top = 8.dp))
            }
        }
        report?.let { r ->
            item(key = "report") { ReportCard(r, onClose = { report = null }) }
        }
        // Albo d'oro (Wikipedia): nella lotta solo maschile, it.wikipedia non ha albi femminili.
        val visibleAlbo = alboDefs.filter { it.women == female }
        if (visibleAlbo.isNotEmpty()) {
            item(key = "albo_title") { SectionTitle("Albo d'oro · Wikipedia") }
            items(visibleAlbo, key = { "albo_${it.id}" }) { def -> AlboCard(def.label, albo[def.id], busy, players, onOpenPlayer) }
        } else if (female) {
            item(key = "albo_none") {
                Text(
                    "Nessun albo d'oro femminile per questo sport: sotto i record delle atlete in lista.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        item(key = "records_title") { SectionTitle("Record tra gli atleti in lista") }
        when {
            loaded == null -> item(key = "loading") { LoadingBar(label = "Caricamento detentori…", modifier = Modifier.padding(top = 16.dp)) }
            records.isEmpty() -> item(key = "empty") {
                Text(
                    "Nessun ${AthleteScoring.athleteWord(sport)} con statistiche: usa \"Genera\" con i link Wikipedia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
            else -> items(records, key = { it.metric.name }) { h -> HolderCard(h, onOpenPlayer) }
        }
        item(key = "footer") {
            Text(
                "Fonte: it.wikipedia.org · © Roberto Di Flumeri",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

/** Record il cui detentore o valore è cambiato tra [before] e [after] (entrambi i generi nella lotta). */
private fun holderChanges(before: List<Player>, after: List<Player>, sport: Sport): List<FieldChange> {
    val genders = listOf(false, true)
    return genders.flatMap { female ->
        val old = holders(before, sport, female).associateBy { it.metric }
        val new = holders(after, sport, female).associateBy { it.metric }
        AthleteMetric.forSport(sport).mapNotNull { m ->
            val b = old[m]?.let(::holderText) ?: "—"
            val a = new[m]?.let(::holderText) ?: "—"
            val label = "${m.labelFor(sport)} (${if (female) "F" else "M"})"
            if (a != b) FieldChange(label, b, a) else null
        }
    }
}

private fun holderText(h: Holder): String =
    h.leaders.joinToString(", ") { it.nome } + " · " + AthleteScoring.format(h.metric, h.value)

@Composable
private fun ReportCard(r: UpdateReport, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dati aggiornati", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Chiudi") }
            }
            Text(
                "${r.changed.size} atleti con dati cambiati, ${r.unchanged.size} invariati" +
                    if (r.failed.isNotEmpty()) ", ${r.failed.size} non letti." else ".",
                style = MaterialTheme.typography.bodySmall,
            )
            if (r.alboChanges.isNotEmpty()) {
                Text("Albo d'oro cambiato", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                ChangesTable(r.alboChanges)
            } else if (r.alboChecked > 0) {
                Text("Albo d'oro: nessun cambiamento (${r.alboChecked} albi verificati).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (r.holderChanges.isNotEmpty()) {
                Text("Detentori cambiati", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                ChangesTable(r.holderChanges)
            } else {
                Text("Nessun detentore è cambiato.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            r.changed.forEach { (name, diff) ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                ChangesTable(diff)
            }
            if (r.unchanged.isNotEmpty()) {
                Text("Invariati: ${r.unchanged.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (r.failed.isNotEmpty()) {
                Text("Non letti (dati salvati invariati): ${r.failed.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun HolderCard(h: Holder, onOpenPlayer: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(h.metric.labelFor(h.leaders.first().sport), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    h.leaders.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenPlayer(p.id) }.padding(vertical = 2.dp)) {
                            PlayerAvatar(name = p.nome, logoPath = p.logoPath, size = 36.dp)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("${flagEmojiFor(p.nazione)} ${p.nome}".trim(), fontWeight = FontWeight.Bold)
                                if (p.carrieraMigliore.isNotBlank()) {
                                    Text(p.carrieraMigliore, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(
                    AthleteScoring.format(h.metric, h.value),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ScoutGreen,
                )
            }
            if (h.runnersUp.isNotEmpty()) {
                Text(
                    "Inseguono: " + h.runnersUp.joinToString(" · ") { "${it.nome} ${AthleteScoring.formatValue(it, h.metric)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Un albo d'oro letto da Wikipedia: campione più recente, detentore (chi ha vinto più titoli) e,
 * al tocco, l'elenco completo dal più recente. I campioni presenti nella tua lista sono toccabili.
 */
@Composable
private fun AlboCard(label: String, data: AwardData?, busy: Boolean, players: List<Player>, onOpenPlayer: (String) -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    fun inList(name: String) = players.firstOrNull { it.nome.equals(name, ignoreCase = true) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            val latest = data?.latest
            if (data == null || latest == null) {
                Text(
                    if (busy) "Download in corso…" else "Dati non disponibili: premi \"Aggiorna\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Campione più recente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val p = inList(latest.player)
                    Text(
                        latest.player,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (p != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = if (p != null) Modifier.clickable { onOpenPlayer(p.id) } else Modifier,
                    )
                    latest.team?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(latest.season, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ScoutGreen)
            }
            data.mostWins?.takeIf { it.second > 1 }?.let { (names, count) ->
                Text(
                    "Detentore (più titoli): ${names.joinToString(", ")} · $count",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 8.dp),
            ) {
                Text("Albo d'oro (${data.winners.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                data.winners.forEach { w ->
                    val p = inList(w.player)
                    Row(
                        modifier = Modifier.fillMaxWidth().then(if (p != null) Modifier.clickable { onOpenPlayer(p.id) } else Modifier).padding(vertical = 3.dp),
                    ) {
                        Text(w.season, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                w.player + if (p != null) " · in lista" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            w.team?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
