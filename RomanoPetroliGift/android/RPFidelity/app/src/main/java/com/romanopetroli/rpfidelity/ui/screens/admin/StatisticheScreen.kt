package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.data.model.RegistrazioneMese
import com.romanopetroli.rpfidelity.data.model.RiscattoCatalogo
import com.romanopetroli.rpfidelity.ui.screens.formatPunti
import com.romanopetroli.rpfidelity.ui.theme.RpGold
import com.romanopetroli.rpfidelity.ui.theme.RpGrayBg
import com.romanopetroli.rpfidelity.ui.theme.RpOrange
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticheScreen(adminViewModel: AdminViewModel, onOpenDrawer: () -> Unit) {
    val statistiche by adminViewModel.statistiche.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()

    LaunchedEffect(Unit) { adminViewModel.caricaStatistiche() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Statistiche",
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
            statistiche?.let { s ->
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Clienti", s.contaClienti.toString(), Modifier.weight(1f))
                        StatTile("Punti in circolo", formatPunti(s.puntiInCircolazione), Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatTile("Voucher riscattati", s.voucherRiscattati.toString(), Modifier.weight(1f))
                        StatTile("Voucher usati", s.voucherUsati.toString(), Modifier.weight(1f))
                    }

                    Text(
                        "Riscatti per premio",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
                items(s.riscattiPerCatalogo) { r -> RiscattoBar(r, s.riscattiPerCatalogo.maxOfOrNull { it.totale } ?: 0) }

                item {
                    Text(
                        "Nuove registrazioni (ultimi 6 mesi)",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
                items(s.registrazioniPerMese) { r -> RegistrazioneBar(r, s.registrazioniPerMese.maxOfOrNull { it.totale } ?: 0) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, valore: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valore, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun RiscattoBar(riscatto: RiscattoCatalogo, massimo: Int) {
    val frazione = if (massimo > 0) riscatto.totale.toFloat() / massimo.toFloat() else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("${riscatto.nome} (${riscatto.costoPunti} punti) — ${riscatto.totale}")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .padding(top = 4.dp)
                .background(RpGrayBg, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (frazione > 0f) frazione.coerceAtLeast(0.03f) else 0f)
                    .height(10.dp)
                    .background(RpOrange, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun RegistrazioneBar(registrazione: RegistrazioneMese, massimo: Int) {
    val frazione = if (massimo > 0) registrazione.totale.toFloat() / massimo.toFloat() else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("${registrazione.mese} — ${registrazione.totale}")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .padding(top = 4.dp)
                .background(RpGrayBg, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (frazione > 0f) frazione.coerceAtLeast(0.03f) else 0f)
                    .height(10.dp)
                    .background(RpGold, RoundedCornerShape(4.dp))
            )
        }
    }
}
