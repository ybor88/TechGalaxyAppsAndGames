package com.scouttable.app.ui.drive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scouttable.app.data.drive.BackupManager
import kotlinx.coroutines.launch

/**
 * Backup/ripristino tramite il selettore di file di sistema: l'utente sceglie "Google Drive"
 * (o qualunque altra posizione) come farebbe salvando un allegato da qualsiasi app. Nessun
 * accesso/Sign-In richiesto lato app.
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup dati") },
        text = {
            Column {
                Text(
                    "Esporta un file di backup e salvalo dove preferisci (es. una cartella su " +
                        "Google Drive, se l'app Drive è installata comparirà tra le posizioni " +
                        "disponibili). Per ripristinare, importa quel file su questo o un altro " +
                        "dispositivo.",
                )
                statusMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = { exportLauncher.launch(BackupManager.BACKUP_FILE_NAME) }) {
                Text("Esporta backup")
            }
        },
        dismissButton = {
            Column {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { importLauncher.launch(arrayOf(BackupManager.BACKUP_MIME_TYPE, "application/x-sqlite3", "*/*")) },
                ) {
                    Text("Importa backup")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onDismiss) {
                    Text("Chiudi")
                }
            }
        },
    )
}
