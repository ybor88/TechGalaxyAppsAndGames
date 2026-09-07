package com.opticpro.suite.ui.calculators

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class WellsPeCriterion(val label: String, val points: Double)

private val peCriteria = listOf(
    WellsPeCriterion("Segni clinici di TVP", 3.0),
    WellsPeCriterion("Diagnosi alternativa meno probabile della PE", 3.0),
    WellsPeCriterion("Frequenza cardiaca >100 bpm", 1.5),
    WellsPeCriterion("Immobilizzazione >3gg o chirurgia nelle 4 settimane precedenti", 1.5),
    WellsPeCriterion("TVP/PE pregressa", 1.5),
    WellsPeCriterion("Emottisi", 1.0),
    WellsPeCriterion("Neoplasia attiva (trattamento entro 6 mesi o palliativo)", 1.0),
)

/** Wells' Criteria per Embolia Polmonare (PE). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellsPeCalculatorScreen(onBack: () -> Unit) {
    val checked = remember { mutableStateListOf(*BooleanArray(peCriteria.size).toTypedArray()) }
    val score = peCriteria.indices.sumOf { if (checked[it]) peCriteria[it].points else 0.0 }
    val risk = when {
        score > 6.0 -> "Alta probabilità"
        score >= 2.0 -> "Probabilità moderata"
        else -> "Bassa probabilità"
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Wells' Criteria - PE") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            peCriteria.forEachIndexed { i, c ->
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                    Text("${c.label} (+${c.points})")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Punteggio: %.1f  →  %s".format(score, risk), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        }
    }
}
