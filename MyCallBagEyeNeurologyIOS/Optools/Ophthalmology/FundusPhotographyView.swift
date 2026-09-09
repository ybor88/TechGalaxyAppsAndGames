// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

struct FundusPhotographyView: View {
    var body: some View {
        CameraCaptureView(
            title: "Ullman indiretto (funduscopia)",
            instructions: "Usa una lente condensante (20D/28D) davanti all'occhio del paziente e allinea la fotocamera posteriore per fotografare il riflesso del fondo. Attiva la torcia come sorgente luminosa se necessario.",
            facing: .back
        )
    }
}
