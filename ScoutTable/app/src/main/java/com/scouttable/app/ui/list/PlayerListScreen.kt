// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import com.scouttable.app.data.lookup.PlayerLookupService
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.EditPlayerDialog
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerFiltersPanel
import com.scouttable.app.ui.common.PlayerRow
import com.scouttable.app.ui.common.rememberPlayerFilterState
import com.scouttable.app.ui.ranking.rankPositions
import com.scouttable.app.ui.review.FieldChange
import com.scouttable.app.ui.review.PlayerDiffDialog
import com.scouttable.app.ui.review.playerChanges
import kotlinx.coroutines.launch

@Composable
fun PlayerListScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    // null = lettura dal database ancora in corso (diverso da "lista vuota"): mostra la barra di
    // caricamento invece del messaggio "Nessun giocatore".
    val loadedPlayers by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val loading = loadedPlayers == null
    val players = loadedPlayers.orEmpty()
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // Aggiornamento del singolo giocatore dal link salvato: id in ricerca, poi le differenze da
    // confermare (stessa finestra della Revisione), poi l'esito.
    var refreshingId by remember { mutableStateOf<String?>(null) }
    var pendingRefresh by remember { mutableStateOf<Triple<Player, PlayerImportRow, List<FieldChange>>?>(null) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }

    /** Rifà la ricerca con il link della fonte salvato (Wikipedia, FBref, Proballers, RealGM, playbasket...). */
    fun refreshPlayer(player: Player) {
        if (refreshingId != null) return
        refreshingId = player.id
        refreshMessage = null
        scope.launch {
            val result = PlayerLookupService.lookupWithRetry(
                player.nome,
                sport,
                existingId = player.id,
                extraUrl = player.proballersUrl,
                expectedYear = player.anno.takeIf { it != 0 },
            )
            refreshingId = null
            if (result is PlayerLookupService.LookupResult.Found) {
                val (existing, updated) = repository.previewUpdate(sport, result.row)
                pendingRefresh = Triple(player, result.row, playerChanges(existing ?: player, updated, sport))
            } else {
                refreshMessage = "${player.nome}: non trovato dal link salvato, dati invariati."
            }
        }
    }

    pendingRefresh?.let { (player, row, changes) ->
        PlayerDiffDialog(
            playerName = player.nome,
            changes = changes,
            onAccept = {
                pendingRefresh = null
                scope.launch {
                    repository.updateList(sport, listOf(row))
                    refreshMessage = if (changes.isEmpty()) "${player.nome}: già aggiornato, nessun dato cambiato."
                    else "${player.nome} aggiornato (${changes.size} ${if (changes.size == 1) "dato cambiato" else "dati cambiati"})."
                }
            },
            titlePrefix = "Aggiorna",
            onDismiss = {
                pendingRefresh = null
                refreshMessage = "${player.nome}: aggiornamento annullato, dati invariati."
            },
        )
    }

    if (showAddDialog) {
        EditPlayerDialog(sport = sport, player = null, onDismiss = { showAddDialog = false })
    }
    // Svuotare la lista è irreversibile: richiede sempre conferma esplicita prima di procedere.
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Svuotare la lista?") },
            text = { Text("Verranno eliminati tutti i ${players.size} giocatori di ${sport.label}. L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteAllPlayers(sport) }
                    showClearConfirm = false
                }) { Text("Svuota") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Annulla") }
            },
        )
    }

    val filters = rememberPlayerFilterState()
    val filtered by remember(players) { derivedStateOf { players.filter(filters::matches) } }
    // Posizione in classifica calcolata su tutta la lista, non solo sui giocatori filtrati.
    val ranks = remember(players, sport) { rankPositions(sport, players) }

    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
    // Filtri e intestazione scorrono insieme alla lista (un'unica LazyColumn): con il telefono in
    // orizzontale, fissi in alto occupavano tutta l'altezza e la lista restava bloccata.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item(key = "filters") { PlayerFiltersPanel(state = filters, players = players, modifier = Modifier.padding(top = 16.dp)) }

        item(key = "summary") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${filtered.size} giocatori · ordinati per nome · tocca un giocatore per aprirlo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (players.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Svuota lista",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        refreshMessage?.let { msg ->
            item(key = "refresh_message") {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        if (loading) {
            item(key = "loading") {
                LoadingBar(label = "Caricamento lista di ${sport.label}…", modifier = Modifier.padding(top = 16.dp))
            }
        } else if (filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nessun giocatore. Usa \"Genera\" per importare una lista.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                )
            }
        } else {
            items(filtered, key = { it.id }) { player ->
                PlayerRow(
                    player = player,
                    sport = sport,
                    onClick = { onOpenPlayer(player.id) },
                    showReviewHint = true,
                    rank = ranks[player.id],
                    // Solo per chi ha il link della fonte memorizzato (inserito con "Genera").
                    onRefresh = player.proballersUrl?.takeIf { it.isNotBlank() }?.let { { refreshPlayer(player) } },
                    refreshing = refreshingId == player.id,
                )
            }
            // Spazio per non far coprire l'ultima riga dal bottone "+".
            item(key = "fab_spacer") { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Aggiungi giocatore")
        }
    }
}
