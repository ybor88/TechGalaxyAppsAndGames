package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClienteDettaglioScreen(adminViewModel: AdminViewModel, onBack: () -> Unit) {
    val cliente by adminViewModel.clienteSelezionato.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()

    var nome by remember(cliente) { mutableStateOf(cliente?.nome ?: "") }
    var cognome by remember(cliente) { mutableStateOf(cliente?.cognome ?: "") }
    var email by remember(cliente) { mutableStateOf(cliente?.email ?: "") }
    var telefono by remember(cliente) { mutableStateOf(cliente?.telefono ?: "") }
    var sospeso by remember(cliente) { mutableStateOf(cliente?.stato == "sospeso") }
    var confermaEliminazione by remember { mutableStateOf(false) }

    if (confermaEliminazione) {
        AlertDialog(
            onDismissRequest = { confermaEliminazione = false },
            title = { Text("Eliminare il cliente?") },
            text = { Text("Questa azione è definitiva e rimuove l'account di ${cliente?.nome} ${cliente?.cognome}.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confermaEliminazione = false
                        cliente?.let { adminViewModel.eliminaCliente(it.id, onBack) }
                    }
                ) { Text("Elimina") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confermaEliminazione = false }) { Text("Annulla") }
            }
        )
    }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Modifica cliente",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (cliente == null) {
                Text("Nessun cliente selezionato.")
                return@Column
            }

            OutlinedTextField(
                value = nome, onValueChange = { nome = it }, label = { Text("Nome") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cognome, onValueChange = { cognome = it }, label = { Text("Cognome") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = telefono, onValueChange = { telefono = it }, label = { Text("Telefono") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = sospeso, onCheckedChange = { sospeso = it })
                Text("Account sospeso")
            }

            if (!error.isNullOrBlank()) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            }

            Button(
                onClick = {
                    cliente?.let {
                        adminViewModel.aggiornaCliente(
                            it.id, nome, cognome, email, telefono,
                            if (sospeso) "sospeso" else "attivo",
                            onBack
                        )
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Salva modifiche") }

            OutlinedButton(
                onClick = { confermaEliminazione = true },
                enabled = !loading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Elimina cliente") }
        }
    }
}
