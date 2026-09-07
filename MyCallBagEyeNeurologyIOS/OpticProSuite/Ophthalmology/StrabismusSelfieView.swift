// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Test di Hirschberg: misura lo spostamento del riflesso corneale dalla luce e lo converte
/// in deviazione stimata usando il rapporto approssimativo 1mm ≈ 22 prismi diottrici (Δ).
struct StrabismusSelfieView: View {
    var body: some View {
        CalibratedMeasureView(
            title: "Test strabismo (Hirschberg)",
            instructions: "1) Fai fissare al paziente una luce puntiforme frontale. 2) Trascina i marker BLU su un oggetto di riferimento noto (es. distanza tra iridi ~11.7mm) per calibrare. 3) Trascina i marker VERDI sul riflesso corneale in entrambi gli occhi per misurarne lo spostamento.",
            defaultRefWidthMm: "11.7",
            resultLabel: "Deviazione stimata:",
            facing: .front,
            computeResult: { mm in
                let prismDiopters = mm * 22
                return String(format: "%.1f mm ≈ %.0fΔ", mm, prismDiopters)
            }
        )
    }
}
