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
import com.scouttable.app.data.rememberPlayerRepository
import kotlinx.coroutines.launch

/** Modifica (o elimina) manualmente un giocatore, per correggere/integrare i dati trovati su internet. */
@Composable
fun EditPlayerDialog(player: Player, onDismiss: () -> Unit) {
    val repository = rememberPlayerRepository()
    val scope = rememberCoroutineScope()

    var nome by remember { mutableStateOf(player.nome) }
    var anno by remember { mutableStateOf(player.anno.takeIf { it != 0 }?.toString() ?: "") }
    var carrieraMigliore by remember { mutableStateOf(player.carrieraMigliore) }
    var stato by remember { mutableStateOf(player.stato) }
    var nazione by remember { mutableStateOf(player.nazione) }
    var logoPath by remember { mutableStateOf(player.logoPath ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica giocatore") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .height(360.dp),
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
                    value = carrieraMigliore,
                    onValueChange = { carrieraMigliore = it },
                    label = { Text("Carriera migliore / club") },
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
                    value = logoPath,
                    onValueChange = { logoPath = it },
                    label = { Text("URL logo/stemma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    repository.updatePlayer(
                        player.copy(
                            nome = nome.trim(),
                            anno = anno.toIntOrNull() ?: 0,
                            carrieraMigliore = carrieraMigliore.trim(),
                            stato = stato.trim(),
                            nazione = nazione.trim(),
                            logoPath = logoPath.trim().ifBlank { null },
                            needsReview = false,
                        )
                    )
                    onDismiss()
                }
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    repository.deletePlayer(player.id)
                    onDismiss()
                }
            }) { Text("Elimina") }
        },
    )
}
