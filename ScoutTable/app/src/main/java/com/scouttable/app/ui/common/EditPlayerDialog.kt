package com.scouttable.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.rememberPlayerRepository
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Aggiunge (quando [player] è null) o modifica/elimina manualmente un giocatore: per integrare
 * i dati trovati su internet, o per inserirne uno da zero senza passare dalla ricerca.
 */
@Composable
fun EditPlayerDialog(sport: Sport, player: Player?, onDismiss: () -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()
    val isNew = player == null

    var nome by remember { mutableStateOf(player?.nome ?: "") }
    var anno by remember { mutableStateOf(player?.anno?.takeIf { it != 0 }?.toString() ?: "") }
    var carrieraMigliore by remember { mutableStateOf(player?.carrieraMigliore ?: "") }
    var stato by remember { mutableStateOf(player?.stato ?: "") }
    var nazione by remember { mutableStateOf(player?.nazione ?: "") }
    var logoPath by remember { mutableStateOf(player?.logoPath ?: "") }
    var ruolo by remember { mutableStateOf(player?.ruolo ?: "") }
    var presenze by remember { mutableStateOf(player?.presenze?.takeIf { it != 0 }?.toString() ?: "") }
    var punteggio by remember { mutableStateOf(player?.punteggio?.takeIf { it != 0 }?.toString() ?: "") }
    var assist by remember { mutableStateOf(player?.assist?.takeIf { it != 0 }?.toString() ?: "") }
    var competizione by remember { mutableStateOf(player?.competizione ?: "") }
    var proballersUrl by remember { mutableStateOf(player?.proballersUrl ?: "") }

    val puntiLabel = if (sport == Sport.BASKET) "Punti totali" else "Gol totali"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuovo giocatore" else "Modifica giocatore") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(420.dp),
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Giocatore") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = anno,
                    onValueChange = { anno = it.filter(Char::isDigit).take(4) },
                    label = { Text("Anno") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = ruolo,
                    onValueChange = { ruolo = it },
                    label = { Text("Ruolo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = carrieraMigliore,
                    onValueChange = { carrieraMigliore = it },
                    label = { Text("Carriera migliore / club") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = competizione,
                    onValueChange = { competizione = it },
                    label = { Text("Competizione massima") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = stato,
                    onValueChange = { stato = it },
                    label = { Text("Stato") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = nazione,
                    onValueChange = { nazione = it },
                    label = { Text("Nazione") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = presenze,
                    onValueChange = { presenze = it.filter(Char::isDigit) },
                    label = { Text("Presenze totali") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = punteggio,
                    onValueChange = { punteggio = it.filter(Char::isDigit) },
                    label = { Text(puntiLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (sport == Sport.BASKET) {
                    OutlinedTextField(
                        value = assist,
                        onValueChange = { assist = it.filter(Char::isDigit) },
                        label = { Text("Assist totali") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = logoPath,
                    onValueChange = { logoPath = it },
                    label = { Text("URL logo/stemma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (sport == Sport.BASKET) {
                    OutlinedTextField(
                        value = proballersUrl,
                        onValueChange = { proballersUrl = it },
                        label = { Text("URL Proballers") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = nome.isNotBlank(),
                onClick = {
                    scope.launch {
                        val base = player ?: Player(
                            id = UUID.randomUUID().toString(),
                            sport = sport,
                            nome = "",
                            anno = 0,
                            carrieraMigliore = "",
                            stato = "",
                            nazione = "",
                            logoPath = null,
                            updatedAt = 0,
                        )
                        repository.updatePlayer(
                            base.copy(
                                nome = nome.trim(),
                                anno = anno.toIntOrNull() ?: 0,
                                ruolo = ruolo.trim(),
                                carrieraMigliore = carrieraMigliore.trim(),
                                competizione = competizione.trim(),
                                stato = stato.trim(),
                                nazione = nazione.trim(),
                                presenze = presenze.toIntOrNull() ?: 0,
                                punteggio = punteggio.toIntOrNull() ?: 0,
                                assist = assist.toIntOrNull() ?: 0,
                                logoPath = logoPath.trim().ifBlank { null },
                                proballersUrl = proballersUrl.trim().ifBlank { null },
                                needsReview = false,
                            )
                        )
                        onDismiss()
                    }
                },
            ) { Text(if (isNew) "Aggiungi" else "Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (player == null) {
                    onDismiss()
                } else {
                    scope.launch {
                        repository.deletePlayer(player.id)
                        onDismiss()
                    }
                }
            }) { Text(if (isNew) "Annulla" else "Elimina") }
        },
    )
}
