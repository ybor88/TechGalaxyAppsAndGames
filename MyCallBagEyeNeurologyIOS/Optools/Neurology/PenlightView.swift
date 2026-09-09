// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Usa la torcia del dispositivo come penlight per esami pupillari, e la fotocamera
/// per osservare/documentare la risposta.
struct PenlightView: View {
    var body: some View {
        CameraCaptureView(
            title: "Penlight",
            instructions: "Attiva la torcia (icona flash) per usarla come penlight durante l'esame pupillare (riflesso fotomotore diretto/consensuale, swinging flashlight test). Usa lo scatto per documentare l'esame.",
            facing: .back
        )
    }
}
