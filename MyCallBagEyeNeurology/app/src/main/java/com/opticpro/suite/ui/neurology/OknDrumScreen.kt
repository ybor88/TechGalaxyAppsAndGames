package com.opticpro.suite.ui.neurology

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

/** Tamburo optocinetico: strisce verticali in movimento continuo per indurre ed osservare il nistagmo optocinetico (OKN). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OknDrumScreen(onBack: () -> Unit) {
    var speed by remember { mutableStateOf(200f) } // px/s
    var reversed by remember { mutableStateOf(false) }
    val stripeWidth = 80f

    val transition = rememberInfiniteTransition(label = "okn")
    val durationMs = (1000f / (speed / stripeWidth)).toInt().coerceAtLeast(50)
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reversed) -stripeWidth else stripeWidth,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("OKN Drum") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Text("Velocità: ${speed.toInt()} px/s")
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 50f..500f)
                Row {
                    FilterChip(selected = !reversed, onClick = { reversed = false }, label = { Text("→ Destra") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = reversed, onClick = { reversed = true }, label = { Text("← Sinistra") })
                }
            }
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                var x = offset - stripeWidth
                var index = 0
                while (x < size.width + stripeWidth) {
                    if (index % 2 == 0) {
                        drawRect(Color.Black, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripeWidth, size.height))
                    } else {
                        drawRect(Color.White, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripeWidth, size.height))
                    }
                    x += stripeWidth
                    index++
                }
            }
        }
    }
}
