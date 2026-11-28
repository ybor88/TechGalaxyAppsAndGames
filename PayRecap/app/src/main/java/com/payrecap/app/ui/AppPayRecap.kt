// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AppPayRecap(vm: PayRecapViewModel) {
    val cedoliniOpz by vm.cedolini.collectAsState()
    val scheda by vm.scheda.collectAsState()
    val selezionato by vm.selezionato.collectAsState()
    val caricamento by vm.caricamento.collectAsState()
    val impostazioni by vm.impostazioni.collectAsState()

    val scegliFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.caricaDocumento(uri)
    }
    val carica = { scegliFile.launch(arrayOf("application/pdf", "image/*")) }

    val revisione = caricamento as? StatoCaricamento.Revisione
    if (revisione != null) {
        BackHandler { vm.annullaCaricamento() }
        SchermataRevisione(revisione, onAnnulla = vm::annullaCaricamento, onSalva = vm::salva)
        return
    }

    BackHandler(enabled = scheda != Scheda.RIEPILOGO) { vm.vaiA(Scheda.RIEPILOGO) }

    val cedolini = cedoliniOpz
    if (cedolini == null) {
        // database in apertura (pochi millisecondi): sfondo neutro invece dello stato vuoto
        Box(Modifier.fillMaxSize().background(Colori.Sfondo))
        return
    }

    Scaffold(
        containerColor = Colori.Sfondo,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Colori.Sfondo).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { Logo(30.dp) }
                Icon(Icons.Filled.Lock, "Dati solo sul telefono", tint = Colori.Verde, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("offline", fontSize = 11.sp, color = Colori.TestoSecondario)
            }
        },
        bottomBar = {
            Column(Modifier.background(Colori.Sfondo)) {
                if (scheda == Scheda.RIEPILOGO && cedolini.isNotEmpty()) {
                    Button(
                        onClick = carica,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.UploadFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Carica un nuovo cedolino", fontWeight = FontWeight.Bold)
                    }
                }
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    val colori = NavigationBarItemDefaults.colors(
                        selectedIconColor = Colori.Blu, selectedTextColor = Colori.Blu, indicatorColor = Colori.BluChiaro,
                    )
                    NavigationBarItem(scheda == Scheda.RIEPILOGO, { vm.vaiA(Scheda.RIEPILOGO) }, { Icon(Icons.Filled.Home, null) }, label = { Text("Riepilogo") }, colors = colori)
                    NavigationBarItem(scheda == Scheda.STORICO, { vm.vaiA(Scheda.STORICO) }, { Icon(Icons.Filled.History, null) }, label = { Text("Storico") }, colors = colori)
                    NavigationBarItem(scheda == Scheda.IMPOSTAZIONI, { vm.vaiA(Scheda.IMPOSTAZIONI) }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Impostazioni") }, colors = colori)
                }
            }
        },
    ) { padding ->
        when (scheda) {
            Scheda.RIEPILOGO -> SchermataRiepilogo(
                cedolini, selezionato, impostazioni, padding,
                onCarica = carica, onEsempio = vm::caricaEsempi, onSeleziona = vm::seleziona,
                onModifica = vm::modifica, onElimina = vm::elimina,
            )
            Scheda.STORICO -> SchermataStorico(cedolini, impostazioni, padding, onSeleziona = vm::seleziona)
            Scheda.IMPOSTAZIONI -> SchermataImpostazioni(
                impostazioni, cedolini.size, padding,
                onCambia = vm::aggiornaImpostazioni, onEsempi = vm::caricaEsempi, onEliminaTutto = vm::eliminaTutto,
            )
        }
    }

    when (val s = caricamento) {
        is StatoCaricamento.Elaborazione -> Dialog(onDismissRequest = {}) {
            Column(
                Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Colori.Blu)
                Spacer(Modifier.height(16.dp))
                Text(s.messaggio, fontWeight = FontWeight.SemiBold)
                Text("Elaborazione sul telefono, nessun invio dati.", fontSize = 12.sp, color = Colori.TestoSecondario)
            }
        }
        is StatoCaricamento.Password -> DialogoPassword(s.errata, onAnnulla = vm::annullaCaricamento) { vm.caricaDocumento(s.uri, it) }
        is StatoCaricamento.Errore -> AlertDialog(
            onDismissRequest = vm::annullaCaricamento,
            title = { Text("Cedolino non letto") },
            text = { Text(s.messaggio) },
            confirmButton = { TextButton(onClick = vm::annullaCaricamento) { Text("OK") } },
        )
        else -> Unit
    }
}

@Composable
private fun DialogoPassword(errata: Boolean, onAnnulla: () -> Unit, onConferma: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("PDF protetto") },
        text = {
            Column {
                Text(
                    if (errata) "Password errata, riprova." else "Il cedolino è protetto da password (spesso il codice fiscale o la data di nascita).",
                    color = if (errata) Colori.Rosso else Colori.Testo, fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, singleLine = true, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(pw) }, enabled = pw.isNotEmpty()) { Text("Apri") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
    )
}
