package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Punto prossimo di accommodazione: l'operatore avvicina un target fino a quando il
 * paziente riferisce sfocatura persistente; la distanza misurata determina l'ampiezza
 * accomodativa (D = 100 / distanza in cm).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearPointAccommodationScreen(onBack: () -> Unit) {
    var distanceCm by remember { mutableStateOf(10f) }
    val amplitudeD = 100f / distanceCm

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Near point of accommodation") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Avvicina il testo/target verso il paziente. Quando riferisce sfocatura persistente, imposta la distanza misurata.")
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("W I S B A L", style = MaterialTheme.typography.headlineMedium)
                    Text("Testo di lettura ravvicinata (near text)", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Distanza punto prossimo: ${distanceCm.toInt()} cm")
            Slider(value = distanceCm, onValueChange = { distanceCm = it }, valueRange = 5f..50f)
            Spacer(Modifier.height(24.dp))
            Text("Ampiezza di accomodazione: %.1f D".format(amplitudeD), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
