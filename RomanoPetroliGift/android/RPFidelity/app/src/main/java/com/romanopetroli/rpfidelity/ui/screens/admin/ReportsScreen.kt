package com.romanopetroli.rpfidelity.ui.screens.admin

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(adminViewModel: AdminViewModel, onOpenDrawer: () -> Unit) {
    var dal by remember { mutableStateOf("") }
    var al by remember { mutableStateOf("") }

    val rifornimenti by adminViewModel.reportRifornimenti.collectAsState()
    val totali by adminViewModel.reportTotali.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()

    LaunchedEffect(Unit) { adminViewModel.caricaReports() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Reports",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Row {
                    OutlinedTextField(
                        value = dal, onValueChange = { dal = it }, label = { Text("Dal") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = al, onValueChange = { al = it }, label = { Text("Al") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = { adminViewModel.caricaReports(dal, al) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Cerca") }

                if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                if (!error.isNullOrBlank()) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                }

                totali?.let {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Totali del periodo", fontWeight = FontWeight.Bold)
                            Text("Totale rifornimenti: %.2f€".format(it.totaleRifornimenti))
                            Text("Totale voucher: %.2f€".format(it.totaleVoucher))
                            Text("Saldo: %.2f€".format(it.saldo))
                        }
                    }
                }

                Text("Dettaglio rifornimenti", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
            items(rifornimenti) { r ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(r.dataOra, fontWeight = FontWeight.Bold)
                        Text("Cliente: ${r.clienteNome ?: "-"} ${r.clienteCognome ?: ""}")
                        Text("Codice: ${r.codiceRifornimento}")
                        Text("Totale: %.2f€  •  Pagato: %.2f€  •  Voucher: %.2f€".format(r.importo, r.importoPagato, r.importoVoucher))
                    }
                }
            }
        }
    }
}
