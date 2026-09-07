package com.opticpro.suite.ui.neurology

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockDrawingScreen(onBack: () -> Unit) {
    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Clock Drawing Test") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                TextButton(onClick = { paths.clear() }) { Text("Cancella") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Chiedi al paziente di disegnare un orologio indicando le ore 11:10. Valuta: cerchio, numeri (posizione/completezza), lancette.")
            Canvas(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        },
                        onDrag = { change, _ ->
                            currentPath?.lineTo(change.position.x, change.position.y)
                        },
                        onDragEnd = {
                            currentPath?.let { paths.add(it) }
                            currentPath = null
                        }
                    )
                }
            ) {
                drawRect(Color.White, size = size)
                paths.forEach { drawPath(it, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)) }
                currentPath?.let { drawPath(it, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)) }
            }
        }
    }
}
