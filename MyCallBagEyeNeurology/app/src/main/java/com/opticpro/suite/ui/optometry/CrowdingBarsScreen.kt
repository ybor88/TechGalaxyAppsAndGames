package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

// Stessa scala Snellen a 6m usata in VisionChartScreen.
private val singleOptotypeLines = listOf(
    "6/60" to 87.3, "6/36" to 52.4, "6/24" to 34.9, "6/18" to 26.2,
    "6/12" to 17.5, "6/9" to 13.1, "6/6" to 8.7
)
private const val MM_PER_DP = 0.15875

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrowdingBarsScreen(onBack: () -> Unit) {
    var distanceM by remember { mutableStateOf(3f) }
    var lineIndex by remember { mutableStateOf(4) } // 6/12 di default

    val (label, baseHeightMm) = singleOptotypeLines[lineIndex]
    val heightMm = baseHeightMm * (distanceM / 6.0)
    val heightDp = (heightMm / MM_PER_DP).roundToInt()
    val gapDp = (heightDp * 0.5f).roundToInt() // barre a mezza altezza ottotipo, standard crowding

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Crowding bars") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Ottotipo singolo affiancato da barre di affollamento, utile per screening pediatrico (amblyopia).")
            Spacer(Modifier.height(12.dp))
            Text("Distanza di test: ${"%.1f".format(distanceM)} m")
            Slider(value = distanceM, onValueChange = { distanceM = it }, valueRange = 1f..6f)
            Text("Riga: $label")
            Slider(
                value = lineIndex.toFloat(),
                onValueChange = { lineIndex = it.roundToInt() },
                valueRange = 0f..(singleOptotypeLines.size - 1).toFloat(),
                steps = singleOptotypeLines.size - 2
            )
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Bar(heightDp, gapDp)
                Text("E", fontSize = heightDp.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = gapDp.dp))
                Bar(heightDp, gapDp)
            }
        }
    }
}

@Composable
private fun Bar(heightDp: Int, gapDp: Int) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height((heightDp + gapDp).dp)
            .background(androidx.compose.ui.graphics.Color.Black)
    )
}
