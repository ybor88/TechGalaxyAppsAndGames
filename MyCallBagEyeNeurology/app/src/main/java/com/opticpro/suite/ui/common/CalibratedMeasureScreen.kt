package com.opticpro.suite.ui.common

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Strumento generico "misura calibrata a 2 punti": l'utente allinea 2 marker su un
 * oggetto di larghezza nota e 2 marker sulla grandezza da misurare (es. PD, esoftalmometria,
 * riflesso corneale). Riutilizzato da più strumenti con etichette/formule diverse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibratedMeasureScreen(
    title: String,
    instructions: String,
    onBack: () -> Unit,
    defaultRefWidthMm: String = "85.6",
    resultLabel: String,
    facing: Int = CameraSelector.LENS_FACING_FRONT,
    computeResult: (measuredMm: Double) -> String
) {
    var refWidthMm by remember { mutableStateOf(defaultRefWidthMm) }
    var calA by remember { mutableStateOf(Offset(120f, 300f)) }
    var calB by remember { mutableStateOf(Offset(320f, 300f)) }
    var targetA by remember { mutableStateOf(Offset(150f, 500f)) }
    var targetB by remember { mutableStateOf(Offset(290f, 500f)) }

    val calPx = hypot((calB.x - calA.x).toDouble(), (calB.y - calA.y).toDouble())
    val targetPx = hypot((targetB.x - targetA.x).toDouble(), (targetB.y - targetA.y).toDouble())
    val widthMm = refWidthMm.toDoubleOrNull() ?: 0.0
    val measuredMm = if (calPx > 0) targetPx / calPx * widthMm else 0.0

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(12.dp)) {
                Text(instructions, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    refWidthMm, { refWidthMm = it },
                    label = { Text("Larghezza oggetto di riferimento (mm)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraCapturePreview(modifier = Modifier.fillMaxSize(), facing = facing)
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val p = change.position
                            val targets = listOf(
                                Pair(calA) { o: Offset -> calA = o },
                                Pair(calB) { o: Offset -> calB = o },
                                Pair(targetA) { o: Offset -> targetA = o },
                                Pair(targetB) { o: Offset -> targetB = o },
                            )
                            val nearest = targets.minByOrNull { hypot((it.first.x - p.x).toDouble(), (it.first.y - p.y).toDouble()) }
                            nearest?.second?.invoke(nearest.first + drag)
                        }
                    }
                ) {
                    fun marker(o: Offset, color: Color) = drawCircle(color, radius = 22f, center = o)
                    drawLine(Color.Blue, calA, calB, strokeWidth = 4f)
                    drawLine(Color.Green, targetA, targetB, strokeWidth = 4f)
                    marker(calA, Color.Blue); marker(calB, Color.Blue)
                    marker(targetA, Color.Green); marker(targetB, Color.Green)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(resultLabel, style = MaterialTheme.typography.titleMedium)
                Text(computeResult(measuredMm), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
