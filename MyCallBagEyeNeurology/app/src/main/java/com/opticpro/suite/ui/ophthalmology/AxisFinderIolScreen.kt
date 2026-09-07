package com.opticpro.suite.ui.ophthalmology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Marcatore d'asse per IOL toriche: ruota la linea trascinandola fino all'asse target pre-calcolato. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxisFinderIolScreen(onBack: () -> Unit) {
    var targetAxis by remember { mutableStateOf(90f) }
    var currentAngleDeg by remember { mutableStateOf(0f) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Axis Finder per IOL toriche") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Asse target (da biometria): ${targetAxis.roundToInt()}°")
            Slider(value = targetAxis, onValueChange = { targetAxis = it }, valueRange = 0f..180f)
            Text("Ruota la linea trascinandola per marcare l'asse sull'occhio del paziente.")
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = change.position.x - center.x
                        val dy = change.position.y - center.y
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 180f
                        if (angle > 180f) angle -= 180f
                        currentAngleDeg = angle
                    }
                }
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) / 2 * 0.85f
                // Cerchio graduato
                drawCircle(Color.Gray, radius, center, style = Stroke(width = 2f))
                // Linea asse target (verde)
                val targetRad = Math.toRadians(targetAxis.toDouble())
                drawLine(
                    Color.Green,
                    Offset(center.x - (radius * cos(targetRad)).toFloat(), center.y - (radius * sin(targetRad)).toFloat()),
                    Offset(center.x + (radius * cos(targetRad)).toFloat(), center.y + (radius * sin(targetRad)).toFloat()),
                    strokeWidth = 4f
                )
                // Linea corrente trascinabile (rossa)
                val curRad = Math.toRadians(currentAngleDeg.toDouble())
                drawLine(
                    Color.Red,
                    Offset(center.x - (radius * cos(curRad)).toFloat(), center.y - (radius * sin(curRad)).toFloat()),
                    Offset(center.x + (radius * cos(curRad)).toFloat(), center.y + (radius * sin(curRad)).toFloat()),
                    strokeWidth = 6f
                )
            }
            val diff = kotlin.math.abs(currentAngleDeg - targetAxis)
            Text(
                "Marcatura corrente: ${currentAngleDeg.roundToInt()}°  |  scarto: ${diff.roundToInt()}°",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
