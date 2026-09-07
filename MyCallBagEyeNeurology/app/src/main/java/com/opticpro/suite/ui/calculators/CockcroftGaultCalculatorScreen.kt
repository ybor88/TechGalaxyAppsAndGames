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
fun CockcroftGaultCalculatorScreen(onBack: () -> Unit) {
    var ageStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var creatinineStr by remember { mutableStateOf("") } // mg/dL
    var isFemale by remember { mutableStateOf(false) }

    val age = ageStr.toDoubleOrNull()
    val weight = weightStr.toDoubleOrNull()
    val creatinine = creatinineStr.toDoubleOrNull()

    val clearance = if (age != null && weight != null && creatinine != null && creatinine > 0) {
        val base = (140 - age) * weight / (72 * creatinine)
        if (isFemale) base * 0.85 else base
    } else null

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cockcroft-Gault") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(ageStr, { ageStr = it }, label = { Text("Età (anni)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(weightStr, { weightStr = it }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(creatinineStr, { creatinineStr = it }, label = { Text("Creatinina sierica (mg/dL)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isFemale, onCheckedChange = { isFemale = it })
                Text("Sesso femminile")
            }
            Spacer(Modifier.height(24.dp))
            if (clearance != null) {
                Text("Clearance creatinina: %.1f mL/min".format(clearance), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
