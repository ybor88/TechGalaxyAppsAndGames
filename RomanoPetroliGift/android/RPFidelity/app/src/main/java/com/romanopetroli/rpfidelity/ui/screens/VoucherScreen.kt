package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.romanopetroli.rpfidelity.data.model.Voucher
import com.romanopetroli.rpfidelity.data.model.VoucherCatalogo
import com.romanopetroli.rpfidelity.ui.theme.RpOrange
import com.romanopetroli.rpfidelity.viewmodel.ClienteViewModel
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherScreen(
    sessionViewModel: SessionViewModel,
    clienteViewModel: ClienteViewModel,
    onBack: () -> Unit
) {
    val user by sessionViewModel.user.collectAsState()
    val catalogo by clienteViewModel.catalogo.collectAsState()
    val mieiVoucher by clienteViewModel.mieiVoucher.collectAsState()
    val loading by clienteViewModel.loading.collectAsState()
    val error by clienteViewModel.error.collectAsState()
    val messaggio by clienteViewModel.messaggio.collectAsState()

    LaunchedEffect(Unit) { clienteViewModel.caricaVoucher() }

    val puntiSaldo = user?.puntiSaldo ?: 0.0

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Voucher",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Il tuo saldo: ${formatPunti(puntiSaldo)} punti", fontWeight = FontWeight.Bold)
                if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
                if (!error.isNullOrBlank()) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
                if (!messaggio.isNullOrBlank()) {
                    Text(messaggio ?: "", color = RpOrange, modifier = Modifier.padding(top = 12.dp))
                }
                Text("Catalogo voucher", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
            items(catalogo) { v ->
                CatalogoCard(v, puntiSaldo) {
                    clienteViewModel.riscatta(v.id) { sessionViewModel.refreshUser() }
                }
            }
            item {
                Text("I miei voucher", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                if (mieiVoucher.isEmpty()) {
                    Text("Non hai ancora riscattato nessun voucher.")
                }
            }
            items(mieiVoucher) { v -> MioVoucherCard(v) }
        }
    }
}

@Composable
private fun CatalogoCard(v: VoucherCatalogo, puntiSaldo: Double, onRiscatta: () -> Unit) {
    val raggiungibile = puntiSaldo >= v.costoPunti
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("%.2f€".format(v.importoPremio), fontWeight = FontWeight.Bold, color = RpOrange)
            Text(v.nome)
            Text("Costo: ${v.costoPunti} punti")
            if (!raggiungibile) {
                Text(
                    "Ti mancano ${formatPunti(v.costoPunti - puntiSaldo)} punti",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(onClick = onRiscatta, enabled = raggiungibile, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (raggiungibile) "Riscatta" else "Punti insufficienti")
            }
        }
    }
}

@Composable
private fun MioVoucherCard(v: Voucher) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" +
                URLEncoder.encode(v.codiceVoucher, "UTF-8")
            AsyncImage(model = qrUrl, contentDescription = "QR voucher", modifier = Modifier.size(120.dp))
            Text("${v.nome} — %.2f€".format(v.importoPremio), fontWeight = FontWeight.Bold)
            Text(v.codiceVoucher, fontFamily = FontFamily.Monospace)
            Text("Scadenza: ${v.dataScadenza}")
            Text(
                v.stato.replaceFirstChar { it.uppercase() },
                color = when (v.stato) {
                    "attivo" -> Color(0xFF1C6B3A)
                    "usato" -> Color(0xFF5B6180)
                    else -> Color(0xFFA12622)
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}
