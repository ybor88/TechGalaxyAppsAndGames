// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Esoftalmometria: misura la distanza dall'apice corneale al margine orbitario laterale
/// (metodo Hertel manuale assistito da fotocamera calibrata). Valori normali: 12-21mm,
/// differenza interoculare significativa se >2mm.
struct ExophthalmometerView: View {
    var body: some View {
        CalibratedMeasureView(
            title: "Esoftalmometro",
            instructions: "1) Tieni una carta di riferimento (es. carta di credito) sullo stesso piano frontale del volto. 2) Trascina i marker BLU sui bordi noti. 3) Trascina i marker VERDI dal margine orbitario laterale all'apice corneale. Normale: 12-21mm; differenza tra i due occhi >2mm è clinicamente significativa.",
            resultLabel: "Proiezione corneale:",
            facing: .front,
            computeResult: { mm in String(format: "%.1f mm", mm) }
        )
    }
}
