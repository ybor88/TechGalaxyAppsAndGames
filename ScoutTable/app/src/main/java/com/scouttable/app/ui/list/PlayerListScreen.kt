// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.EditPlayerDialog
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerFiltersPanel
import com.scouttable.app.ui.common.PlayerRow
import com.scouttable.app.ui.common.rememberPlayerFilterState
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

    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        PlayerFiltersPanel(state = filters, players = players)

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

        if (loading) {
            LoadingBar(label = "Caricamento lista di ${sport.label}…", modifier = Modifier.padding(top = 16.dp))
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nessun giocatore. Usa \"Genera\" per importare una lista.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { player ->
                    PlayerRow(
                        player = player,
                        sport = sport,
                        onClick = { onOpenPlayer(player.id) },
                        showReviewHint = true,
                    )
                }
            }
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
