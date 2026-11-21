// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.DuplicateGroup
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import kotlinx.coroutines.launch

/**
 * Bottone "Elimina duplicati" delle liste: cerca le schede doppie di [sport], le mostra divise per
 * gruppo e chiede conferma. Niente viene eliminato senza conferma; per ogni gruppo si può scegliere
 * quale scheda tenere o escludere il gruppo. [onResult] riceve il messaggio dell'esito.
 */
@Composable
fun DuplicatePlayersButton(sport: Sport, onResult: (String) -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>?>(null) }

    if (searching) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = {
            searching = true
            scope.launch {
                val found = repository.findDuplicates(sport)
                searching = false
                if (found.isEmpty()) onResult("Nessun duplicato trovato in ${sport.label}.") else groups = found
            }
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Elimina duplicati")
        }
    }

    groups?.let { found ->
        DuplicatePlayersDialog(
            sport = sport,
            groups = found,
            onConfirm = { confirmed ->
                groups = null
                scope.launch {
                    val removed = repository.removeDuplicates(confirmed)
                    onResult(
                        if (removed == 0) "Nessun duplicato eliminato."
                        else "Eliminati $removed duplicati (${confirmed.size} ${if (confirmed.size == 1) "giocatore" else "giocatori"} unificati).",
                    )
                }
            },
            onDismiss = {
                groups = null
                onResult("Eliminazione duplicati annullata, lista invariata.")
            },
        )
    }
}

@Composable
private fun DuplicatePlayersDialog(
    sport: Sport,
    groups: List<DuplicateGroup>,
    onConfirm: (List<DuplicateGroup>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Per ogni gruppo: incluso o no, e quale scheda tenere (di base la più completa).
    val excluded = remember(groups) { mutableStateListOf<Int>() }
    val keepIds = remember(groups) { mutableStateMapOf<Int, String>().apply { groups.forEachIndexed { i, g -> put(i, g.keepId) } } }
    val chosen = groups.indices.filter { it !in excluded }.map { groups[it].copy(keepId = keepIds.getValue(it)) }
    val toDelete = chosen.sumOf { it.remove.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicati in ${sport.label}") },
        text = {
            Column {
                Text(
                    "Trovati ${groups.size} giocatori con schede doppie. Per ognuno resta la scheda selezionata " +
                        "(tocca un'altra riga per cambiarla), le altre vengono eliminate. Stella, scouting e visionato " +
                        "passano alla scheda tenuta. Togli la spunta per non toccare un gruppo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    groups.forEachIndexed { i, g ->
                        item(key = "g$i") {
                            val included = i !in excluded
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = included, onCheckedChange = { if (it) excluded.remove(i) else excluded.add(i) })
                                Column(Modifier.weight(1f)) {
                                    Text(g.keep.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(g.reasons.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            g.players.forEach { p ->
                                val keep = keepIds[i] == p.id
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = included) { keepIds[i] = p.id }.padding(start = 12.dp),
                                ) {
                                    RadioButton(selected = keep, onClick = { keepIds[i] = p.id }, enabled = included)
                                    Column(Modifier.weight(1f)) {
                                        Text(describe(p), style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            if (!included) "non toccato" else if (keep) "resta" else "da eliminare",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when {
                                                !included -> MaterialTheme.colorScheme.onSurfaceVariant
                                                keep -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.error
                                            },
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }, enabled = toDelete > 0) {
                Text(if (toDelete == 1) "Elimina 1 scheda" else "Elimina $toDelete schede", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Riga di riconoscimento: anno, nazione, club, dati principali e fonte. */
private fun describe(p: Player): String = listOfNotNull(
    p.anno.takeIf { it != 0 }?.toString(),
    p.nazione.takeIf { it.isNotBlank() },
    p.ruolo.takeIf { it.isNotBlank() },
    p.carrieraMigliore.takeIf { it.isNotBlank() },
    p.presenze.takeIf { it > 0 }?.let { "$it pres." },
    p.punteggio.takeIf { it > 0 }?.let { "$it ${if (p.sport.isBasket) "punti" else "gol"}" },
    p.stato.takeIf { it.isNotBlank() },
    p.proballersUrl?.takeIf { it.isNotBlank() }?.let { it.substringAfter("://").substringBefore('/') },
).joinToString(" · ").ifEmpty { "nessun dato" }
