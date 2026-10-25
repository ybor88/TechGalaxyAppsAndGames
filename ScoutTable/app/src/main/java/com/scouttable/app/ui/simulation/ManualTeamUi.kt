// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.simulation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scouttable.app.data.Sport
import com.scouttable.app.data.simulation.ManualTeamRepository
import com.scouttable.app.data.simulation.SavedTeam
import com.scouttable.app.ui.common.PlayerAvatar
import kotlinx.coroutines.launch

/**
 * Scelta di una squadra per una casella (partita o torneo): mostra la squadra scelta, o il
 * pulsante per sceglierla. Toccando si apre l'elenco delle squadre salvate dello sport, da cui
 * scegliere, crearne una nuova, modificarle o eliminarle.
 */
@Composable
fun TeamPicker(
    sport: Sport,
    label: String,
    selected: SavedTeam?,
    onSelected: (SavedTeam?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showList by remember { mutableStateOf(false) }
    if (showList) {
        TeamListDialog(
            sport = sport,
            title = label,
            onPick = { onSelected(it); showList = false },
            onDismiss = { showList = false },
            onTeamChanged = { changed -> if (changed.id == selected?.id) onSelected(changed) },
            onTeamDeleted = { id -> if (id == selected?.id) onSelected(null) },
        )
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showList = true },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            if (selected != null) {
                PlayerAvatar(name = selected.name, logoPath = selected.logoPath, size = 40.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(selected.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        valueLabel(sport, selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Cambia", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Scegli o crea la squadra", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Elenco delle squadre salvate: scegli, crea, modifica, elimina. */
@Composable
private fun TeamListDialog(
    sport: Sport,
    title: String,
    onPick: (SavedTeam) -> Unit,
    onDismiss: () -> Unit,
    onTeamChanged: (SavedTeam) -> Unit,
    onTeamDeleted: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(sport) { ManualTeamRepository(context, sport) }
    var teams by remember { mutableStateOf<List<SavedTeam>?>(null) }
    var editing by remember { mutableStateOf<SavedTeam?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedTeam?>(null) }
    LaunchedEffect(sport) { teams = repository.load() }

    editing?.let { team ->
        TeamEditorDialog(
            sport = sport,
            initial = team,
            repository = repository,
            onSave = { saved ->
                scope.launch {
                    teams = repository.save(saved)
                    editing = null
                    onTeamChanged(saved)
                }
            },
            onDismiss = { editing = null },
        )
    }
    confirmDelete?.let { team ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminare ${team.name}?") },
            text = { Text("La squadra salvata verrà eliminata. Le partite già registrate restano.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        teams = repository.delete(team.id)
                        onTeamDeleted(team.id)
                        confirmDelete = null
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Annulla") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = { editing = SavedTeam(ManualTeamRepository.newId(), "", null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nuova squadra")
                }
                val list = teams
                when {
                    list == null -> Text("Caricamento…", modifier = Modifier.padding(top = 12.dp))
                    list.isEmpty() -> Text(
                        "Nessuna squadra salvata: creane una con \"Nuova squadra\".",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    else -> list.forEach { team ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onPick(team) }) {
                            PlayerAvatar(name = team.name, logoPath = team.logoPath, size = 32.dp)
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(team.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(valueLabel(sport, team), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { editing = team }) { Icon(Icons.Default.Edit, contentDescription = "Modifica ${team.name}") }
                            IconButton(onClick = { confirmDelete = team }) {
                                Icon(Icons.Default.Delete, contentDescription = "Elimina ${team.name}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

/**
 * Modifica di una squadra: nome, stemma dalla galleria e un solo valore (basket: efficienza totale;
 * calcio: il valore percentuale/decimale). Nella partita vince la squadra con il valore più alto.
 */
@Composable
private fun TeamEditorDialog(
    sport: Sport,
    initial: SavedTeam,
    repository: ManualTeamRepository,
    onSave: (SavedTeam) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var logo by remember { mutableStateOf(initial.logoPath) }
    var value by remember { mutableStateOf(initial.value?.let(::formatNumber).orEmpty()) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { repository.importLogo(context, uri)?.let { logo = it } }
    }
    val parsed = value.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Nuova squadra" else "Modifica squadra") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(name = name.ifBlank { "?" }, logoPath = logo, size = 56.dp)
                    OutlinedButton(
                        onClick = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (logo == null) "Scegli stemma" else "Cambia stemma")
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome squadra") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                NumberField(
                    value = value,
                    onChange = { value = it },
                    label = valueName(sport),
                    decimal = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    if (sport.isBasket) "Nella partita vince la squadra con l'efficienza totale più alta; più è grande la " +
                        "differenza, più netto è il risultato."
                    else "Nella partita vince la squadra con il valore più BASSO (percentuale o decimale, es. 0,85 o 42,5); " +
                        "più è grande la differenza, più gol di scarto. A valori uguali è pareggio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsed != null,
                onClick = { onSave(initial.copy(name = name.trim(), logoPath = logo, value = parsed)) },
            ) { Text("Salva squadra") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Nome del valore della squadra per lo sport. */
fun valueName(sport: Sport): String = if (sport.isBasket) "Efficienza totale" else "Valore (percentuale o decimale)"

/** "Efficienza totale: 245" / "Valore: 1,85" (o l'avviso se manca). */
fun valueLabel(sport: Sport, team: SavedTeam): String =
    team.value?.let { "${if (sport.isBasket) "Efficienza totale" else "Valore"}: ${formatNumber(it)}" } ?: "Valore non inserito: modifica la squadra"

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, decimal: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == ',' || it == '.' || it == '-')) }) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

internal fun formatNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
