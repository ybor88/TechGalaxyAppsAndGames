package com.opticpro.suite.ui.calculators

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Criterion(val label: String, val points: Int)

private val criteria = listOf(
    Criterion("Scompenso cardiaco/disfunzione VS", 1),
    Criterion("Ipertensione", 1),
    Criterion("Età ≥75 anni", 2),
    Criterion("Diabete mellito", 1),
    Criterion("Ictus/TIA/tromboembolismo pregresso", 2),
    Criterion("Patologia vascolare (IMA, PAD, placca aortica)", 1),
    Criterion("Età 65-74 anni", 1),
    Criterion("Sesso femminile", 1),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChadsVascCalculatorScreen(onBack: () -> Unit) {
    val checked = remember { mutableStateListOf(*BooleanArray(criteria.size).toTypedArray()) }
    val score = criteria.indices.sumOf { if (checked[it]) criteria[it].points else 0 }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("CHA₂DS₂-VASc") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            criteria.forEachIndexed { i, c ->
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                    Text("${c.label} (+${c.points})")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Punteggio: $score", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        }
    }
}
