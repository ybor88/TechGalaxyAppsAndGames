package com.opticpro.suite.ui.ophthalmology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstigmatismDialScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Astigmatism Dial") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Chiedi al paziente quale linea appare più nera/nitida. L'asse indicato corrisponde all'asse del cilindro negativo (+90° all'asse dell'errore).")
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) / 2 * 0.9f
                for (deg in 0 until 180 step 10) {
                    val rad = Math.toRadians(deg.toDouble())
                    val dx = (radius * cos(rad)).toFloat()
                    val dy = (radius * sin(rad)).toFloat()
                    drawLine(Color.Black, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), strokeWidth = 3f)
                }
            }
        }
    }
}
