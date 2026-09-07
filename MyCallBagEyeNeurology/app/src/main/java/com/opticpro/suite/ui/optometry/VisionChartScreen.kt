package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Altezza standard (mm) delle lettere Snellen se il test fosse eseguito a 6 metri.
private val snellenLines = listOf(
    "6/60" to 87.3, "6/36" to 52.4, "6/24" to 34.9, "6/18" to 26.2,
    "6/12" to 17.5, "6/9" to 13.1, "6/6" to 8.7, "6/5" to 7.3, "6/4" to 5.8
)
private const val MM_PER_DP = 0.15875 // conversione approssimata a 160dpi di riferimento

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionChartScreen(onBack: () -> Unit) {
    var distanceM by remember { mutableStateOf(3f) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Tabella acuità visiva") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Text("Distanza di test: ${"%.1f".format(distanceM)} m")
                Slider(value = distanceM, onValueChange = { distanceM = it }, valueRange = 1f..6f)
                Text(
                    "Posiziona il dispositivo alla distanza indicata dal paziente. La dimensione degli ottotipi si adatta automaticamente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                items(snellenLines) { (label, baseHeightMm) ->
                    val heightMm = baseHeightMm * (distanceM / 6.0)
                    val heightDp = (heightMm / MM_PER_DP).roundToInt()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "E",
                            fontSize = heightDp.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Text(label, modifier = Modifier.padding(end = 16.dp))
                    }
                }
            }
        }
    }
}
