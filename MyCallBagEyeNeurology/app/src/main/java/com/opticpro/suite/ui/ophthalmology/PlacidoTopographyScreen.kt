package com.opticpro.suite.ui.ophthalmology

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.opticpro.suite.ui.common.CameraCapturePreview

/** Disco di Placido: anelli concentrici proiettati sullo schermo, osservati per riflesso attraverso la fotocamera frontale per screening di irregolarità corneali (astigmatismo irregolare, cheratocono). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacidoTopographyScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Topografia con disco di Placido") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Avvicina l'occhio del paziente allo schermo e osserva, tramite la fotocamera frontale, la regolarità " +
                    "del riflesso degli anelli sulla cornea. Anelli irregolari/ovalizzati suggeriscono astigmatismo o cheratocono.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraCapturePreview(modifier = Modifier.fillMaxSize(), facing = CameraSelector.LENS_FACING_FRONT)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                    val maxRadius = minOf(size.width, size.height) / 2 * 0.9f
                    for (i in 1..8) {
                        drawCircle(
                            color = Color.White,
                            radius = maxRadius * i / 8f,
                            center = center,
                            style = Stroke(width = 4f)
                        )
                    }
                }
            }
        }
    }
}
