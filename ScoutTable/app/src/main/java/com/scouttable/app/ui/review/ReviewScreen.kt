// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.ReviewPrefs
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.PlayerRow
import com.scouttable.app.ui.common.incompleteDataNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val flagged by repository.observeFlaggedForReview(sport).collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    // Revisione del singolo giocatore: id in ricerca, poi l'aggiornamento trovato in attesa di
    // conferma (dato da salvare + differenze da mostrare).
    var singleBusyId by remember { mutableStateOf<String?>(null) }
    var pendingSingle by remember { mutableStateOf<Triple<Player, PlayerImportRow, List<FieldChange>>?>(null) }

    pendingSingle?.let { (player, row, changes) ->
        PlayerDiffDialog(
            playerName = player.nome,
            changes = changes,
            onAccept = {
                pendingSingle = null
                scope.launch {
                    repository.updateList(sport, listOf(row))
                    statusMessage = if (changes.isEmpty()) "${player.nome}: nessun dato cambiato, segnato come revisionato."
                    else "${player.nome} aggiornato (${changes.size} ${if (changes.size == 1) "dato cambiato" else "dati cambiati"})."
                }
            },
            onDismiss = {
                pendingSingle = null
                statusMessage = "${player.nome}: aggiornamento annullato, dati invariati."
            },
        )
    }

    /** Cerca di nuovo un solo giocatore e mostra le differenze prima di salvare. */
    fun reviewSingle(player: Player) {
        if (singleBusyId != null || busy) return
        singleBusyId = player.id
        statusMessage = null
        scope.launch {
            val result = PlayerLookupService.lookupWithRetry(
                player.nome,
                sport,
                existingId = player.id,
                extraUrl = player.proballersUrl,
                expectedYear = player.anno.takeIf { it != 0 },
            )
            singleBusyId = null
            if (result is PlayerLookupService.LookupResult.Found) {
                val (existing, updated) = repository.previewUpdate(sport, result.row)
                pendingSingle = Triple(player, result.row, playerChanges(existing ?: player, updated, sport))
            } else {
                statusMessage = "${player.nome}: non trovato su internet, dati invariati."
            }
        }
    }

    // Spiegazione e bottoni scorrono insieme alla lista: in orizzontale, fissi in alto, occupavano
    // tutta l'altezza disponibile e la lista restava bloccata.
    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "header") {
            Column(modifier = Modifier.padding(top = 16.dp)) {
            Text("Revisione mensile giocatori attivi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ogni giocatore con stato \"Attivo\" o \"Svincolato\" viene segnalato per revisione 30 giorni dopo " +
                    "la sua ultima revisione (può essere cambiato il club migliore o lo stato), a blocchi: " +
                    "al massimo ${ReviewPrefs.REVIEW_BATCH_SIZE} alla volta, chi aspetta da più tempo per primo. " +
                    "Gli altri entrano man mano che questi vengono revisionati. " +
                    "Basta aprire la scheda di un giocatore per segnarlo come revisionato: non ricomparirà " +
                    "per 30 giorni. \"Aggiorna da internet\" rilancia la ricerca per ognuno di loro e " +
                    "aggiorna i dati in automatico; \"Revisiona\" sotto un giocatore aggiorna solo lui, " +
                    "mostrando prima cosa cambia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Button(onClick = {
                    scope.launch {
                        val flaggedNow = repository.flagDueForReview(sport)
                        statusMessage = if (flaggedNow > 0) "Segnalati $flaggedNow giocatori." else "Nessuna azione necessaria."
                    }
                }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Esegui ora")
                }
                OutlinedButton(
                    onClick = {
                        busy = true
                        statusMessage = null
                        scope.launch {
                            val refreshed = mutableListOf<PlayerImportRow>()
                            val failed = mutableListOf<String>()
                            flagged.forEachIndexed { index, player ->
                                progress = "Aggiornamento ${index + 1}/${flagged.size}: ${player.nome}"
                                // Stessa ricerca, identica in tutto (retry su errore transitorio incluso),
                                // di "Genera/Aggiorna da lista" (vedi PasteListScreen): usare qui una
                                // versione senza retry e senza pausa tra le richieste sovraccaricava le
                                // API gratuite (TheSportsDB/Wikipedia) con raffiche ravvicinate, facendo
                                // sballare foto/stemmi e altri dati sui risultati.
                                val result = PlayerLookupService.lookupWithRetry(
                                    player.nome,
                                    sport,
                                    existingId = player.id,
                                    extraUrl = player.proballersUrl,
                                    expectedYear = player.anno.takeIf { it != 0 },
                                )
                                when (result) {
                                    is PlayerLookupService.LookupResult.Found -> refreshed.add(result.row)
                                    else -> failed.add(player.nome)
                                }
                                // Vedi la stessa pausa in PasteListScreen: per il calcio ogni
                                // giocatore fa il doppio delle richieste di prima (Wikipedia EN+IT,
                                // due ricerche stemma TheSportsDB), 400ms non basta più su liste lunghe.
                                if (index < flagged.lastIndex) delay(900)
                            }
                            if (refreshed.isNotEmpty()) repository.updateList(sport, refreshed)
                            busy = false
                            progress = ""
                            statusMessage = buildString {
                                append("Aggiornati ${refreshed.size} giocatori su ${flagged.size}.")
                                if (failed.isNotEmpty()) append("\nNon trovati: ${failed.joinToString(", ")}")
                                incompleteDataNote(refreshed, sport)?.let { append("\n\n$it") }
                            }
                        }
                    },
                    enabled = !busy && flagged.isNotEmpty(),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) {
                    Text("Aggiorna da internet (${flagged.size})")
                }
            }

            if (busy) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    CircularProgressIndicator()
                    Text(progress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }

            statusMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            }

            }
        }

        if (flagged.isEmpty()) {
            item(key = "empty") {
                Text("Nessun giocatore in attesa di revisione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(flagged, key = { it.id }) { player ->
                Column {
                    PlayerRow(
                        player = player,
                        sport = sport,
                        onClick = {
                            // Aprire la scheda basta a considerarlo "revisionato": non deve
                            // ricomparire ogni giorno finché non passa un mese, anche se l'utente
                            // chiude la schermata di dettaglio senza modificare nulla.
                            scope.launch { repository.clearReviewFlag(player.id) }
                            onOpenPlayer(player.id)
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    ) {
                        if (singleBusyId == player.id) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                            Text("Ricerca in corso…", style = MaterialTheme.typography.bodySmall)
                        } else {
                            TextButton(
                                onClick = { reviewSingle(player) },
                                enabled = singleBusyId == null && !busy,
                            ) { Text("Revisiona ${player.nome}") }
                        }
                    }
                }
            }
        }
    }
}
