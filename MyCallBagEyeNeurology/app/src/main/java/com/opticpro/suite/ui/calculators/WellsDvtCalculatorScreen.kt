package com.opticpro.suite.ui.calculators

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class WellsCriterion(val label: String, val points: Int)

private val dvtCriteria = listOf(
    WellsCriterion("Cancro attivo (trattamento in corso o entro 6 mesi)", 1),
    WellsCriterion("Paralisi/paresi o immobilizzazione recente arto inferiore", 1),
    WellsCriterion("Allettamento >3gg o chirurgia maggiore <12 settimane", 1),
    WellsCriterion("Dolorabilità localizzata lungo il sistema venoso profondo", 1),
    WellsCriterion("Gonfiore di tutto l'arto inferiore", 1),
    WellsCriterion("Gonfiore del polpaccio >3cm rispetto al controlaterale", 1),
    WellsCriterion("Edema improntabile limitato all'arto sintomatico", 1),
    WellsCriterion("Vene collaterali superficiali (non varicose)", 1),
    WellsCriterion("TVP pregressa documentata", 1),
    WellsCriterion("Diagnosi alternativa altrettanto o più probabile", -2),
)

/** Wells' Criteria per Trombosi Venosa Profonda (TVP/DVT). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellsDvtCalculatorScreen(onBack: () -> Unit) {
    val checked = remember { mutableStateListOf(*BooleanArray(dvtCriteria.size).toTypedArray()) }
    val score = dvtCriteria.indices.sumOf { if (checked[it]) dvtCriteria[it].points else 0 }
    val risk = when {
        score >= 3 -> "Alta probabilità"
        score in 1..2 -> "Probabilità moderata"
        else -> "Bassa probabilità"
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Wells' Criteria - TVP") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            dvtCriteria.forEachIndexed { i, c ->
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                    Text("${c.label} (${if (c.points > 0) "+" else ""}${c.points})")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Punteggio: $score  →  $risk", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        }
    }
}
