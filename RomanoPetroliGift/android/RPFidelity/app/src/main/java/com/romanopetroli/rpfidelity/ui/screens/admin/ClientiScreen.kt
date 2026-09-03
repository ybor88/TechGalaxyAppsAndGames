package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.data.model.ClienteAdmin
import com.romanopetroli.rpfidelity.ui.screens.formatPunti
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientiScreen(adminViewModel: AdminViewModel, onOpenDrawer: () -> Unit, onSelezionaCliente: () -> Unit) {
    val clienti by adminViewModel.clienti.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()

    LaunchedEffect(Unit) { adminViewModel.caricaClienti() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Gestione clienti",
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
            }
            items(clienti) { c ->
                ClienteRow(c) {
                    adminViewModel.selezionaCliente(c)
                    onSelezionaCliente()
                }
            }
        }
    }
}

@Composable
private fun ClienteRow(cliente: ClienteAdmin, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${cliente.nome} ${cliente.cognome}", fontWeight = FontWeight.Bold)
                Text(
                    cliente.stato.replaceFirstChar { it.uppercase() },
                    color = if (cliente.stato == "attivo") androidx.compose.ui.graphics.Color(0xFF1C6B3A) else androidx.compose.ui.graphics.Color(0xFFA12622)
                )
            }
            Text(cliente.email)
            Text("${cliente.ruolo.replaceFirstChar { it.uppercase() }} • ${formatPunti(cliente.puntiSaldo)} punti")
        }
    }
}
