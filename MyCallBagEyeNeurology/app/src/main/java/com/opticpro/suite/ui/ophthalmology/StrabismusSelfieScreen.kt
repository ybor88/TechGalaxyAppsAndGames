package com.opticpro.suite.ui.ophthalmology

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import com.opticpro.suite.ui.common.CalibratedMeasureScreen

/**
 * Test di Hirschberg: misura lo spostamento del riflesso corneale dalla luce e lo converte
 * in deviazione stimata usando il rapporto approssimativo 1mm ≈ 22 prismi diottrici (Δ).
 */
@Composable
fun StrabismusSelfieScreen(onBack: () -> Unit) {
    CalibratedMeasureScreen(
        title = "Test strabismo (Hirschberg)",
        instructions = "1) Fai fissare al paziente una luce puntiforme frontale. 2) Trascina i marker BLU su un " +
            "oggetto di riferimento noto (es. distanza tra iridi ~11.7mm) per calibrare. 3) Trascina i marker VERDI " +
            "sul riflesso corneale in entrambi gli occhi per misurarne lo spostamento.",
        onBack = onBack,
        defaultRefWidthMm = "11.7",
        resultLabel = "Deviazione stimata:",
        facing = CameraSelector.LENS_FACING_FRONT,
        computeResult = { mm ->
            val prismDiopters = mm * 22
            "%.1f mm ≈ %.0fΔ".format(mm, prismDiopters)
        }
    )
}
