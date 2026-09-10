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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.scouttable.app.data.drive.DriveSyncManager
import kotlinx.coroutines.launch

@Composable
fun DriveBackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(DriveSyncManager.lastSignedInAccount(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching {
            account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(Exception::class.java)
        }.onFailure {
            statusMessage = "Accesso non riuscito: ${it.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup su Google Drive") },
        text = {
            Column {
                Text(
                    if (account == null) {
                        "Accedi con un account Google per salvare/ripristinare il database di ScoutTable " +
                            "nella cartella privata dell'app su Drive."
                    } else {
                        "Account: ${account?.email}"
                    }
                )
                statusMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (account == null) {
                Button(onClick = { signInLauncher.launch(DriveSyncManager.signInClient(context).signInIntent) }) {
                    Text("Accedi")
                }
            } else {
                Button(onClick = {
                    scope.launch {
                        statusMessage = "Backup in corso..."
                        val result = DriveSyncManager.backup(context, account!!)
                        statusMessage = if (result.isSuccess) "Backup completato." else "Errore: ${result.exceptionOrNull()?.message}"
                    }
                }) {
                    Text("Backup ora")
                }
            }
        },
        dismissButton = {
            if (account != null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            statusMessage = "Ripristino in corso..."
                            val result = DriveSyncManager.restore(context, account!!)
                            statusMessage = if (result.isSuccess) {
                                "Ripristino completato. Riavvia l'app per vedere i dati."
                            } else {
                                "Errore: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                ) {
                    Text("Ripristina ultimo backup")
                }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Chiudi") }
            }
        },
    )
}
