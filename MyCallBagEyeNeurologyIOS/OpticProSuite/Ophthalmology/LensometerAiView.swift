// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Metodo di "neutralizzazione" (principio dello skiascopio manuale): la lente in esame
/// viene interposta tra fotocamera e un target; l'operatore avvicina/allontana la lente
/// fino a quando il target non mostra più parallasse muovendo il dispositivo (punto di neutralità).
struct LensometerAiView: View {
    @State private var distanceCm: Double = 50
    @State private var movesSameDirection = true

    private var powerD: Double { 100 / distanceCm }
    private var signedPower: Double { movesSameDirection ? -powerD : powerD }

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text("⚠️ Beta: stima manuale basata sul metodo di neutralizzazione, NON una misura automatica da immagine. Per lensometria accurata usare uno strumento dedicato.")
                    .font(.footnote)
                    .foregroundColor(.red)
                Text("1) Interponi la lente tra fotocamera e questo schermo (guardando il target attraverso la lente). 2) Muovi lateralmente il dispositivo: se l'immagine oltre la lente si muove nella stessa direzione del movimento, la lente è negativa; se in direzione opposta, è positiva. 3) Trova la distanza lente-occhio a cui l'immagine non si muove (punto neutro) e impostala sotto.")
            }
            .padding(16)

            CameraPreview(facing: .back)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            VStack(alignment: .leading, spacing: 8) {
                Text("Distanza al punto neutro: \(Int(distanceCm)) cm")
                Slider(value: $distanceCm, in: 5...200)
                Toggle("Il movimento apparente è nella STESSA direzione (lente negativa)", isOn: $movesSameDirection)
                Text("Potere stimato: \(String(format: "%.2f", signedPower)) D").font(.title2)
            }
            .padding(16)
        }
        .navigationTitle("Lensometro (neutralizzazione)")
        .navigationBarTitleDisplayMode(.inline)
    }
}
