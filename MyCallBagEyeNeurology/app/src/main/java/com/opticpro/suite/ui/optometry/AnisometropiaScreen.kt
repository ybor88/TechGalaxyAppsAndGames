package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnisometropiaScreen(onBack: () -> Unit) {
    var odSphere by remember { mutableStateOf("") }
    var osSphere by remember { mutableStateOf("") }
    var odCylinder by remember { mutableStateOf("") }
    var osCylinder by remember { mutableStateOf("") }

    val odSe = (odSphere.toDoubleOrNull() ?: 0.0) + (odCylinder.toDoubleOrNull() ?: 0.0) / 2
    val osSe = (osSphere.toDoubleOrNull() ?: 0.0) + (osCylinder.toDoubleOrNull() ?: 0.0) / 2
    val diff = kotlin.math.abs(odSe - osSe)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Anisometropia") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Inserisci sfera e cilindro per calcolare l'equivalente sferico e la differenza tra i due occhi.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(odSphere, { odSphere = it }, label = { Text("OD Sfera (D)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(odCylinder, { odCylinder = it }, label = { Text("OD Cilindro (D)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(osSphere, { osSphere = it }, label = { Text("OS Sfera (D)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(osCylinder, { osCylinder = it }, label = { Text("OS Cilindro (D)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Text("Equivalente sferico OD: %.2f D".format(odSe), style = MaterialTheme.typography.titleMedium)
            Text("Equivalente sferico OS: %.2f D".format(osSe), style = MaterialTheme.typography.titleMedium)
            Text("Anisometropia: %.2f D".format(diff), style = MaterialTheme.typography.titleLarge)
            if (diff >= 2.0) {
                Text("Clinicamente significativa (≥2D): valutare rischio aniseiconia.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
