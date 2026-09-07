// © Roberto Di Flumeri
import SwiftUI

struct GcsCalculatorView: View {
    @State private var eye: Double = 4
    @State private var verbal: Double = 5
    @State private var motor: Double = 6

    private var total: Int { Int(eye) + Int(verbal) + Int(motor) }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Apertura occhi: \(Int(eye))/4")
            Slider(value: $eye, in: 1...4, step: 1)
            Text("Risposta verbale: \(Int(verbal))/5")
            Slider(value: $verbal, in: 1...5, step: 1)
            Text("Risposta motoria: \(Int(motor))/6")
            Slider(value: $motor, in: 1...6, step: 1)

            Text("GCS totale: \(total)/15").font(.title2)
        }
        .padding(16)
        .navigationTitle("Glasgow Coma Scale")
        .navigationBarTitleDisplayMode(.inline)
    }
}
