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
fun BmiCalculatorScreen(onBack: () -> Unit) {
    var weightKg by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    val w = weightKg.toDoubleOrNull()
    val h = heightCm.toDoubleOrNull()?.div(100)
    val bmi = if (w != null && h != null && h > 0) w / (h * h) else null

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("BMI Calculator") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(weightKg, { weightKg = it }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(heightCm, { heightCm = it }, label = { Text("Altezza (cm)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            if (bmi != null) {
                Text("BMI: %.1f".format(bmi), style = MaterialTheme.typography.headlineSmall)
                val category = when {
                    bmi < 18.5 -> "Sottopeso"
                    bmi < 25.0 -> "Normopeso"
                    bmi < 30.0 -> "Sovrappeso"
                    else -> "Obesità"
                }
                Text(category, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
