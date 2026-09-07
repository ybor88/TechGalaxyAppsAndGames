package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmslerGridScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Amsler Grid") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Copri un occhio, fissa il punto centrale a ~30cm. Segnala eventuali linee ondulate, distorte o aree mancanti.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Canvas(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)) {
                val cols = 20
                val rows = 20
                val cellW = size.width / cols
                val cellH = size.height / rows
                for (i in 0..cols) {
                    drawLine(Color.Black, Offset(i * cellW, 0f), Offset(i * cellW, size.height), strokeWidth = 1f)
                }
                for (j in 0..rows) {
                    drawLine(Color.Black, Offset(0f, j * cellH), Offset(size.width, j * cellH), strokeWidth = 1f)
                }
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(Color.Red, radius = 8f, center = center)
            }
        }
    }
}
