package com.opticpro.suite.ui.neurology

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import com.opticpro.suite.ui.common.CameraCaptureScreen

/** Usa la torcia del dispositivo come penlight per esami pupillari, e la fotocamera per osservare/documentare la risposta. */
@Composable
fun PenlightScreen(onBack: () -> Unit) {
    CameraCaptureScreen(
        title = "Penlight",
        instructions = "Attiva la torcia (icona flash) per usarla come penlight durante l'esame pupillare (riflesso " +
            "fotomotore diretto/consensuale, swinging flashlight test). Usa lo scatto per documentare l'esame.",
        onBack = onBack,
        facing = CameraSelector.LENS_FACING_BACK,
        albumName = "OpticProSuite/Penlight"
    )
}
