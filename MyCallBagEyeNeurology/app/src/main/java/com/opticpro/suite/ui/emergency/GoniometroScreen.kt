package com.opticpro.suite.ui.emergency

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Goniometro basato su accelerometro: misura l'inclinazione del dispositivo rispetto alla verticale per stimare l'ampiezza articolare (ROM). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoniometroScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var rawAngle by remember { mutableStateOf(0f) }
    var zeroOffset by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                rawAngle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val displayAngle = (rawAngle - zeroOffset + 360) % 360

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Goniometro") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Appoggia il bordo del dispositivo lungo il segmento articolare da misurare (es. braccio), " +
                    "azzera nella posizione di partenza, poi leggi l'angolo dopo il movimento.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(32.dp))
            Text("${displayAngle.roundToInt()}°", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { zeroOffset = rawAngle }) {
                Text("Azzera (posizione di partenza)")
            }
        }
    }
}
