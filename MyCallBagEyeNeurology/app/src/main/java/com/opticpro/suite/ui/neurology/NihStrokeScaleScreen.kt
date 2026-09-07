package com.opticpro.suite.ui.neurology

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class NihsItem(val label: String, val maxScore: Int)

private val items = listOf(
    NihsItem("1a. Livello di coscienza", 3),
    NihsItem("1b. Domande LOC", 2),
    NihsItem("1c. Comandi LOC", 2),
    NihsItem("2. Sguardo", 2),
    NihsItem("3. Visus", 3),
    NihsItem("4. Paresi facciale", 3),
    NihsItem("5a. Motorio arto sup. SX", 4),
    NihsItem("5b. Motorio arto sup. DX", 4),
    NihsItem("6a. Motorio arto inf. SX", 4),
    NihsItem("6b. Motorio arto inf. DX", 4),
    NihsItem("7. Atassia arti", 2),
    NihsItem("8. Sensibilità", 2),
    NihsItem("9. Linguaggio", 3),
    NihsItem("10. Disartria", 2),
    NihsItem("11. Estinzione/negligenza", 2),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NihStrokeScaleScreen(onBack: () -> Unit) {
    val scores = remember { mutableStateListOf(*IntArray(items.size).toTypedArray()) }
    val total = scores.sum()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("NIH Stroke Scale") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f)) {
                items(items.indices.toList()) { i ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("${items[i].label}  (score: ${scores[i]})")
                        Slider(
                            value = scores[i].toFloat(),
                            onValueChange = { scores[i] = it.toInt() },
                            valueRange = 0f..items[i].maxScore.toFloat(),
                            steps = items[i].maxScore - 1
                        )
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Punteggio totale NIHSS: $total",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
