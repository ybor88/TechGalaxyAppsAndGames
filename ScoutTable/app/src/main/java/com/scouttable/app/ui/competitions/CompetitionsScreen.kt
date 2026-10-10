// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.competitions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scouttable.app.data.Sport
import com.scouttable.app.data.competitions.CompetitionCatalog
import com.scouttable.app.data.competitions.CompetitionCategory
import com.scouttable.app.data.competitions.CompetitionData
import com.scouttable.app.data.competitions.CompetitionDef
import com.scouttable.app.data.competitions.CompetitionRepository
import com.scouttable.app.data.competitions.CompetitionSnapshot
import com.scouttable.app.data.competitions.RefreshProgress
import com.scouttable.app.data.competitions.TeamRef
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.flagEmojiFor
import com.scouttable.app.ui.theme.ScoutGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
fun CompetitionsScreen(sport: Sport, padding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember { CompetitionRepository(context) }
    val scope = rememberCoroutineScope()

    var snapshot by remember(sport) { mutableStateOf<CompetitionSnapshot?>(null) }
    var loaded by remember(sport) { mutableStateOf(false) }
    var busy by remember(sport) { mutableStateOf(false) }
    var progress by remember(sport) { mutableStateOf<RefreshProgress?>(null) }
    var message by remember(sport) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(sport) { mutableStateOf("") }
    var logoOverrides by remember(sport) { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Competizione per cui si sta scegliendo il logo dalla galleria.
    var pickingLogoFor by rememberSaveable(sport) { mutableStateOf<String?>(null) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = pickingLogoFor
        pickingLogoFor = null
        if (uri != null && id != null) {
            scope.launch {
                if (repository.setLogoOverride(context, sport, id, uri)) logoOverrides = repository.loadLogoOverrides(sport)
            }
        }
    }

    fun startRefresh() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            val before = snapshot?.discovered?.size ?: 0
            val (fresh, missing) = repository.refreshAll(sport) { progress = it }
            snapshot = fresh
            busy = false
            progress = null
            val added = fresh.discovered.size - before
            message = listOfNotNull(
                if (added > 0) "$added nuove competizioni aggiunte." else null,
                if (missing > 0) "$missing competizioni senza dati (Wikipedia non ha risposto o non li riporta)." else null,
            ).joinToString(" ").ifBlank { null }
        }
    }

    LaunchedEffect(sport) {
        snapshot = repository.load(sport)
        logoOverrides = repository.loadLogoOverrides(sport)
        loaded = true
        // Prima apertura (nessun dato salvato): si scarica subito, senza aspettare "Aggiorna",
        // altrimenti le card restano vuote e sembrano non rispondere.
        if (snapshot == null) startRefresh()
    }

    // Elenco fisso finché non c'è un aggiornamento, poi anche le competizioni trovate in automatico.
    val definitions = remember(snapshot, sport) { snapshot?.definitions(sport) ?: CompetitionCatalog.forSport(sport) }
    val discoveredCount = snapshot?.discovered?.size ?: 0
    val visible = remember(definitions, query) {
        val q = query.trim()
        if (q.isBlank()) definitions
        else definitions.filter { it.label.contains(q, ignoreCase = true) || it.wikiTitle.contains(q, ignoreCase = true) }
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
                        progress = if (p.total > 0) p.done.toFloat() / p.total else null,
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cerca competizione") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        if (!loaded) return@LazyColumn

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
                    customLogo = logoOverrides[def.id],
                    hasSnapshot = snapshot != null,
                    onChangeLogo = {
                        pickingLogoFor = def.id
                        pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
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
 * Card di una competizione: logo e nome della competizione, sotto il detentore (stemma e nome
 * della squadra con più titoli) e a destra, in evidenza, il numero di titoli. Tutto è già nella
 * card: nessuna azione al tocco.
 */
@Composable
private fun CompetitionCard(
    def: CompetitionDef,
    data: CompetitionData?,
    customLogo: String?,
    hasSnapshot: Boolean,
    onChangeLogo: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo scelto a mano (ha la precedenza) o da Wikipedia, con l'icona per cambiarlo.
                Box {
                    // Fondo chiaro dietro al logo: molti loghi di Wikipedia sono scritte nere su
                    // trasparente (es. Mondiali FIFA, Copa América), invisibili sul tema scuro.
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayerAvatar(name = def.label, logoPath = customLogo ?: data?.logoUrl, size = 38.dp)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onChangeLogo),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = "Cambia logo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(def.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (def.discovered) {
                        Text(
                            "Aggiunta in automatico da Wikipedia",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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

/** Stemma (del club, o della federazione per le nazionali; la bandiera solo se Wikipedia non ha
 *  un'immagine per la squadra) e nome della squadra con più titoli. */
@Composable
private fun TeamLine(team: TeamRef) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        val flag = if (team.isNational && team.badgeUrl == null) flagEmojiFor(team.name) else ""
        if (flag.isNotBlank()) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(flag, fontSize = 28.sp) }
        } else {
            PlayerAvatar(name = team.name, logoPath = team.badgeUrl, size = 40.dp)
        }
        Text(
            team.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

private fun titlesWord(count: Int): String = if (count == 1) "titolo" else "titoli"

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(Date(millis))
