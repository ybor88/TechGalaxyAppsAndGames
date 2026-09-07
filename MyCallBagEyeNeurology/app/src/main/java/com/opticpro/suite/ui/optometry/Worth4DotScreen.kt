package com.opticpro.suite.ui.optometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Worth4DotScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Worth 4 Dot Test") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Richiede occhiali rosso/verde. Il paziente riferisce quanti punti vede: 2=soppressione OD, 3=soppressione OS, 4=fusione, 5=diplopia.",
                style = MaterialTheme.typography.bodyMedium
            )
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val w = size.width
                val h = size.height
                val r = 40f
                drawCircle(Color.Red, r, Offset(w / 2, h * 0.25f))
                drawCircle(Color(0xFF00A651), r, Offset(w * 0.3f, h * 0.55f))
                drawCircle(Color(0xFF00A651), r, Offset(w * 0.7f, h * 0.55f))
                drawCircle(Color.White, r, Offset(w / 2, h * 0.8f))
            }
        }
    }
}
