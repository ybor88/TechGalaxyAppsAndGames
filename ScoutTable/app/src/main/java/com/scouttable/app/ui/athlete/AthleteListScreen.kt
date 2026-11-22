// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.AthleteScoring
import com.scouttable.app.data.athlete.isFemale
import com.scouttable.app.data.lookup.PlayerLookupService
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.DuplicatePlayersButton
import com.scouttable.app.ui.common.LoadingBar
import com.scouttable.app.ui.common.PlayerFiltersPanel
import com.scouttable.app.ui.common.rememberPlayerFilterState
import com.scouttable.app.ui.ranking.rankChangeMessage
import com.scouttable.app.ui.ranking.rankPositions
import com.scouttable.app.ui.review.PendingUpdate
import com.scouttable.app.ui.review.PlayerDiffDialog
import com.scouttable.app.ui.review.applyAcceptedChanges
import com.scouttable.app.ui.review.playerChanges
import kotlinx.coroutines.launch

/**
 * Lista degli atleti (Lotta, MotoGP, Formula 1): filtri condivisi con basket/calcio, nella lotta
 * anche Maschile/Femminile; ordinamento per nome o per classifica generale. Ogni card mostra il
 * rapporto (punti per gara o % vittorie) con il distacco dalla media e la freccia ↑/↓.
 */
@Composable
fun AthleteListScreen(sport: Sport, padding: PaddingValues, onOpenPlayer: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loaded by repository.observePlayers(sport).collectAsState<List<Player>, List<Player>?>(initial = null)
    val players = loaded.orEmpty()
    val filters = rememberPlayerFilterState()
    var female by rememberSaveable(sport) { mutableStateOf<Boolean?>(null) }
    // Ordine: classifica generale, nome o prossima revisione (come nella lista di calcio e basket).
    var sortMode by rememberSaveable(sport) { mutableStateOf("ranking") }
    var showAdd by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var refreshingId by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingUpdate?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh(player: Player) {
        if (refreshingId != null) return
        refreshingId = player.id
        message = null
        scope.launch {
            val result = PlayerLookupService.lookupWithRetry(player.nome, sport, existingId = player.id, extraUrl = player.proballersUrl)
            refreshingId = null
            if (result is PlayerLookupService.LookupResult.Found) {
                val (existing, updated) = repository.previewUpdate(sport, result.row)
                pending = PendingUpdate(existing ?: player, updated, playerChanges(existing ?: player, updated, sport))
            } else {
                val reason = (result as? PlayerLookupService.LookupResult.Error)?.message ?: "non trovato"
                message = "${player.nome}: $reason, dati invariati."
            }
        }
    }

    pending?.let { (player, updated, changes) ->
        PlayerDiffDialog(
            playerName = player.nome,
            changes = changes,
            titlePrefix = "Aggiorna",
            onAccept = { accepted ->
                pending = null
                scope.launch {
                    val before = repository.exportPlayers(sport)
                    val saved = applyAcceptedChanges(player, updated, changes, accepted)
                    repository.updatePlayer(saved)
                    val rankText = rankChangeMessage(sport, player, before, saved, repository.exportPlayers(sport))
                    Toast.makeText(context, rankText, Toast.LENGTH_LONG).show()
                    message = (if (changes.isEmpty()) "${player.nome}: già aggiornato." else "${player.nome} aggiornato.") + " $rankText"
                }
            },
            onDismiss = {
                pending = null
                message = "${player.nome}: aggiornamento annullato, dati invariati."
            },
        )
    }
    if (showAdd) AthleteEditDialog(sport = sport, player = null, onDismiss = { showAdd = false })
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Svuotare la lista?") },
            text = { Text("Verranno eliminati tutti i ${players.size} atleti di ${sport.label}. L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteAllPlayers(sport) }
                    showClear = false
                }) { Text("Svuota") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Annulla") } },
        )
    }

    val general = AthleteScoring.generalMetric(sport)
    val ranks = remember(players, sport) { rankPositions(sport, players) }
    val visible = remember(players, female, sortMode, filters.query, filters.stato, filters.nazione, filters.club, filters.visionato, filters.star, filters.scouting) {
        val list = players.filter { filters.matches(it) && (female == null || it.isFemale == female) }
        when (sortMode) {
            "ranking" -> list.sortedWith(compareByDescending<Player> { AthleteScoring.value(it, general) ?: -1.0 }.thenBy { it.nome })
            // Prima chi è già in Revisione, poi gli attivi per data, in fondo chi non ne avrà (ritirati).
            "review" -> list.sortedWith(
                compareBy<Player> { if (it.needsReview) Long.MIN_VALUE else com.scouttable.app.data.ReviewPrefs.nextReviewAt(it) ?: Long.MAX_VALUE }
                    .thenBy { it.nome.lowercase() },
            )
            else -> list
        }
    }

    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item(key = "filters") {
                PlayerFiltersPanel(state = filters, players = players, modifier = Modifier.padding(top = 16.dp))
            }
            run {
                item(key = "gender") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(selected = female == null, onClick = { female = null }, label = { Text("Tutti") })
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(selected = female == false, onClick = { female = false }, label = { Text("Maschile") })
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(selected = female == true, onClick = { female = true }, label = { Text("Femminile") })
                    }
                }
            }
            item(key = "summary") {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${visible.size} ${AthleteScoring.athleteWord(sport, plural = true)} · " + when {
                            sport.isCombat -> "⭐ vittorie e KO ≥ 80% · ↑ una delle due ≥ 80% · – vittorie ≥ 50% · ↓ sotto il 50%"
                            sport.isMotor -> "⭐ gare vinte × 2 > gare disputate · ↑ vittorie, podi, punti, pole o giri veloci × 2 > gare · ↓ altrimenti"
                            else -> "freccia e distacco: ${AthleteScoring.ratioMetric(sport).label.lowercase()} rispetto alla media"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (players.size > 1) {
                        DuplicatePlayersButton(sport = sport, onResult = { message = it })
                    }
                    if (players.isNotEmpty()) {
                        IconButton(onClick = { showClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Svuota lista", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item(key = "sort") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text("Ordina:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                    FilterChip(selected = sortMode == "ranking", onClick = { sortMode = "ranking" }, label = { Text(general.label) })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = sortMode == "name", onClick = { sortMode = "name" }, label = { Text("Nome") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = sortMode == "review", onClick = { sortMode = "review" }, label = { Text("Revisione") })
                }
            }
            message?.let { msg ->
                item(key = "message") {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            when {
                loaded == null -> item(key = "loading") {
                    LoadingBar(label = "Caricamento lista di ${sport.label}…", modifier = Modifier.padding(top = 16.dp))
                }
                visible.isEmpty() -> item(key = "empty") {
                    Text(
                        "Nessun ${AthleteScoring.athleteWord(sport)}. Usa \"Genera\" per importare i link Wikipedia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
                else -> {
                    items(visible, key = { it.id }) { player ->
                        AthleteRow(
                            player = player,
                            allPlayers = players,
                            onClick = { onOpenPlayer(player.id) },
                            rank = ranks[player.id],
                            onRefresh = { refresh(player) },
                            refreshing = refreshingId == player.id,
                            showReviewHint = true,
                        )
                    }
                    item(key = "spacer") { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
        FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Aggiungi atleta")
        }
    }
}
