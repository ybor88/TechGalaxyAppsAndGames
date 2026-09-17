package com.scouttable.app.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport
import com.scouttable.app.data.YouthClub
import com.scouttable.app.data.decodeYouthClubs
import com.scouttable.app.data.encodeYouthClubs
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
    var secondLogoPath by remember { mutableStateOf(player?.secondLogoPath ?: "") }
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
    var visionato by remember { mutableStateOf(player?.visionato ?: false) }
    var star by remember { mutableStateOf(player?.star ?: false) }
    // Nazionale: comuni a entrambi gli sport (presenze/punteggio), il resto specifico.
    var presenzeNazionale by remember { mutableStateOf(player?.presenzeNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    var punteggioNazionale by remember { mutableStateOf(player?.punteggioNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    var golSubitiNazionale by remember { mutableStateOf(player?.golSubitiNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    var assistNazionale by remember { mutableStateOf(player?.assistNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    var rimbalziNazionale by remember { mutableStateOf(player?.rimbalziNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    var palleRecuperateNazionale by remember { mutableStateOf(player?.palleRecuperateNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    var minutiNazionale by remember { mutableStateOf(player?.minutiNazionale?.takeIf { it != 0 }?.toString() ?: "") }
    // Secondo logo (periodo/stagione migliore): finora solo l'immagine era modificabile, non i
    // dati testuali/numerici mostrati sotto in PlayerDetailScreen.
    var secondLogoClub by remember { mutableStateOf(player?.secondLogoClub ?: "") }
    var secondLogoPeriodo by remember { mutableStateOf(player?.secondLogoPeriodo ?: "") }
    var secondLogoPresenze by remember { mutableStateOf(player?.secondLogoPresenze?.takeIf { it != 0 }?.toString() ?: "") }
    var secondLogoGol by remember { mutableStateOf(player?.secondLogoGol?.takeIf { it != 0 }?.toString() ?: "") }
    var secondLogoEff by remember { mutableStateOf(player?.secondLogoEff?.takeIf { it != 0 }?.toString() ?: "") }
    // Giovanili (solo calcio): una riga di testo "Club (anni)" per voce, invece della lista JSON
    // grezza — più semplice da leggere/modificare a mano.
    var giovaniliText by remember {
        mutableStateOf(decodeYouthClubs(player?.giovanili).joinToString("\n") { "${it.club} (${it.anni})" })
    }
    var college by remember { mutableStateOf(player?.college ?: "") }
    var effMedio by remember { mutableStateOf(player?.effMedio?.takeIf { it != 0 }?.toString() ?: "") }
    var minutiCarriera by remember { mutableStateOf(player?.minutiCarriera?.takeIf { it != 0 }?.toString() ?: "") }
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
    // Stesso meccanismo del logo principale (carriera migliore), ma per il "secondo logo"
    // (club/stagione con Eff/rapporto migliore per il basket, spell migliore per il calcio,
    // vedi PlayerDetailScreen): finora era impostato solo in automatico dalla ricerca, senza
    // modo di correggerlo o impostarlo a mano.
    val pickSecondImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                copyImageToInternalStorage(context, uri)?.let { secondLogoPath = it }
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Altezza fissa (420dp) sostituita con una percentuale dell'altezza disponibile: in
    // orizzontale lo schermo è spesso più basso di 420dp, e un Column vincolato a
    // un'altezza fissa MAGGIORE di quella che l'AlertDialog può effettivamente concedergli
    // smette di scrollare correttamente (il contenuto oltre il bordo resta tagliato e
    // irraggiungibile, segnalato dall'utente: "se metto il tel orizzontale... non scrollano
    // le informazioni"). Il cap a 420dp resta per non allargare inutilmente il dialogo sugli
    // schermi molto alti in verticale.
    val configuration = LocalConfiguration.current
    val dialogContentHeight = remember(configuration.screenHeightDp) {
        minOf(420, (configuration.screenHeightDp * 0.6f).toInt()).dp
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
                    .height(dialogContentHeight)
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
                Text(
                    "Nazionale",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                OutlinedTextField(
                    value = presenzeNazionale,
                    onValueChange = { presenzeNazionale = it.filter(Char::isDigit) },
                    label = { Text("Presenze in Nazionale") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (isPortiereCalcio) {
                    OutlinedTextField(
                        value = golSubitiNazionale,
                        onValueChange = { golSubitiNazionale = it.filter(Char::isDigit) },
                        label = { Text("Gol subiti in Nazionale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = punteggioNazionale,
                        onValueChange = { punteggioNazionale = it.filter(Char::isDigit) },
                        label = { Text(if (sport == Sport.BASKET) "Punti in Nazionale" else "Gol in Nazionale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (sport == Sport.BASKET) {
                    OutlinedTextField(
                        value = assistNazionale,
                        onValueChange = { assistNazionale = it.filter(Char::isDigit) },
                        label = { Text("Assist in Nazionale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = rimbalziNazionale,
                        onValueChange = { rimbalziNazionale = it.filter(Char::isDigit) },
                        label = { Text("Rimbalzi in Nazionale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = palleRecuperateNazionale,
                        onValueChange = { palleRecuperateNazionale = it.filter(Char::isDigit) },
                        label = { Text("Palle recuperate in Nazionale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = minutiNazionale,
                        onValueChange = { minutiNazionale = it.filter(Char::isDigit) },
                        label = { Text("Minuti in Nazionale") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "Statistiche di carriera",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    OutlinedTextField(
                        value = college,
                        onValueChange = { college = it },
                        label = { Text("College") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = effMedio,
                        onValueChange = { effMedio = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("Eff medio") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = minutiCarriera,
                        onValueChange = { minutiCarriera = it.filter(Char::isDigit) },
                        label = { Text("Minuti totali di carriera") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (sport == Sport.CALCIO) {
                    Text(
                        "Giovanili",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    OutlinedTextField(
                        value = giovaniliText,
                        onValueChange = { giovaniliText = it },
                        label = { Text("Una per riga: Club (anni)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Text(
                    "Periodo migliore (secondo logo)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                OutlinedTextField(
                    value = secondLogoClub,
                    onValueChange = { secondLogoClub = it },
                    label = { Text("Club/squadra") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = secondLogoPeriodo,
                    onValueChange = { secondLogoPeriodo = it },
                    label = { Text("Periodo/stagione") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (sport == Sport.CALCIO) {
                    OutlinedTextField(
                        value = secondLogoPresenze,
                        onValueChange = { secondLogoPresenze = it.filter(Char::isDigit) },
                        label = { Text("Presenze in quel periodo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = secondLogoGol,
                        onValueChange = { secondLogoGol = it.filter(Char::isDigit) },
                        label = { Text(if (isPortiereCalcio) "Gol subiti in quel periodo" else "Gol in quel periodo") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = secondLogoEff,
                        onValueChange = { secondLogoEff = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("Eff in quella stagione") },
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerAvatar(
                        name = nome.ifBlank { "?" },
                        logoPath = secondLogoPath.ifBlank { null },
                        size = 48.dp,
                    )
                    OutlinedButton(
                        onClick = {
                            pickSecondImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    ) { Text("Scegli logo periodo migliore dal dispositivo") }
                }
                if (secondLogoPath.isNotBlank()) {
                    TextButton(onClick = { secondLogoPath = "" }) { Text("Rimuovi logo periodo migliore") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { visionato = !visionato },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = visionato, onCheckedChange = { visionato = it })
                    Text("Visionato dal vivo")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { star = !star },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = star, onCheckedChange = { star = it })
                    Text("Preferito ⭐")
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
                                secondLogoPath = secondLogoPath.trim().ifBlank { null },
                                proballersUrl = proballersUrl.trim().ifBlank { null },
                                visionato = visionato,
                                star = star,
                                presenzeNazionale = presenzeNazionale.toIntOrNull() ?: 0,
                                punteggioNazionale = punteggioNazionale.toIntOrNull() ?: 0,
                                golSubitiNazionale = golSubitiNazionale.toIntOrNull() ?: 0,
                                assistNazionale = assistNazionale.toIntOrNull() ?: 0,
                                rimbalziNazionale = rimbalziNazionale.toIntOrNull() ?: 0,
                                palleRecuperateNazionale = palleRecuperateNazionale.toIntOrNull() ?: 0,
                                minutiNazionale = minutiNazionale.toIntOrNull() ?: 0,
                                secondLogoClub = secondLogoClub.trim(),
                                secondLogoPeriodo = secondLogoPeriodo.trim(),
                                secondLogoPresenze = secondLogoPresenze.toIntOrNull() ?: 0,
                                secondLogoGol = secondLogoGol.toIntOrNull() ?: 0,
                                secondLogoEff = secondLogoEff.toIntOrNull() ?: 0,
                                giovanili = encodeYouthClubs(parseGiovaniliText(giovaniliText)),
                                college = college.trim(),
                                effMedio = effMedio.toIntOrNull() ?: 0,
                                minutiCarriera = minutiCarriera.toIntOrNull() ?: 0,
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
                    // Un tocco impreciso su questo pulsante (adiacente a "Salva") cancellava il
                    // giocatore senza nessuna possibilità di annullare: aggiunta una conferma,
                    // stesso pattern già usato altrove nell'app per azioni distruttive (es.
                    // "Svuota lista").
                    showDeleteConfirm = true
                }
            }) { Text(if (isNew) "Annulla" else "Elimina") }
        },
    )

    if (showDeleteConfirm && player != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare ${player.nome}?") },
            text = { Text("Il giocatore verrà rimosso definitivamente dalla lista.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deletePlayer(player.id)
                        showDeleteConfirm = false
                        onDismiss()
                    }
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            },
        )
    }
}

/** Una percentuale di tiro non può superare 100: evita che un refuso (es. "150") resti salvato. */
private fun String.capAtCento(): String = toIntOrNull()?.coerceAtMost(100)?.toString() ?: this

// Inverso del "${club} (${anni})" mostrato nel campo giovanili: una riga per voce. Una riga senza
// parentesi (es. digitata a mano senza rispettare il formato) diventa comunque un club valido con
// anni vuoti, invece di essere scartata silenziosamente.
private val giovaniliLineRegex = Regex("""^(.+?)\s*\(([^()]*)\)\s*$""")

private fun parseGiovaniliText(text: String): List<YouthClub> =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
        val m = giovaniliLineRegex.find(line)
        if (m != null) YouthClub(m.groupValues[1].trim(), m.groupValues[2].trim())
        else YouthClub(line, "")
    }

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
