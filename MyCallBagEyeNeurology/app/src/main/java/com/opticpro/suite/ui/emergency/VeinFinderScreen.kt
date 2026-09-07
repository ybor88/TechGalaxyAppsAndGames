package com.opticpro.suite.ui.emergency

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opticpro.suite.ui.common.CameraCapturePreview

/** Vein finder: anteprima fotocamera per individuare vene superficiali sfruttando torcia radente e contrasto naturale (nessun filtro IR reale, che richiede hardware dedicato). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeinFinderScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Vein finder") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Illumina la cute tangenzialmente con la torcia e osserva il contrasto delle vene superficiali. " +
                    "Nota: non è un vero imaging a infrarossi (richiederebbe hardware dedicato), ma un ausilio visivo.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraCapturePreview(modifier = Modifier.fillMaxSize(), facing = CameraSelector.LENS_FACING_BACK)
            }
        }
    }
}
