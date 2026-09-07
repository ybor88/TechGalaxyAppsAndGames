package com.opticpro.suite.ui.emergency

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opticpro.suite.ui.common.CameraCapturePreview

/** Modalità illuminatore blu cobalto: usa lo schermo come sorgente di luce blu per l'esame con fluoresceina (abrasioni corneali). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CobaltBlueLightScreen(onBack: () -> Unit) {
    var illuminatorMode by remember { mutableStateOf(true) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Luce blu cobalto") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        if (illuminatorMode) {
            Box(
                Modifier.fillMaxSize().padding(padding).background(Color(0xFF0026FF)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(onClick = { illuminatorMode = false }, modifier = Modifier.padding(24.dp)) {
                    Text("Passa a modalità osservazione (fotocamera)")
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "Applica fluoresceina, illumina l'occhio con una sorgente blu esterna e osserva la fluorescenza " +
                        "verde delle abrasioni corneali attraverso la fotocamera.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CameraCapturePreview(modifier = Modifier.fillMaxSize(), facing = CameraSelector.LENS_FACING_BACK)
                }
                Button(onClick = { illuminatorMode = true }, modifier = Modifier.padding(16.dp)) {
                    Text("Torna a modalità illuminatore")
                }
            }
        }
    }
}
