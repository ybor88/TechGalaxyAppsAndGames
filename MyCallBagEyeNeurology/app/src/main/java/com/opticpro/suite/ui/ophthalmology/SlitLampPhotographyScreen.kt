package com.opticpro.suite.ui.ophthalmology

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import com.opticpro.suite.ui.common.CameraCaptureScreen

@Composable
fun SlitLampPhotographyScreen(onBack: () -> Unit) {
    CameraCaptureScreen(
        title = "Fotografia lampada a fessura",
        instructions = "Allinea la fotocamera all'oculare della lampada a fessura o avvicinala al segmento anteriore " +
            "del paziente. Usa la torcia per illuminazione supplementare se necessario, poi scatta.",
        onBack = onBack,
        facing = CameraSelector.LENS_FACING_BACK,
        albumName = "OpticProSuite/SlitLamp"
    )
}
