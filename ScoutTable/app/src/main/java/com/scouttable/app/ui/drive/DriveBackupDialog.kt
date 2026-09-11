package com.scouttable.app.ui.drive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.scouttable.app.data.drive.BackupManager
import kotlinx.coroutines.launch

/**
 * Backup/ripristino tramite il selettore di file di sistema: l'utente sceglie "Google Drive"
 * (o qualunque altra posizione) come farebbe salvando un allegato da qualsiasi app. Nessun
 * accesso/Sign-In richiesto lato app.
 *
 * Dialog costruito a mano (invece del componente AlertDialog) perché con 3 azioni (Esporta,
 * Importa, Chiudi) il layout a due slot di AlertDialog tronca i pulsanti su schermi piccoli;
 * qui il testo sta in un'area con scroll indipendente e i pulsanti restano sempre visibili
 * sotto, mai tagliati.
 */
@Composable
fun DriveBackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.BACKUP_MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            statusMessage = "Esportazione in corso..."
            val result = BackupManager.exportTo(context, uri)
            statusMessage = if (result.isSuccess) {
                "Backup esportato."
            } else {
                "Errore: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            statusMessage = "Importazione in corso..."
            val result = BackupManager.importFrom(context, uri)
            statusMessage = if (result.isSuccess) {
                "Dati importati. Riavvia l'app per vederli."
            } else {
                "Errore: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // verticalScroll ha bisogno di un limite di altezza per far scattare davvero lo scroll
    // (senza un max esplicito la Column si limita a crescere quanto serve, e su schermi bassi o
    // in orizzontale il dialog finirebbe comunque tagliato dai bordi dello schermo).
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.widthIn(max = 560.dp).heightIn(max = maxDialogHeight)) {
            // Tutto il contenuto (testo + pulsanti) è dentro un'unica Column con scroll: cosi',
            // anche su schermi piccoli o in orizzontale, niente resta mai tagliato fuori dalla
            // vista — al massimo compare uno scroll, mai un troncamento.
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Backup dati", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text(
                    "Esporta un file di backup e salvalo dove preferisci (es. una cartella su " +
                        "Google Drive, se l'app Drive è installata comparirà tra le posizioni " +
                        "disponibili). Per ripristinare, importa quel file su questo o un altro " +
                        "dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                statusMessage?.let {
                    Text(it, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    onClick = { exportLauncher.launch(BackupManager.BACKUP_FILE_NAME) },
                ) {
                    Text("Esporta backup")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onClick = { importLauncher.launch(arrayOf(BackupManager.BACKUP_MIME_TYPE, "application/x-sqlite3", "*/*")) },
                ) {
                    Text("Importa backup")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onDismiss) {
                    Text("Chiudi")
                }
            }
        }
    }
}
