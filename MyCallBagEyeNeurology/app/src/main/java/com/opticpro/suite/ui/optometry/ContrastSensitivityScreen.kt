package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContrastSensitivityScreen(onBack: () -> Unit) {
    var contrastPercent by remember { mutableStateOf(50f) }
    var spatialFrequency by remember { mutableStateOf(6f) } // cicli per larghezza schermo

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Contrast Sensitivity Chart") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Contrasto: ${contrastPercent.toInt()}%")
            Slider(value = contrastPercent, onValueChange = { contrastPercent = it }, valueRange = 1f..100f)
            Text("Frequenza spaziale: ${spatialFrequency.toInt()} cicli")
            Slider(value = spatialFrequency, onValueChange = { spatialFrequency = it }, valueRange = 1f..20f)
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val steps = 400
                val amplitude = contrastPercent / 100f * 0.5f
                for (i in 0 until steps) {
                    val x = i.toFloat() / steps
                    val gray = 0.5f + amplitude * sin(2 * Math.PI * spatialFrequency * x).toFloat()
                    val c = gray.coerceIn(0f, 1f)
                    drawRect(
                        color = Color(c, c, c),
                        topLeft = androidx.compose.ui.geometry.Offset(x * size.width, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width / steps + 1f, size.height)
                    )
                }
            }
        }
    }
}
