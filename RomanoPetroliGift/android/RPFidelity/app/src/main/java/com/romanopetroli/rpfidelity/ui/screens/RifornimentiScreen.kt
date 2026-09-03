package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.viewmodel.ClienteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RifornimentiScreen(clienteViewModel: ClienteViewModel, onOpenDrawer: () -> Unit) {
    var dal by remember { mutableStateOf("") }
    var al by remember { mutableStateOf("") }

    val rifornimenti by clienteViewModel.rifornimenti.collectAsState()
    val loading by clienteViewModel.loading.collectAsState()
    val error by clienteViewModel.error.collectAsState()

    LaunchedEffect(Unit) { clienteViewModel.caricaRifornimenti() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "I miei rifornimenti",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dal, onValueChange = { dal = it }, label = { Text("Dal (yyyy-mm-dd)") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = al, onValueChange = { al = it }, label = { Text("Al (yyyy-mm-dd)") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = { clienteViewModel.caricaRifornimenti(dal, al) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Cerca") }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            if (!error.isNullOrBlank()) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
            if (!loading && rifornimenti.isEmpty()) {
                Text("Nessun rifornimento trovato.", modifier = Modifier.padding(top = 24.dp))
            }

            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(rifornimenti) { r ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r.dataOra, fontWeight = FontWeight.Bold)
                                Text("+${formatPunti(r.puntiMaturati)} punti")
                            }
                            Text("Importo: %.2f€  •  Voucher: %.2f€  •  Pagato: %.2f€".format(r.importo, r.importoVoucher, r.importoPagato))
                        }
                    }
                }
            }
        }
    }
}
