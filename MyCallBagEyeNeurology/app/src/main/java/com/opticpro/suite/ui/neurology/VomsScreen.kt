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

private data class VomsItem(val label: String)

private val vomsComponents = listOf(
    VomsItem("Inseguimento lento (smooth pursuit)"),
    VomsItem("Saccadi orizzontali"),
    VomsItem("Saccadi verticali"),
    VomsItem("Convergenza (near point)"),
    VomsItem("VOR orizzontale"),
    VomsItem("VOR verticale"),
    VomsItem("Sensibilità al movimento visivo"),
)

/** Vestibular/Ocular Motor Screening: punteggio sintomi 0-10 per componente (mal di testa, capogiro, nausea, annebbiamento). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VomsScreen(onBack: () -> Unit) {
    val scores = remember { mutableStateListOf(*IntArray(vomsComponents.size).toTypedArray()) }
    var npcCm by remember { mutableStateOf("") }
    val total = scores.sum()
    val npc = npcCm.toDoubleOrNull()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("VOMS") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f)) {
                items(vomsComponents.indices.toList()) { i ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("${vomsComponents[i].label}  (sintomi: ${scores[i]}/10)")
                        Slider(
                            value = scores[i].toFloat(),
                            onValueChange = { scores[i] = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            npcCm, { npcCm = it },
                            label = { Text("Near Point of Convergence (cm)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (npc != null && npc > 5.0) {
                            Text("NPC >5cm: anomalo", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Punteggio sintomi totale: $total  (soglia clinica di allarme: incremento ≥2 rispetto al basale per componente)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
