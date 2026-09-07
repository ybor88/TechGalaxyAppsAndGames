package com.opticpro.suite.ui.calculators

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.pow

/** CKD-EPI 2021 (creatinina, senza fattore razza). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CkdEpiCalculatorScreen(onBack: () -> Unit) {
    var ageStr by remember { mutableStateOf("") }
    var creatinineStr by remember { mutableStateOf("") } // mg/dL
    var isFemale by remember { mutableStateOf(false) }

    val age = ageStr.toDoubleOrNull()
    val scr = creatinineStr.toDoubleOrNull()

    val egfr = if (age != null && scr != null && age > 0 && scr > 0) {
        val kappa = if (isFemale) 0.7 else 0.9
        val alpha = if (isFemale) -0.241 else -0.302
        val sexFactor = if (isFemale) 1.012 else 1.0
        val minRatio = min(scr / kappa, 1.0)
        val maxRatio = kotlin.math.max(scr / kappa, 1.0)
        142 * minRatio.pow(alpha) * maxRatio.pow(-1.200) * 0.9938.pow(age) * sexFactor
    } else null

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("CKD-EPI 2021") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(ageStr, { ageStr = it }, label = { Text("Età (anni)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(creatinineStr, { creatinineStr = it }, label = { Text("Creatinina sierica (mg/dL)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isFemale, onCheckedChange = { isFemale = it })
                Text("Sesso femminile")
            }
            Spacer(Modifier.height(24.dp))
            egfr?.let {
                Text("eGFR: %.0f mL/min/1.73m²".format(it), style = MaterialTheme.typography.headlineSmall)
                val stage = when {
                    it >= 90 -> "G1 - Normale"
                    it >= 60 -> "G2 - Lievemente ridotto"
                    it >= 45 -> "G3a - Lieve-moderata riduzione"
                    it >= 30 -> "G3b - Moderata-severa riduzione"
                    it >= 15 -> "G4 - Severa riduzione"
                    else -> "G5 - Insufficienza renale"
                }
                Text(stage, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
