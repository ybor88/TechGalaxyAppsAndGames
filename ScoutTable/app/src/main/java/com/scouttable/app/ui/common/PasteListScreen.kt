// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

/** Riga incollata già scomposta: nome da cercare (con eventuale anno in coda) e URL Proballers. */
private data class ParsedLine(val name: String, val proballersUrl: String?)

/**
 * Ogni riga è un URL: Wikipedia per il calcio, Proballers per il basket. Nome (e, se presente
 * nella disambiguazione, anno di nascita) si ricavano dall'URL stesso, così non vanno riscritti a
 * mano. Una riga che non è un URL viene ancora trattata come "Nome Cognome [Anno]".
 */
private fun parseLine(line: String): ParsedLine {
    val wiki = Regex("""wikipedia\.org/wiki/([^?#\s]+)""", RegexOption.IGNORE_CASE).find(line)
    if (wiki != null) {
        // es. "Francesco_Totti" o "Marco_Rossi_(calciatore_1987)" -> "Marco Rossi 1987"
        val title = URLDecoder.decode(wiki.groupValues[1], "UTF-8").replace('_', ' ')
        val year = Regex("""\(([^)]*)\)""").find(title)?.groupValues?.get(1)
            ?.let { Regex("""(?:18|19|20)\d{2}""").find(it)?.value }
        val name = title.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        return ParsedLine(if (year != null) "$name $year" else name, null)
    }
    val proballers = Regex("""proballers\.com/(?:[a-z]{2}/)?[^/]+/[^/]+/\d+/([^/?#\s]+)""", RegexOption.IGNORE_CASE)
        .find(line)
    if (proballers != null) {
        // es. ".../player/2765/michael-jordan" -> "Michael Jordan"
        val name = proballers.groupValues[1].split('-').filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        return ParsedLine(name, line.trim())
    }
    val parts = line.split("|").map { it.trim() }
    return ParsedLine(parts[0], parts.getOrNull(1)?.ifBlank { null })
}

/**
 * Schermata base per "Genera nuova lista" e "Aggiorna nuova lista": l'utente incolla una lista
 * di nomi (uno per riga), l'app cerca ogni giocatore su internet (TheSportsDB) e recupera anno,
 * carriera migliore, stato, nazione e stemma del club. Nessun file da preparare.
 */
@Composable
fun PasteListScreen(
    sport: Sport,
    padding: PaddingValues,
    title: String,
    description: String,
    buttonLabel: String,
    onFound: suspend (List<PlayerImportRow>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }
    var progressDetail by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = {
                Text(
                    if (sport == Sport.BASKET) "Un link Proballers per riga" else "Un link Wikipedia per riga"
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Text(
            if (sport == Sport.BASKET) {
                "Incolla l'URL della pagina Proballers di ogni giocatore, es: " +
                    "https://www.proballers.com/basketball/player/2765/michael-jordan"
            } else {
                "Incolla l'URL della pagina Wikipedia di ogni giocatore, es: " +
                    "https://it.wikipedia.org/wiki/Francesco_Totti"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Button(
            enabled = !busy && text.isNotBlank(),
            onClick = {
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                busy = true
                statusMessage = null
                scope.launch {
                    val found = mutableListOf<PlayerImportRow>()
                    val notFound = mutableListOf<String>()
                    val errors = mutableListOf<String>()
                    val startedAt = System.currentTimeMillis()
                    progressFraction = 0f
                    progressDetail = null
                    lines.forEachIndexed { index, line ->
                        val (name, proballersUrl) = parseLine(line)
                        progress = "Ricerca ${index + 1}/${lines.size}: $name"
                        when (val result = PlayerLookupService.lookupWithRetry(name, sport, extraUrl = proballersUrl)) {
                            is PlayerLookupService.LookupResult.Found -> found.add(result.row)
                            is PlayerLookupService.LookupResult.NotFound -> notFound.add(name)
                            // Distinto da "non trovato": qui è successo un errore vero (rete,
                            // parsing...) — mostrare il messaggio aiuta a capire la causa reale
                            // invece di confonderlo con un nome semplicemente non riconosciuto.
                            is PlayerLookupService.LookupResult.Error -> errors.add("$name (${result.message})")
                        }
                        // Piccola pausa tra un giocatore e l'altro: le API gratuite usate per la
                        // ricerca (TheSportsDB con chiave di test condivisa, Wikipedia,
                        // basketball-reference.com) possono limitare o bloccare temporaneamente
                        // raffiche di richieste ravvicinate, facendo apparire dati mancanti/
                        // incompleti (loghi, giovanili, secondo logo) su intere liste che invece
                        // funzionerebbero singolarmente. Per il calcio ogni giocatore fa ormai il
                        // doppio delle richieste di prima (Wikipedia EN+IT, due ricerche stemma
                        // TheSportsDB per i due loghi): 400ms non bastava più oltre le 5-6 righe,
                        // portato a 900ms.
                        if (index < lines.lastIndex) delay(900)
                        // Percentuale sui giocatori già elaborati; il tempo rimanente è stimato
                        // dalla media reale dei giocatori finora (pausa di 900ms compresa).
                        val done = index + 1
                        progressFraction = done.toFloat() / lines.size
                        val remainingMillis = (System.currentTimeMillis() - startedAt) / done * (lines.size - done)
                        progressDetail = if (done < lines.size) "Tempo rimanente stimato: ${formatRemaining(remainingMillis)}" else null
                    }
                    progress = "Salvataggio in corso…"
                    onFound(found)
                    busy = false
                    progress = ""
                    progressDetail = null
                    statusMessage = buildString {
                        append("Trovati e salvati ${found.size} giocatori su ${lines.size}.")
                        if (notFound.isNotEmpty()) {
                            append("\nNon trovati (nome non riconosciuto o sport diverso): ")
                            append(notFound.joinToString(", "))
                        }
                        if (errors.isNotEmpty()) {
                            append("\nErrori: ")
                            append(errors.joinToString(", "))
                        }
                        incompleteDataNote(found, sport)?.let { append("\n\n$it") }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(buttonLabel)
        }

        if (busy) {
            LoadingBar(
                label = progress,
                progress = progressFraction,
                detail = progressDetail ?: if (progressFraction == 0f) "Calcolo del tempo rimanente…" else null,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        statusMessage?.let {
            Text(it, modifier = Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(1)
    return if (seconds < 60) "circa $seconds s" else "circa ${seconds / 60} min ${seconds % 60} s"
}
