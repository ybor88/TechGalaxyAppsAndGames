// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Vein finder: anteprima fotocamera per individuare vene superficiali sfruttando torcia
/// radente e contrasto naturale (nessun filtro IR reale, che richiede hardware dedicato).
struct VeinFinderView: View {
    var body: some View {
        VStack(spacing: 0) {
            Text("Illumina la cute tangenzialmente con la torcia e osserva il contrasto delle vene superficiali. Nota: non è un vero imaging a infrarossi (richiederebbe hardware dedicato), ma un ausilio visivo.")
                .font(.footnote)
                .padding(16)
            CameraPreview(facing: .back)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("Vein finder")
        .navigationBarTitleDisplayMode(.inline)
    }
}
