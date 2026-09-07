package com.opticpro.suite.ui.optometry

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
import com.opticpro.suite.ui.common.CameraPreview
import kotlin.math.hypot

/**
 * Metodo "carta di calibrazione": l'utente allinea 2 marker sui bordi di un oggetto
 * di larghezza nota (es. carta di credito, 85.6mm) e 2 marker sulle pupille.
 * PD = distanza(pupille) / distanza(calibrazione) * larghezza nota.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdMeasurementScreen(onBack: () -> Unit) {
    var refWidthMm by remember { mutableStateOf("85.6") }
    var calA by remember { mutableStateOf(Offset(120f, 300f)) }
    var calB by remember { mutableStateOf(Offset(320f, 300f)) }
    var pupilA by remember { mutableStateOf(Offset(150f, 500f)) }
    var pupilB by remember { mutableStateOf(Offset(290f, 500f)) }

    val calPx = hypot((calB.x - calA.x).toDouble(), (calB.y - calA.y).toDouble())
    val pupilPx = hypot((pupilB.x - pupilA.x).toDouble(), (pupilB.y - pupilA.y).toDouble())
    val widthMm = refWidthMm.toDoubleOrNull() ?: 0.0
    val pdMm = if (calPx > 0) pupilPx / calPx * widthMm else 0.0

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Misurazione PD") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "1) Tieni una carta (es. carta di credito) appoggiata sulla fronte, sullo stesso piano degli occhi. " +
                        "2) Trascina i marker BLU sui bordi della carta. 3) Trascina i marker VERDI al centro di ciascuna pupilla.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    refWidthMm, { refWidthMm = it },
                    label = { Text("Larghezza oggetto di riferimento (mm)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraPreview(modifier = Modifier.fillMaxSize(), facing = CameraSelector.LENS_FACING_FRONT)
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val p = change.position
                            val targets = listOf(
                                Triple("calA", calA) { o: Offset -> calA = o },
                                Triple("calB", calB) { o: Offset -> calB = o },
                                Triple("pupilA", pupilA) { o: Offset -> pupilA = o },
                                Triple("pupilB", pupilB) { o: Offset -> pupilB = o },
                            )
                            val nearest = targets.minByOrNull { hypot((it.second.x - p.x).toDouble(), (it.second.y - p.y).toDouble()) }
                            nearest?.third?.invoke(nearest.second + drag)
                        }
                    }
                ) {
                    fun marker(o: Offset, color: Color) = drawCircle(color, radius = 22f, center = o)
                    drawLine(Color.Blue, calA, calB, strokeWidth = 4f)
                    drawLine(Color.Green, pupilA, pupilB, strokeWidth = 4f)
                    marker(calA, Color.Blue); marker(calB, Color.Blue)
                    marker(pupilA, Color.Green); marker(pupilB, Color.Green)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PD stimata:", style = MaterialTheme.typography.titleMedium)
                Text("%.1f mm".format(pdMm), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
