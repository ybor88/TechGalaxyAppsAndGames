package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.data.model.ConversazioneCliente
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessaggiScreen(adminViewModel: AdminViewModel, onOpenDrawer: () -> Unit, onApriConversazione: (Int) -> Unit) {
    val conversazioni by adminViewModel.conversazioni.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()

    LaunchedEffect(Unit) { adminViewModel.caricaConversazioni() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Messaggi",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                if (loading) CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                if (!error.isNullOrBlank()) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (!loading && conversazioni.isEmpty()) {
                    Text("Nessun messaggio ricevuto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(conversazioni) { c ->
                ConversazioneRow(c) { onApriConversazione(c.clienteId) }
            }
        }
    }
}

@Composable
private fun ConversazioneRow(conversazione: ConversazioneCliente, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${conversazione.nome} ${conversazione.cognome}", fontWeight = FontWeight.Bold)
            Text(
                (if (conversazione.ultimoMittente == "admin") "Tu: " else "") + conversazione.ultimoMessaggio,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
