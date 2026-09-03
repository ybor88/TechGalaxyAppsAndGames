package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.ui.theme.RpOrange
import com.romanopetroli.rpfidelity.viewmodel.ClienteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContattiScreen(clienteViewModel: ClienteViewModel, onOpenDrawer: () -> Unit) {
    var messaggio by remember { mutableStateOf("") }

    val distributore by clienteViewModel.distributore.collectAsState()
    val loading by clienteViewModel.loading.collectAsState()
    val error by clienteViewModel.error.collectAsState()
    val successo by clienteViewModel.messaggio.collectAsState()

    LaunchedEffect(Unit) { clienteViewModel.caricaContatti() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Contatti",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            distributore?.let { d ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(d.nome, fontWeight = FontWeight.Bold)
                        val indirizzo = listOfNotNull(d.indirizzo, d.citta).joinToString(", ")
                        if (indirizzo.isNotBlank()) {
                            Text(indirizzo)
                        }
                    }
                }
            }

            Text(
                "Scrivici",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )
            Text("Compila il modulo per una domanda sul programma fedeltà, sui punti o sui voucher.")

            OutlinedTextField(
                value = messaggio,
                onValueChange = { messaggio = it },
                label = { Text("Messaggio") },
                modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp)
            )

            if (!error.isNullOrBlank()) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            if (!successo.isNullOrBlank()) {
                Text(successo ?: "", color = RpOrange, modifier = Modifier.padding(top = 12.dp))
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            }

            Button(
                onClick = {
                    clienteViewModel.inviaMessaggio(messaggio) { messaggio = "" }
                },
                enabled = !loading && messaggio.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Invia messaggio") }
        }
    }
}
