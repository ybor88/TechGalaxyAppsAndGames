// Copyright © Roberto Di Flumeri
package com.scouttable.app.ui.athlete

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.athlete.GENERE_FEMMINILE
import com.scouttable.app.data.athlete.GENERE_MASCHILE
import com.scouttable.app.data.athlete.isFemale
import com.scouttable.app.data.rememberPlayerRepository
import com.scouttable.app.ui.common.PlayerAvatar
import com.scouttable.app.ui.common.copyImageToInternalStorage
import java.util.UUID
import kotlinx.coroutines.launch

/** Un campo numerico del dialogo: etichetta, valore iniziale e dove scriverlo nel [Player]. */
private class StatField(val label: String, val get: (Player) -> Int, val set: (Player, Int) -> Player)

private fun statFields(sport: Sport): List<StatField> =
    if (sport.isTeamSport) com.scouttable.app.data.athlete.teamStatFields(sport).map { StatField(it.label, it.get, it.set) }
    else if (sport.isMotor) listOf(
        StatField("Gare disputate", { it.presenze }) { p, v -> p.copy(presenze = v) },
        StatField("Punti ottenuti", { it.punteggio }) { p, v -> p.copy(punteggio = v) },
        StatField("Podi", { it.assist }) { p, v -> p.copy(assist = v) },
        StatField("Gare vinte", { it.rimbalzi }) { p, v -> p.copy(rimbalzi = v) },
        StatField("Mondiali vinti", { it.palleRecuperate }) { p, v -> p.copy(palleRecuperate = v) },
        StatField("Pole position", { it.tackle }) { p, v -> p.copy(tackle = v) },
        StatField("Giri veloci", { it.golEvitati }) { p, v -> p.copy(golEvitati = v) },
    ) else listOf(
        StatField("Incontri disputati", { it.presenze }) { p, v -> p.copy(presenze = v) },
        StatField("Vinti", { it.punteggio }) { p, v -> p.copy(punteggio = v) },
        StatField("di cui per KO", { it.rimbalzi }) { p, v -> p.copy(rimbalzi = v) },
        StatField("Persi", { it.assist }) { p, v -> p.copy(assist = v) },
        StatField("di cui per KO subito", { it.tackle }) { p, v -> p.copy(tackle = v) },
        StatField("Pareggi", { it.golEvitati }) { p, v -> p.copy(golEvitati = v) },
        StatField(com.scouttable.app.data.athlete.AthleteScoring.titlesLabel(sport), { it.palleRecuperate }) { p, v -> p.copy(palleRecuperate = v) },
    )

/** Aggiunta ([player] null) o modifica a mano di un atleta, con elimina. */
@Composable
fun AthleteEditDialog(sport: Sport, player: Player?, onDismiss: () -> Unit, onDeleted: () -> Unit = {}) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fields = remember(sport) { statFields(sport) }
    var nome by remember { mutableStateOf(player?.nome.orEmpty()) }
    var anno by remember { mutableStateOf(player?.anno?.takeIf { it > 0 }?.toString().orEmpty()) }
    var nazione by remember { mutableStateOf(player?.nazione.orEmpty()) }
    var stato by remember { mutableStateOf(player?.stato ?: PlayerStatus.ATTIVO) }
    var team by remember { mutableStateOf(player?.carrieraMigliore.orEmpty()) }
    var competizione by remember {
        mutableStateOf(
            player?.competizione ?: when (sport) {
                Sport.FORMULA1 -> "Formula 1"
                Sport.MOTOGP -> "Motomondiale"
                Sport.FOOTBALL -> "Football americano"
                Sport.RUGBY -> "Rugby a 15"
                Sport.HOCKEY -> "Hockey su ghiaccio"
                else -> ""
            },
        )
    }
    var female by remember { mutableStateOf(player?.isFemale ?: false) }
    // Sport di squadra: il ruolo in campo (nelle lotte la colonna ruolo contiene il genere).
    var ruolo by remember { mutableStateOf(if (sport.isTeamSport) player?.ruolo.orEmpty() else "") }
    var url by remember { mutableStateOf(player?.proballersUrl.orEmpty()) }
    var photo by remember { mutableStateOf(player?.logoPath.orEmpty()) }
    var visionato by remember { mutableStateOf(player?.visionato ?: false) }
    var star by remember { mutableStateOf(player?.star ?: false) }
    var scouting by remember { mutableStateOf(player?.scouting ?: false) }
    val values = remember { fields.map { f -> mutableStateOf(player?.let(f.get)?.takeIf { it != 0 }?.toString().orEmpty()) } }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { copyImageToInternalStorage(context, uri)?.let { photo = it } }
    }

    if (confirmDelete && player != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${player.nome}?") },
            text = { Text("L'atleta verrà tolto dalla lista di ${sport.label}.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deletePlayer(player.id) }
                    confirmDelete = false
                    onDismiss()
                    onDeleted()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "Nuovo ${com.scouttable.app.data.athlete.AthleteScoring.athleteWord(sport)}" else "Modifica ${player.nome}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(name = nome.ifBlank { "?" }, logoPath = photo.ifBlank { null }, size = 56.dp)
                    OutlinedButton(
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.padding(start = 12.dp),
                    ) { Text("Cambia foto") }
                }
                Field("Nome", nome) { nome = it }
                Field("Anno di nascita", anno, numeric = true) { anno = it }
                Field("Nazione (in inglese, es. Italy)", nazione) { nazione = it }
                Field("Stato (Attivo, Ritirato…)", stato) { stato = it }
                Field(
                    when {
                        sport.isMotor -> "Scuderia"
                        sport.isTeamSport -> "Squadra principale"
                        else -> "Categoria di peso"
                    },
                    team,
                ) { team = it }
                Field(if (sport.isMotor) "Campionato" else "Disciplina", competizione) { competizione = it }
                if (sport.isTeamSport) Field("Ruolo", ruolo) { ruolo = it }
                if (sport.isCombat) GenderSelector(female, { female = it }, modifier = Modifier.padding(top = 8.dp))
                fields.forEachIndexed { i, f -> Field(f.label, values[i].value, numeric = true) { values[i].value = it } }
                Field("Link Wikipedia", url) { url = it }
                CheckRow("Visionato", visionato) { visionato = it }
                CheckRow("Preferito ⭐", star) { star = it }
                CheckRow("Scouting 🔎", scouting) { scouting = it }
                if (player != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Elimina atleta", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    var p = (player ?: Player(
                        id = UUID.randomUUID().toString(), sport = sport, nome = "", anno = 0, carrieraMigliore = "",
                        stato = "", nazione = "", logoPath = null, updatedAt = 0, lastReviewedAt = System.currentTimeMillis(),
                    )).copy(
                        nome = nome.trim(),
                        anno = anno.toIntOrNull() ?: 0,
                        nazione = nazione.trim(),
                        stato = stato.trim(),
                        carrieraMigliore = team.trim(),
                        competizione = competizione.trim(),
                        ruolo = when {
                            sport.isCombat -> if (female) GENERE_FEMMINILE else GENERE_MASCHILE
                            sport.isTeamSport -> ruolo.trim()
                            else -> ""
                        },
                        proballersUrl = url.trim().ifBlank { null },
                        logoPath = photo.ifBlank { null },
                        visionato = visionato,
                        star = star,
                        scouting = scouting,
                    )
                    fields.forEachIndexed { i, f -> p = f.set(p, values[i].value.toIntOrNull() ?: 0) }
                    scope.launch { repository.updatePlayer(p) }
                    onDismiss()
                },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun Field(label: String, value: String, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (numeric) it.filter(Char::isDigit) else it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
