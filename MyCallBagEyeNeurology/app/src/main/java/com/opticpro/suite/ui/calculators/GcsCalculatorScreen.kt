package com.opticpro.suite.ui.calculators

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GcsCalculatorScreen(onBack: () -> Unit) {
    var eye by remember { mutableStateOf(4) }
    var verbal by remember { mutableStateOf(5) }
    var motor by remember { mutableStateOf(6) }
    val total = eye + verbal + motor

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Glasgow Coma Scale") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Apertura occhi: $eye/4")
            Slider(value = eye.toFloat(), onValueChange = { eye = it.toInt() }, valueRange = 1f..4f, steps = 2)
            Text("Risposta verbale: $verbal/5")
            Slider(value = verbal.toFloat(), onValueChange = { verbal = it.toInt() }, valueRange = 1f..5f, steps = 3)
            Text("Risposta motoria: $motor/6")
            Slider(value = motor.toFloat(), onValueChange = { motor = it.toInt() }, valueRange = 1f..6f, steps = 4)
            Spacer(Modifier.height(24.dp))
            Text("GCS totale: $total/15", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
