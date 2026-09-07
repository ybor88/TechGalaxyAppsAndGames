// © Roberto Di Flumeri
import SwiftUI

/// Punto prossimo di accommodazione: l'operatore avvicina un target fino a quando il
/// paziente riferisce sfocatura persistente; la distanza misurata determina l'ampiezza
/// accomodativa (D = 100 / distanza in cm).
struct NearPointAccommodationView: View {
    @State private var distanceCm: Double = 10

    private var amplitudeD: Double { 100 / distanceCm }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Avvicina il testo/target verso il paziente. Quando riferisce sfocatura persistente, imposta la distanza misurata.")

            VStack(alignment: .leading, spacing: 4) {
                Text("W I S B A L").font(.title)
                Text("Testo di lettura ravvicinata (near text)").font(.footnote)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppColor.surface)
            .cornerRadius(12)

            Text("Distanza punto prossimo: \(Int(distanceCm)) cm")
            Slider(value: $distanceCm, in: 5...50)

            Text("Ampiezza di accomodazione: \(String(format: "%.1f", amplitudeD)) D")
                .font(.title2)
        }
        .padding(16)
        .navigationTitle("Near point of accommodation")
        .navigationBarTitleDisplayMode(.inline)
    }
}
