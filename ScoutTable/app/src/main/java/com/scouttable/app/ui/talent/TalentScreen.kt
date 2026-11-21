// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.talent

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Sport
import com.scouttable.app.data.talent.Talent
import com.scouttable.app.data.talent.TalentSearch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nome italiano del paese dal codice ISO ("IT" -> "Italia"). */
fun countryNameIt(iso2: String): String = Locale("", iso2).getDisplayCountry(Locale.ITALIAN).ifBlank { iso2 }

/** Bandiera emoji dal codice ISO. */
fun flagOf(iso2: String): String =
    iso2.uppercase().map { 0x1F1E6 + (it - 'A') }.joinToString("") { String(Character.toChars(it)) }

/** Tutti i paesi ISO con il nome italiano, per la ricerca per nome. */
private val allCountries: List<Pair<String, String>> by lazy {
    Locale.getISOCountries().map { it to countryNameIt(it) }.sortedBy { it.second }
}

private sealed interface TalentState {
    data object Idle : TalentState
    data class Loading(val waitSeconds: Int = 0) : TalentState
    data class Done(val talents: List<Talent>) : TalentState
    data class Error(val message: String) : TalentState
}

/**
 * Talenti: globo 3D del mondo; scelto un paese (tocco sul globo o ricerca per nome) mostra i
 * presunti giovani talenti dello sport della card, al massimo 25 anni oggi, cercati su Wikidata
 * per nazionalità (vedi [TalentSearch]). Presente in tutte le card.
 */
@Composable
fun TalentScreen(sport: Sport, padding: PaddingValues) {
    val context = LocalContext.current
    val countries by produceState(initialValue = emptyList<GlobeCountry>()) {
        value = withContext(Dispatchers.IO) { runCatching { WorldCountries.load(context) }.getOrDefault(emptyList()) }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var state by remember { mutableStateOf<TalentState>(TalentState.Idle) }
    var retry by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now() }

    fun choose(iso: String) {
        selected = iso
        countries.firstOrNull { it.iso2 == iso }?.let { focus = WorldCountries.center(it) }
    }

    LaunchedEffect(sport, selected, retry) {
        val iso = selected ?: return@LaunchedEffect
        state = TalentState.Loading()
        state = try {
            TalentState.Done(TalentSearch.search(sport, iso, today) { wait -> state = TalentState.Loading(wait) })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // paese o sport cambiato mentre cercava: ci pensa la nuova ricerca
        } catch (e: TalentSearch.BusyException) {
            TalentState.Error("Wikidata sta limitando le richieste (succede quando è molto carico): riprova tra un minuto.")
        } catch (e: Exception) {
            TalentState.Error("Ricerca non riuscita: controlla la connessione e riprova.")
        }
    }

    val globe: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier, contentAlignment = Alignment.Center) {
            if (countries.isEmpty()) CircularProgressIndicator()
            else WorldGlobe(countries, selected, ::choose, Modifier.fillMaxSize(), focus)
        }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            globe(Modifier.fillMaxHeight().aspectRatio(1f).padding(8.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                talentContent(sport, selected, state, today, ::choose, onRetry = { retry++ })
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { globe(Modifier.fillMaxWidth().aspectRatio(1f).heightIn(max = 420.dp).padding(12.dp)) }
            talentContent(sport, selected, state, today, ::choose, onRetry = { retry++ })
        }
    }
}

private fun LazyListScope.talentContent(
    sport: Sport,
    selected: String?,
    state: TalentState,
    today: LocalDate,
    onChoose: (String) -> Unit,
    onRetry: () -> Unit,
) {
    item { CountrySearch(onChoose) }
    item {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (selected == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Ruota il globo e tocca un paese (o cercalo per nome) per vedere i giovani talenti di ${sport.label.lowercase()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else {
                Text(
                    "${flagOf(selected)} Presunti talenti · ${countryNameIt(selected)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${sport.label} · al massimo ${TalentSearch.MAX_ETA} anni al ${today.format(DATE)} · " +
                        "cercati in rete su Wikidata per nazionalità, i più noti prima (voci Wikipedia nel mondo).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    when (state) {
        TalentState.Idle -> Unit
        is TalentState.Loading -> item {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    if (state.waitSeconds > 0) "Wikidata è occupato: nuovo tentativo tra ${state.waitSeconds} s…" else "Ricerca dei talenti in corso…",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        is TalentState.Error -> item {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Riprova") }
            }
        }
        is TalentState.Done -> if (state.talents.isEmpty()) {
            item {
                Text(
                    "Nessun giovane talento trovato per questo paese: su Wikidata non ci sono atleti di ${sport.label.lowercase()} " +
                        "di questa nazionalità sotto i ${TalentSearch.MAX_ETA + 1} anni.",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            itemsIndexed(state.talents, key = { i, t -> "$i-${t.nome}" }) { index, t -> TalentRow(index + 1, t) }
        }
    }
    item { Text("© Roberto Di Flumeri", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

@Composable
private fun CountrySearch(onChoose: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val matches = remember(text) {
        val q = text.trim()
        if (q.length < 2) emptyList() else allCountries.filter { it.second.contains(q, ignoreCase = true) }.take(6)
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Cerca un paese") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        matches.forEach { (iso, name) ->
            Text(
                "${flagOf(iso)}  $name",
                modifier = Modifier.fillMaxWidth().clickable { onChoose(iso); text = "" }.padding(vertical = 10.dp, horizontal = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TalentRow(rank: Int, t: Talent) {
    val uri = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .let { m -> t.link?.let { link -> m.clickable { runCatching { uri.openUri(link) } } } ?: m },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(t.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${t.eta} anni · nascita ${t.nascita.format(DATE)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                val info = listOf(t.ruolo, t.squadra).filter { it.isNotBlank() }.joinToString(" · ")
                if (info.isNotBlank()) {
                    Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "${t.notorieta}\nvoci",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
