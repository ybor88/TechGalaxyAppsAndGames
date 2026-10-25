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
import com.scouttable.app.data.simulation.ManualPlayer
import com.scouttable.app.data.simulation.ManualTeamRepository
import com.scouttable.app.data.simulation.SavedTeam
import com.scouttable.app.data.simulation.slotsFor
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
                        "${selected.filledCount} di ${slotsFor(sport).size} giocatori",
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
                    onClick = { editing = SavedTeam(ManualTeamRepository.newId(), "", null, List(slotsFor(sport).size) { null }) },
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
                                Text("${team.filledCount} di ${slotsFor(sport).size} giocatori", style = MaterialTheme.typography.labelSmall)
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
 * Modifica di una squadra, a tutto schermo: nome, stemma dalla galleria e un giocatore per ogni
 * posizione (basket: nome ed Eff; calcio: nome, presenze e gol, per il portiere gol subiti).
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
    val slots = slotsFor(sport)
    var name by remember { mutableStateOf(initial.name) }
    var logo by remember { mutableStateOf(initial.logoPath) }
    // Un campo di testo per valore, così si possono scrivere anche numeri parziali ("12,").
    val names = remember { mutableStateListOf(*Array(slots.size) { initial.players.getOrNull(it)?.name.orEmpty() }) }
    val effs = remember { mutableStateListOf(*Array(slots.size) { initial.players.getOrNull(it)?.eff?.let(::formatNumber).orEmpty() }) }
    val apps = remember { mutableStateListOf(*Array(slots.size) { initial.players.getOrNull(it)?.presenze?.toString().orEmpty() }) }
    val goals = remember { mutableStateListOf(*Array(slots.size) { initial.players.getOrNull(it)?.gol?.toString().orEmpty() }) }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { repository.importLogo(context, uri)?.let { logo = it } }
    }

    fun build(): SavedTeam = initial.copy(
        name = name.trim(),
        logoPath = logo,
        players = slots.indices.map { i ->
            val n = names[i].trim()
            if (n.isBlank()) null else ManualPlayer(
                name = n,
                eff = effs[i].replace(',', '.').toDoubleOrNull(),
                presenze = apps[i].toIntOrNull(),
                gol = goals[i].toIntOrNull(),
            )
        },
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(if (initial.name.isBlank()) "Nuova squadra" else "Modifica squadra", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (sport.isBasket) "10 giocatori: 5 titolari e 5 di panchina. Ogni giocatore sfida chi ha la sua stessa " +
                        "posizione nell'altra squadra, vince l'Eff più alto."
                    else "11 giocatori in 4-4-2. Ogni giocatore sfida chi ha la sua stessa posizione nell'altra squadra: vince il " +
                        "rapporto gol/presenze più alto; per il portiere inserisci i gol subiti (vince chi ne subisce meno a partita).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
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
                slots.forEachIndexed { i, slot ->
                    Text(
                        "${i + 1}. ${slot.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = names[i],
                            onValueChange = { names[i] = it },
                            label = { Text("Giocatore") },
                            singleLine = true,
                            modifier = Modifier.weight(if (sport.isBasket) 2.2f else 1.8f),
                        )
                        if (sport.isBasket) {
                            NumberField(effs[i], { effs[i] = it }, "Eff", decimal = true, modifier = Modifier.weight(1f))
                        } else {
                            NumberField(apps[i], { apps[i] = it }, "Presenze", modifier = Modifier.weight(1f))
                            NumberField(goals[i], { goals[i] = it }, if (slot.code == "GK") "Gol subiti" else "Gol", modifier = Modifier.weight(1f))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annulla") }
                    Button(
                        enabled = name.isNotBlank() && names.any { it.isNotBlank() },
                        onClick = { onSave(build()) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Salva squadra") }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

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

private fun formatNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
