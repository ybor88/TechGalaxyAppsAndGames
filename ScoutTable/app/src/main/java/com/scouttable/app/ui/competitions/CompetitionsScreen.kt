// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.competitions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouttable.app.data.Sport
import com.scouttable.app.data.competitions.ClubTitles
import com.scouttable.app.data.competitions.TopPlayerRanking
import com.scouttable.app.data.competitions.CompetitionCatalog
import com.scouttable.app.data.competitions.CompetitionCategory
import com.scouttable.app.data.competitions.CompetitionData
import com.scouttable.app.data.competitions.CompetitionDef
import com.scouttable.app.data.competitions.CompetitionRepository
import com.scouttable.app.data.competitions.CompetitionSnapshot
import com.scouttable.app.data.competitions.RefreshProgress
import com.scouttable.app.data.competitions.TeamRef
import com.scouttable.app.ui.common.DropdownFilter
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.theme.ScoutGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detentori: una card per competizione (campionati, coppe nazionali e internazionali, tornei per
 * nazionali) con il logo e il detentore, cioè il club (o la nazionale) che l'ha vinta più volte,
 * con il numero di titoli. I dati
 * vengono da Wikipedia solo premendo "Aggiorna" e restano salvati fino al prossimo aggiornamento;
 * "Aggiorna" aggiunge anche le competizioni nuove trovate su Wikipedia. La card non apre la voce
 * Wikipedia: tutto quello che serve (logo e detentore) è già letto da lì e mostrato nella card.
 *
 * Intestazione, ricerca e card scorrono tutte insieme (un'unica LazyColumn): con il telefono in
 * orizzontale un'intestazione fissa lasciava pochissimo spazio alla lista.
 */
@Composable
fun CompetitionsScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { CompetitionRepository(context) }
    val scope = rememberCoroutineScope()
    // Nessuno stemma dei club: solo testi, per alleggerire l'app (immagini non più scaricate né mostrate).
    // Competizioni maschili o femminili (radio in alto): le due liste non si mescolano mai.
    var women by rememberSaveable(sport) { mutableStateOf(false) }
    // false = card delle competizioni, true = classifica dei club "Top club".
    var showTopPlayers by rememberSaveable(sport) { mutableStateOf(false) }

    var snapshot by remember(sport) { mutableStateOf<CompetitionSnapshot?>(null) }
    var loaded by remember(sport) { mutableStateOf(false) }
    var busy by remember(sport) { mutableStateOf(false) }
    var progress by remember(sport) { mutableStateOf<RefreshProgress?>(null) }
    var message by remember(sport) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(sport) { mutableStateOf("") }
    // Tipo di competizione selezionato nel filtro a tendina (null = tutte).
    var categoryFilter by rememberSaveable(sport) { mutableStateOf<String?>(null) }
    fun startRefresh() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val before = snapshot?.discovered?.size ?: 0
            // Un errore imprevisto (rete, file, risposta inattesa di Wikipedia) non deve chiudere
            // l'app: restano i dati salvati e compare un messaggio.
            val outcome = try {
                repository.refreshAll(sport) { progress = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            busy = false
            progress = null
            if (outcome == null) {
                message = "Aggiornamento non riuscito (Wikipedia non raggiungibile o risposta inattesa): restano i dati salvati. Riprova tra poco."
                return@launch
            }
            val (fresh, missing) = outcome
            snapshot = fresh
            val added = fresh.discovered.size - before
            message = listOfNotNull(
                if (added > 0) "$added nuove competizioni aggiunte." else null,
                if (missing > 0) "$missing competizioni senza dati (Wikipedia non ha risposto o non li riporta)." else null,
            ).joinToString(" ").ifBlank { null }
        }
    }

    LaunchedEffect(sport) {
        snapshot = runCatching { repository.load(sport) }.getOrNull()
        loaded = true
        // Prima apertura (nessun dato salvato): si scarica subito, senza aspettare "Aggiorna",
        // altrimenti le card restano vuote e sembrano non rispondere.
        if (snapshot == null) startRefresh()
    }

    // Elenco fisso finché non c'è un aggiornamento, poi anche le competizioni trovate in automatico.
    val definitions = remember(snapshot, sport) { snapshot?.definitions(sport) ?: CompetitionCatalog.forSport(sport) }
    val discoveredCount = snapshot?.discovered?.size ?: 0
    val genderDefs = remember(definitions, women) { definitions.filter { it.isWomen == women } }
    val visible = remember(genderDefs, query, categoryFilter) {
        val q = query.trim()
        genderDefs.filter { def ->
            (categoryFilter == null || def.category.label == categoryFilter) &&
                (q.isBlank() || def.label.contains(q, ignoreCase = true) || def.wikiTitle.contains(q, ignoreCase = true))
        }
    }
    // Solo i tipi effettivamente presenti per lo sport, nell'ordine in cui compaiono le sezioni.
    val categoryOptions = remember(genderDefs) {
        CompetitionCategory.entries.filter { c -> genderDefs.any { it.category == c } }.map { it.label }
    }
    // Classifica dei club per titoli totali: calcolata fuori dal thread dell'interfaccia e solo
    // quando è aperta.
    val topClubs by produceState<List<ClubTitles>?>(null, genderDefs, snapshot, showTopPlayers) {
        value = if (!showTopPlayers) null else withContext(Dispatchers.Default) {
            runCatching { TopPlayerRanking.clubTitles(genderDefs, snapshot?.data.orEmpty()) }.getOrDefault(emptyList())
        }
    }

    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "header") {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Detentori ${sport.label.lowercase()}", style = MaterialTheme.typography.titleMedium)
                        val current = snapshot
                        Text(
                            if (current == null) "Download dei dati da Wikipedia in corso…"
                            else "Aggiornato il ${formatDate(current.updatedAt)} · ${definitions.size} competizioni" +
                                if (discoveredCount > 0) " ($discoveredCount aggiunte in automatico)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(enabled = !busy, onClick = { startRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aggiorna")
                    }
                }

                progress?.let { p ->
                    LoadingBar(
                        label = p.phase,
                        progress = p.fraction,
                        detail = "Può richiedere qualche minuto: Wikipedia limita le richieste ravvicinate.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    listOf(false to "Maschile", true to "Femminile").forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { women = value; categoryFilter = null }.padding(end = 16.dp),
                        ) {
                            RadioButton(selected = women == value, onClick = { women = value; categoryFilter = null })
                            Text(label)
                        }
                    }
                }
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        selected = !showTopPlayers,
                        onClick = { showTopPlayers = false },
                        label = { Text("Competizioni") },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = showTopPlayers,
                        onClick = { showTopPlayers = true },
                        label = { Text("Top club") },
                    )
                }
                if (!showTopPlayers) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Cerca competizione") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    DropdownFilter(
                        label = "Tipo di competizione",
                        options = categoryOptions,
                        selected = categoryFilter,
                        onSelected = { categoryFilter = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }

        if (!loaded) {
            item(key = "loading") {
                LoadingBar(label = "Caricamento detentori salvati…", modifier = Modifier.padding(top = 16.dp))
            }
            return@LazyColumn
        }

        if (showTopPlayers) {
            item(key = "top_intro") {
                Text(
                    "Classifica dei club per titoli totali: per ogni club si sommano i titoli di tutte le competizioni " +
                        "${if (women) "femminili" else "maschili"} (campionati, coppe nazionali e internazionali) di cui è " +
                        "il detentore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            val ranked = topClubs
            if (ranked == null) {
                item(key = "top_loading") {
                    LoadingBar(label = "Calcolo della classifica…", modifier = Modifier.padding(top = 16.dp))
                }
                return@LazyColumn
            }
            if (ranked.isEmpty()) {
                item(key = "top_empty") {
                    Text(
                        if (snapshot == null) "Download dei detentori in corso…" else "Nessun club detentore.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
            // Stessa posizione a pari titoli (1, 1, 3...), calcolata una volta per tutta la lista.
            val positions = IntArray(ranked.size).also { pos ->
                ranked.indices.forEach { i -> pos[i] = if (i > 0 && ranked[i].total == ranked[i - 1].total) pos[i - 1] else i + 1 }
            }
            items(ranked.size, key = { "club_$it" }) { i ->
                val club = ranked[i]
                TopClubCard(position = positions[i], club = club)
            }
            item(key = "top_footer") {
                Text(
                    "Fonte: en.wikipedia.org · © Roberto Di Flumeri",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
            return@LazyColumn
        }

        CompetitionCategory.entries.forEach { category ->
            val defs = visible.filter { it.category == category }
            if (defs.isEmpty()) return@forEach
            item(key = "header_${category.name}") {
                Text(
                    "${category.label} (${defs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(defs, key = { it.id }) { def ->
                CompetitionCard(
                    def = def,
                    data = snapshot?.data?.get(def.id),
                    hasSnapshot = snapshot != null,
                )
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nessuna competizione corrisponde ai filtri.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
        }
        item(key = "footer") {
            Text(
                "Fonte: en.wikipedia.org · © Roberto Di Flumeri",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

/**
 * Card di una competizione: nome della competizione, sotto il detentore (nome di tutte le squadre
 * con più titoli, anche se sono molte a pari merito) e a destra, in evidenza, il numero di titoli.
 * Solo testi, nessuno stemma. Tutto è già nella card: nessuna azione al tocco.
 */
@Composable
private fun CompetitionCard(
    def: CompetitionDef,
    data: CompetitionData?,
    hasSnapshot: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(def.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (def.discovered) {
                Text(
                    "Aggiunta in automatico da Wikipedia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when {
                data == null -> Text(
                    if (hasSnapshot) "Dati non disponibili su Wikipedia" else "Download in corso…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                data.recordTeams.isEmpty() -> Text(
                    "Wikipedia non indica la squadra con più titoli",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        data.recordTeams.forEach { team -> TeamLine(team) }
                    }
                    data.recordTitles?.let { count ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = ScoutGreen,
                            )
                            Text(
                                if (data.recordTeams.size > 1) titlesWord(count) + " ciascuna" else titlesWord(count),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Riga della classifica dei club: posizione, club, titoli totali a destra; sotto, le competizioni
 *  da cui vengono. Solo testi. */
@Composable
private fun TopClubCard(position: Int, club: ClubTitles) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                Text(
                    teamLabel(club.team),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${club.total}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ScoutGreen)
                    Text(titlesWord(club.total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                club.competitions.joinToString(" · ") { (name, count) -> "$name $count" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Nome della squadra con più titoli (solo testo; per le nazionali preceduto dalla bandiera, che è
 *  un carattere emoji e non un'immagine da scaricare). */
@Composable
private fun TeamLine(team: TeamRef) {
    Text(
        teamLabel(team),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun teamLabel(team: TeamRef): String {
    val flag = if (team.isNational) flagEmojiFor(team.name) else ""
    return if (flag.isNotBlank()) "$flag ${team.name}" else team.name
}

private fun titlesWord(count: Int): String = if (count == 1) "titolo" else "titoli"

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(Date(millis))
