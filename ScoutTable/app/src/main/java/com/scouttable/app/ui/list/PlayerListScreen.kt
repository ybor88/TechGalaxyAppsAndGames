package com.scouttable.app.ui.list

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.DropdownFilter
import com.scouttable.app.ui.common.EditPlayerDialog
import com.scouttable.app.ui.common.PlayerRow

@Composable
fun PlayerListScreen(sport: Sport, padding: PaddingValues) {
    val repository = rememberPlayerRepository()
    val players by repository.observePlayers(sport).collectAsState(initial = emptyList())
    var editingPlayer by remember { mutableStateOf<Player?>(null) }

    editingPlayer?.let { player ->
        EditPlayerDialog(player = player, onDismiss = { editingPlayer = null })
    }

    var query by remember { mutableStateOf("") }
    var statoFilter by remember { mutableStateOf<String?>(null) }
    var nazioneFilter by remember { mutableStateOf<String?>(null) }
    var clubFilter by remember { mutableStateOf<String?>(null) }

    val stati = remember(players) { players.map { it.stato }.filter { it.isNotBlank() }.distinct().sorted() }
    val nazioni = remember(players) { players.map { it.nazione }.filter { it.isNotBlank() }.distinct().sorted() }
    val club = remember(players) { players.map { it.carrieraMigliore }.filter { it.isNotBlank() }.distinct().sorted() }

    val filtered = remember(players, query, statoFilter, nazioneFilter, clubFilter) {
        players.filter { p ->
            (query.isBlank() || p.nome.contains(query, ignoreCase = true)) &&
                (statoFilter == null || p.stato == statoFilter) &&
                (nazioneFilter == null || p.nazione == nazioneFilter) &&
                (clubFilter == null || p.carrieraMigliore == clubFilter)
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cerca giocatore") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownFilter("Stato", stati, statoFilter, { statoFilter = it }, modifier = Modifier.weight(1f))
            DropdownFilter("Nazione", nazioni, nazioneFilter, { nazioneFilter = it }, modifier = Modifier.weight(1f))
        }
        DropdownFilter(
            "Carriera migliore",
            club,
            clubFilter,
            { clubFilter = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Text(
            "${filtered.size} giocatori · ordinati per nome · tocca un giocatore per modificarlo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nessun giocatore. Usa \"Genera\" per importare una lista.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { player ->
                    PlayerRow(player = player, sport = sport, onClick = { editingPlayer = player })
                }
            }
        }
    }
}
