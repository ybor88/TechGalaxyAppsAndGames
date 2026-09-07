package com.opticpro.suite.ui.emergency

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

/** Anelli di riferimento da 1 a 9 mm da confrontare con la pupilla del paziente sullo schermo (calibrare con oggetto noto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PupilGaugeScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Calibro pupillare") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Accosta lo schermo alla guancia del paziente e confronta il diametro pupillare con gli anelli (mm). Nota: la scala reale dipende dal dispositivo; calibrare prima con un oggetto di dimensione nota.")
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val center = Offset(size.width / 2, size.height / 2)
                val mmToPx = 8f // fattore approssimativo, calibrare per dispositivo
                for (mm in 1..9) {
                    drawCircle(Color.Black, radius = mm * mmToPx, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
            }
        }
    }
}
