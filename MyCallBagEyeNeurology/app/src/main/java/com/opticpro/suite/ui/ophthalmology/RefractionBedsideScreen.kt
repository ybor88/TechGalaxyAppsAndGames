package com.opticpro.suite.ui.ophthalmology

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Retinoscopia al comodino: neutralizza il riflesso e sottrae la correzione per la distanza di lavoro per ottenere il potere netto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefractionBedsideScreen(onBack: () -> Unit) {
    var grossPower by remember { mutableStateOf("") }
    var workingDistanceCm by remember { mutableStateOf("67") }

    val gross = grossPower.toDoubleOrNull()
    val wd = workingDistanceCm.toDoubleOrNull()
    val workingDistanceCorrection = if (wd != null && wd > 0) 100.0 / wd else null
    val netPower = if (gross != null && workingDistanceCorrection != null) gross - workingDistanceCorrection else null

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Refrazione approssimata al comodino") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Esegui la retinoscopia con lenti di neutralizzazione al letto del paziente. Inserisci il potere " +
                    "lordo di neutralizzazione e la distanza di lavoro per ottenere il potere netto (retinoscopia)."
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                grossPower, { grossPower = it },
                label = { Text("Potere lordo di neutralizzazione (D)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                workingDistanceCm, { workingDistanceCm = it },
                label = { Text("Distanza di lavoro (cm)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            workingDistanceCorrection?.let {
                Text("Correzione distanza di lavoro: -%.2f D".format(it), style = MaterialTheme.typography.bodyMedium)
            }
            netPower?.let {
                Text("Potere netto stimato: %.2f D".format(it), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
