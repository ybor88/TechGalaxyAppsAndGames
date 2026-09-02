package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificaVoucherScreen(adminViewModel: AdminViewModel, onBack: () -> Unit) {
    var codice by remember { mutableStateOf("") }
    var scannerAperto by remember { mutableStateOf(false) }

    val voucher by adminViewModel.voucherVerificato.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()

    if (scannerAperto) {
        QrScannerDialog(
            onResult = { code ->
                codice = code
                scannerAperto = false
                adminViewModel.verificaVoucher(code)
            },
            onDismiss = { scannerAperto = false }
        )
    }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Verifica voucher",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Inserisci o scansiona il codice del voucher mostrato dal cliente")

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codice, onValueChange = { codice = it }, label = { Text("Codice voucher") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { scannerAperto = true }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Scansiona")
                }
            }
            Button(
                onClick = { adminViewModel.verificaVoucher(codice.trim()) },
                enabled = !loading && codice.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Cerca") }

            if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            if (!error.isNullOrBlank()) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }

            voucher?.let { v ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cliente: ${v.optString("cliente_nome")} ${v.optString("cliente_cognome")}", fontWeight = FontWeight.Bold)
                        Text("Voucher: ${v.optString("nome")} (%.2f€)".format(v.optDouble("importo_premio", 0.0)))
                        Text("Scadenza: ${v.optString("data_scadenza")}")
                        Text("Stato: ${v.optString("stato")}")

                        if (v.optString("stato") == "attivo") {
                            Button(
                                onClick = { adminViewModel.usaVoucher(v.optInt("id")) { codice = "" } },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) { Text("Segna come usato") }
                        }
                    }
                }
            }
        }
    }
}
