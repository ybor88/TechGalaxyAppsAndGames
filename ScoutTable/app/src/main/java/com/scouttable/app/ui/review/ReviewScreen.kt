package com.scouttable.app.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.EditPlayerDialog
import com.scouttable.app.ui.common.PlayerRow
import com.scouttable.app.ui.common.incompleteDataNote
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val flagged by repository.observeFlaggedForReview(sport).collectAsState(initial = emptyList())
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    editingPlayer?.let { player ->
        EditPlayerDialog(sport = sport, player = player, onDismiss = { editingPlayer = null })
    }

    Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
        Text("Revisione mensile giocatori attivi", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ogni 30 giorni i giocatori con stato \"Attivo\" vengono segnalati per revisione " +
                "(può essere cambiato il club migliore o lo stato). \"Aggiorna da internet\" rilancia la " +
                "ricerca per ognuno di loro e aggiorna i dati in automatico.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Button(onClick = {
                scope.launch {
                    val ranNow = repository.runMonthlyReviewIfDue(sport, force = true)
                    statusMessage = if (ranNow) "Revisione eseguita." else "Nessuna azione necessaria."
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
                            val result = PlayerLookupService.lookup(
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

        if (flagged.isEmpty()) {
            Text("Nessun giocatore in attesa di revisione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(flagged, key = { it.id }) { player ->
                    PlayerRow(player = player, sport = sport, onClick = { editingPlayer = player })
                }
            }
        }
    }
}
