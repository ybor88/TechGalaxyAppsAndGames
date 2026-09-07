package com.opticpro.suite.ui.ophthalmology

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import com.opticpro.suite.ui.common.CameraCaptureScreen

@Composable
fun FundusPhotographyScreen(onBack: () -> Unit) {
    CameraCaptureScreen(
        title = "Ullman indiretto (funduscopia)",
        instructions = "Usa una lente condensante (20D/28D) davanti all'occhio del paziente e allinea la fotocamera " +
            "posteriore per fotografare il riflesso del fondo. Attiva la torcia come sorgente luminosa se necessario.",
        onBack = onBack,
        facing = CameraSelector.LENS_FACING_BACK,
        albumName = "OpticProSuite/Funduscopia"
    )
}
