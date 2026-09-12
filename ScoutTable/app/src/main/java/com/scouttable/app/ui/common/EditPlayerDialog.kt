package com.scouttable.app.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import com.scouttable.app.data.Sport
import com.scouttable.app.data.lookup.isPortiere
import com.scouttable.app.data.rememberPlayerRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aggiunge (quando [player] è null) o modifica/elimina manualmente un giocatore: per integrare
 * i dati trovati su internet, o per inserirne uno da zero senza passare dalla ricerca.
 */
@Composable
fun EditPlayerDialog(sport: Sport, player: Player?, onDismiss: () -> Unit) {
    val repository = rememberPlayerRepository()
    val context = LocalContext.current
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
    var golSubiti by remember { mutableStateOf(player?.golSubiti?.takeIf { it != 0 }?.toString() ?: "") }
    var rimbalzi by remember { mutableStateOf(player?.rimbalzi?.takeIf { it != 0 }?.toString() ?: "") }
    var palleRecuperate by remember { mutableStateOf(player?.palleRecuperate?.takeIf { it != 0 }?.toString() ?: "") }
    var percentualeTiriDaDue by remember { mutableStateOf(player?.percentualeTiriDaDue?.takeIf { it != 0 }?.toString() ?: "") }
    var percentualeTiriDaTre by remember { mutableStateOf(player?.percentualeTiriDaTre?.takeIf { it != 0 }?.toString() ?: "") }
    var tackle by remember { mutableStateOf(player?.tackle?.takeIf { it != 0 }?.toString() ?: "") }
    var golEvitati by remember { mutableStateOf(player?.golEvitati?.takeIf { it != 0 }?.toString() ?: "") }
    var proballersUrl by remember { mutableStateOf(player?.proballersUrl ?: "") }
    val isPortiereCalcio = sport == Sport.CALCIO && isPortiere(ruolo)

    val puntiLabel = if (sport == Sport.BASKET) "Punti totali" else "Gol totali"

    // Foto/stemma scelti dal dispositivo (galleria), non più incollando un URL: il contenuto
    // dell'immagine viene copiato nello storage privato dell'app (le content:// del selettore non
    // sono garantite leggibili dopo il riavvio) e si salva il percorso del file copiato. Vale sia
    // per basket che per calcio, ovunque manchi un logo/foto trovato in automatico.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                copyImageToInternalStorage(context, uri)?.let { logoPath = it }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuovo giocatore" else "Modifica giocatore") },
        text = {
            Column(
                // L'ordine conta: height() PRIMA di verticalScroll() nella catena di modifier è
                // essenziale, non intercambiabile. Con l'ordine inverso (era così da prima di
                // questa sessione, mai notato perché il dialogo aveva meno campi ed entrava tutto
                // in 420dp) il Column viene vincolato a un'altezza ESATTA di 420dp e passato así
                // dentro verticalScroll, che quindi vede contenuto e viewport della stessa
                // dimensione: zero spazio di scroll, e i campi oltre i 420dp restano tagliati e
                // irraggiungibili (verificato dal vivo su emulatore: lo swipe non muoveva nulla).
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
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
                // Suggerimenti a comparsa da allCountryNames (elenco completo): utile soprattutto
                // quando la ricerca automatica non ha trovato la nazionalità e va inserita a mano,
                // senza dover ricordare/digitare l'ortografia esatta. Resta comunque testo libero,
                // per non rompere valori già presenti non nell'elenco.
                // Niente popup/menu a comparsa (es. DropdownMenu/ExposedDropdownMenuBox): dentro
                // questo dialogo, con contenuto a scroll verticale ad altezza fissa, un popup ha
                // causato un blocco dello scroll dell'intera modale — i suggerimenti sono quindi
                // una semplice riga di chip che scorre in orizzontale, parte del normale layout.
                OutlinedTextField(
                    value = nazione,
                    onValueChange = { nazione = it },
                    label = { Text("Nazione") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val nazioneSuggestions = remember(nazione) {
                    if (nazione.isBlank()) emptyList()
                    else allCountryNames.filter { it.contains(nazione, ignoreCase = true) && !it.equals(nazione, ignoreCase = true) }
                }
                if (nazioneSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        nazioneSuggestions.take(6).forEach { suggestion ->
                            SuggestionChip(
                                onClick = { nazione = suggestion },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = presenze,
                    onValueChange = { presenze = it.filter(Char::isDigit) },
                    label = { Text("Presenze totali") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                // Per i portieri "gol fatti"/assist non hanno senso (sempre ~0): si mostrano solo
                // presenze e gol subiti (più sotto).
                if (!isPortiereCalcio) {
                    OutlinedTextField(
                        value = punteggio,
                        onValueChange = { punteggio = it.filter(Char::isDigit) },
                        label = { Text(puntiLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = assist,
                        onValueChange = { assist = it.filter(Char::isDigit) },
                        label = { Text("Assist totali") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (sport == Sport.BASKET) {
                    OutlinedTextField(
                        value = rimbalzi,
                        onValueChange = { rimbalzi = it.filter(Char::isDigit) },
                        label = { Text("Rimbalzi totali") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = palleRecuperate,
                        onValueChange = { palleRecuperate = it.filter(Char::isDigit) },
                        label = { Text("Palle recuperate totali") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = percentualeTiriDaDue,
                        onValueChange = { percentualeTiriDaDue = it.filter(Char::isDigit).take(3).capAtCento() },
                        label = { Text("% al tiro da due") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = percentualeTiriDaTre,
                        onValueChange = { percentualeTiriDaTre = it.filter(Char::isDigit).take(3).capAtCento() },
                        label = { Text("% al tiro da tre") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    // Solo per i portieri: recuperato da statmuse.com quando disponibile, campo
                    // comunque modificabile a mano per correggerlo o completarlo.
                    OutlinedTextField(
                        value = golSubiti,
                        onValueChange = { golSubiti = it.filter(Char::isDigit) },
                        label = { Text("Gol subiti (portieri)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = tackle,
                        onValueChange = { tackle = it.filter(Char::isDigit) },
                        label = { Text("Tackle (difensori)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = golEvitati,
                        onValueChange = { golEvitati = it.filter(Char::isDigit) },
                        label = { Text("Gol evitati (difensori)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAvatar(
                        name = nome.ifBlank { "?" },
                        logoPath = logoPath.ifBlank { null },
                        size = 48.dp,
                    )
                    OutlinedButton(
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    ) { Text("Scegli foto/logo dal dispositivo") }
                }
                if (logoPath.isNotBlank()) {
                    TextButton(onClick = { logoPath = "" }) { Text("Rimuovi foto") }
                }
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
                                golSubiti = golSubiti.toIntOrNull() ?: 0,
                                rimbalzi = rimbalzi.toIntOrNull() ?: 0,
                                palleRecuperate = palleRecuperate.toIntOrNull() ?: 0,
                                percentualeTiriDaDue = percentualeTiriDaDue.toIntOrNull() ?: 0,
                                percentualeTiriDaTre = percentualeTiriDaTre.toIntOrNull() ?: 0,
                                tackle = tackle.toIntOrNull() ?: 0,
                                golEvitati = golEvitati.toIntOrNull() ?: 0,
                                logoPath = logoPath.trim().ifBlank { null },
                                proballersUrl = proballersUrl.trim().ifBlank { null },
                                needsReview = false,
                                lastReviewedAt = System.currentTimeMillis(),
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

/** Una percentuale di tiro non può superare 100: evita che un refuso (es. "150") resti salvato. */
private fun String.capAtCento(): String = toIntOrNull()?.coerceAtMost(100)?.toString() ?: this

/**
 * Copia il contenuto dell'immagine scelta dal selettore di sistema in "files/logos" (storage
 * privato dell'app): la content:// restituita dal selettore è leggibile solo per la sessione
 * corrente, non è un riferimento stabile da poter riaprire dopo un riavvio dell'app. Ritorna il
 * percorso "file://..." del file copiato (accettato da Coil/PlayerAvatar come un URL qualsiasi),
 * o null se la copia fallisce.
 */
private suspend fun copyImageToInternalStorage(context: Context, source: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "logos").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            Uri.fromFile(file).toString()
        }.getOrNull()
    }
