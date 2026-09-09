// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Modalità illuminatore blu cobalto: usa lo schermo come sorgente di luce blu per
/// l'esame con fluoresceina (abrasioni corneali).
struct CobaltBlueLightView: View {
    @State private var illuminatorMode = true

    var body: some View {
        Group {
            if illuminatorMode {
                ZStack(alignment: .bottom) {
                    Color(red: 0, green: 0x26 / 255, blue: 1)
                    Button("Passa a modalità osservazione (fotocamera)") { illuminatorMode = false }
                        .padding(24)
                        .buttonStyle(.borderedProminent)
                }
                .ignoresSafeArea()
            } else {
                VStack(spacing: 0) {
                    Text("Applica fluoresceina, illumina l'occhio con una sorgente blu esterna e osserva la fluorescenza verde delle abrasioni corneali attraverso la fotocamera.")
                        .font(.footnote)
                        .padding(16)
                    CameraPreview(facing: .back)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    Button("Torna a modalità illuminatore") { illuminatorMode = true }
                        .padding(16)
                }
            }
        }
        .navigationTitle("Luce blu cobalto")
        .navigationBarTitleDisplayMode(.inline)
    }
}
