// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Metodo "carta di calibrazione": l'utente allinea 2 marker sui bordi di un oggetto
/// di larghezza nota (es. carta di credito, 85.6mm) e 2 marker sulle pupille.
/// PD = distanza(pupille) / distanza(calibrazione) * larghezza nota.
struct PdMeasurementView: View {
    var body: some View {
        CalibratedMeasureView(
            title: "Misurazione PD",
            instructions: "1) Tieni una carta (es. carta di credito) appoggiata sulla fronte, sullo stesso piano degli occhi. 2) Trascina i marker BLU sui bordi della carta. 3) Trascina i marker VERDI al centro di ciascuna pupilla.",
            resultLabel: "PD stimata:",
            facing: .front,
            computeResult: { mm in String(format: "%.1f mm", mm) }
        )
    }
}
