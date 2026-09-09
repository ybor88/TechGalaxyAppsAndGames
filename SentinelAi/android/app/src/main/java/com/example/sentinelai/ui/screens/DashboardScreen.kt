package com.example.sentinelai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sentinelai.ui.components.FeatureTile
import com.example.sentinelai.ui.components.ScreenHeader
import com.example.sentinelai.ui.components.SectionTitle
import com.example.sentinelai.ui.components.ShieldStatusCard
import com.example.sentinelai.ui.components.StatCard
import com.example.sentinelai.ui.theme.SentinelBlue
import com.example.sentinelai.viewmodel.SentinelViewModel

private data class Tile(val icon: String, val title: String, val subtitle: String, val route: String)

private val TILES = listOf(
    Tile("🧠", "Rilevamento AI Avanzato", "Analisi euristica con punteggio di rischio", "scan"),
    Tile("⚡", "Protezione in Tempo Reale", "Monitoraggio live della cartella protetta", "realtime"),
    Tile("🎯", "Difesa Predittiva", "Blocco automatico oltre la soglia di rischio", "settings"),
    Tile("👁", "Privacy e Controllo", "Audit permessi delle app installate", "privacy"),
    Tile("☁", "Protezione Cloud e Offline", "Firme locali sempre attive, verifica cloud opzionale", "cloud"),
    Tile("📱", "Sicurezza Multipiattaforma", "Sincronizza impostazioni con il desktop", "settings")
)

@Composable
fun DashboardScreen(viewModel: SentinelViewModel, onNavigate: (String) -> Unit) {
    val dashboard by viewModel.dashboard.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("Dashboard", "Panoramica dello stato di protezione del sistema") }

        item {
            ShieldStatusCard(
                protected = dashboard.realtimeActive,
                subtitle = if (dashboard.realtimeActive) "Protezione in tempo reale attiva"
                else "Attiva la protezione in tempo reale nella sezione dedicata"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("File scansionati oggi", dashboard.filesScannedToday.toString(), Modifier.weight(1f))
                StatCard("Minacce oggi", dashboard.threatsToday.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("In quarantena", dashboard.quarantineCount.toString(), Modifier.weight(1f))
                StatCard("Versione DB", dashboard.signatureVersion, Modifier.weight(1f))
            }
        }

        item { SectionTitle("Funzionalità di protezione") }

        items(TILES.chunked(2)) { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (tile in rowTiles) {
                    FeatureTile(
                        icon = tile.icon,
                        title = tile.title,
                        subtitle = tile.subtitle,
                        onClick = { onNavigate(tile.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onNavigate("scan") },
                colors = ButtonDefaults.buttonColors(containerColor = SentinelBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scansione rapida")
            }
        }
    }
}
