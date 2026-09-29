// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player

private val siNoOptions = listOf("Sì", "No")

/**
 * Filtri giocatore (nome, stato, nazione, carriera migliore, visionato, stella) condivisi tra la
 * lista ([com.scouttable.app.ui.list.PlayerListScreen]) e le classifiche
 * ([com.scouttable.app.ui.ranking.PodiumRankingScreen]). null = "Tutti".
 */
@Stable
class PlayerFilterState {
    var query by mutableStateOf("")
    var stato by mutableStateOf<String?>(null)
    var nazione by mutableStateOf<String?>(null)
    var club by mutableStateOf<String?>(null)
    var visionato by mutableStateOf<String?>(null)
    var star by mutableStateOf<String?>(null)

    val activeCount: Int
        get() = listOf(query.isNotBlank(), stato != null, nazione != null, club != null, visionato != null, star != null)
            .count { it }

    fun clear() {
        query = ""
        stato = null
        nazione = null
        club = null
        visionato = null
        star = null
    }

    fun matches(p: Player): Boolean =
        (query.isBlank() || p.nome.contains(query, ignoreCase = true)) &&
            (stato == null || p.stato == stato) &&
            (nazione == null || p.nazione == nazione) &&
            (club == null || p.carrieraMigliore == club) &&
            (visionato == null || p.visionato == (visionato == "Sì")) &&
            (star == null || p.star == (star == "Sì"))
}

@Composable
fun rememberPlayerFilterState(): PlayerFilterState = remember { PlayerFilterState() }

/** Campo di ricerca + tendine dei filtri; le opzioni delle tendine vengono da [players]. */
@Composable
fun PlayerFiltersPanel(state: PlayerFilterState, players: List<Player>, modifier: Modifier = Modifier) {
    val stati = remember(players) { players.map { it.stato }.filter { it.isNotBlank() }.distinct().sorted() }
    val nazioni = remember(players) { players.map { it.nazione }.filter { it.isNotBlank() }.distinct().sorted() }
    val club = remember(players) { players.map { it.carrieraMigliore }.filter { it.isNotBlank() }.distinct().sorted() }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            label = { Text("Cerca giocatore") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownFilter("Stato", stati, state.stato, { state.stato = it }, modifier = Modifier.weight(1f))
            DropdownFilter("Nazione", nazioni, state.nazione, { state.nazione = it }, modifier = Modifier.weight(1f))
        }
        DropdownFilter(
            "Carriera migliore",
            club,
            state.club,
            { state.club = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownFilter("Visionato", siNoOptions, state.visionato, { state.visionato = it }, modifier = Modifier.weight(1f))
            DropdownFilter("Stella ⭐", siNoOptions, state.star, { state.star = it }, modifier = Modifier.weight(1f))
        }
    }
}
