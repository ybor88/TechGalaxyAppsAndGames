package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.ui.screens.QrScannerDialog
import com.romanopetroli.rpfidelity.ui.screens.formatPunti
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

private const val IMPORTO_MASSIMO = 150.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistraRifornimentoScreen(adminViewModel: AdminViewModel, onBack: () -> Unit) {
    var codiceCard by remember { mutableStateOf("") }
    var codiceVoucher by remember { mutableStateOf("") }
    var importo by remember { mutableStateOf("") }
    var scannerAperto by remember { mutableStateOf<String?>(null) } // "card" | "voucher" | null

    val cliente by adminViewModel.clienteIdentificato.collectAsState()
    val carrello by adminViewModel.carrelloVoucher.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()
    val successo by adminViewModel.successo.collectAsState()

    val totaleVoucher = carrello.sumOf { it.importoPremio }

    if (scannerAperto != null) {
        QrScannerDialog(
            onResult = { code ->
                if (scannerAperto == "card") codiceCard = code else codiceVoucher = code
                scannerAperto = null
            },
            onDismiss = { scannerAperto = null }
        )
    }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Registra rifornimento",
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
            Text("1 punto ogni 10€ pagati — massimo ${IMPORTO_MASSIMO.toInt()}€ per operazione")

            if (!error.isNullOrBlank()) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            if (!successo.isNullOrBlank()) {
                Text(successo ?: "", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
            }

            if (cliente == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = codiceCard,
                        onValueChange = { codiceCard = it },
                        label = { Text("Codice Card Cliente") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { scannerAperto = "card" }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Scansiona")
                    }
                }
                Button(
                    onClick = { adminViewModel.identificaCliente(codiceCard.trim()) },
                    enabled = !loading && codiceCard.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Identifica cliente") }
            } else {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${cliente!!.nome} ${cliente!!.cognome}", fontWeight = FontWeight.Bold)
                            Text("Saldo attuale: ${formatPunti(cliente!!.puntiSaldo)} punti")
                        }
                        TextButton(onClick = {
                            adminViewModel.cambiaCliente()
                            codiceCard = ""
                            codiceVoucher = ""
                            importo = ""
                        }) { Text("Cambia cliente") }
                    }
                }

                Text("Voucher applicati", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                if (carrello.isEmpty()) {
                    Text("Nessun voucher inserito.")
                } else {
                    carrello.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${v.nome} — %.2f€ (${v.codiceVoucher})".format(v.importoPremio))
                            TextButton(onClick = { adminViewModel.rimuoviVoucher(v.codiceVoucher) }) { Text("Rimuovi") }
                        }
                    }
                    Text("Totale voucher: %.2f€".format(totaleVoucher), fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = codiceVoucher,
                        onValueChange = { codiceVoucher = it },
                        label = { Text("Utilizza un voucher") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { scannerAperto = "voucher" }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Scansiona")
                    }
                }
                OutlinedButton(
                    onClick = {
                        adminViewModel.aggiungiVoucher(codiceVoucher.trim())
                        codiceVoucher = ""
                    },
                    enabled = !loading && codiceVoucher.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Aggiungi voucher") }

                OutlinedTextField(
                    value = importo,
                    onValueChange = { importo = it },
                    label = { Text("Importo rifornimento (€)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                )
                if (totaleVoucher > 0) {
                    Text("Verranno scalati %.2f€ di voucher dall'importo inserito.".format(totaleVoucher))
                }

                Button(
                    onClick = {
                        val valore = importo.replace(',', '.').toDoubleOrNull() ?: 0.0
                        adminViewModel.confermaRifornimento(valore) {
                            codiceCard = ""
                            codiceVoucher = ""
                            importo = ""
                        }
                    },
                    enabled = !loading && importo.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Conferma Rifornimento")
                    }
                }
            }
        }
    }
}
