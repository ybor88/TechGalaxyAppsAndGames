// © Roberto Di Flumeri
import SwiftUI

// Altezza standard (mm) delle lettere Snellen se il test fosse eseguito a 6 metri.
private let snellenLines: [(String, Double)] = [
    ("6/60", 87.3), ("6/36", 52.4), ("6/24", 34.9), ("6/18", 26.2),
    ("6/12", 17.5), ("6/9", 13.1), ("6/6", 8.7), ("6/5", 7.3), ("6/4", 5.8)
]
private let mmPerPoint = 0.35 // conversione approssimata mm -> punti tipografici a distanza di riferimento

struct VisionChartView: View {
    @State private var distanceM: Double = 3

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Distanza di test: \(String(format: "%.1f", distanceM)) m")
                Slider(value: $distanceM, in: 1...6)
                Text("Posiziona il dispositivo alla distanza indicata dal paziente. La dimensione degli ottotipi si adatta automaticamente.")
                    .font(.footnote)
            }
            .padding(16)
            Divider()
            List(snellenLines, id: \.0) { label, baseHeightMm in
                let heightMm = baseHeightMm * (distanceM / 6.0)
                let heightPt = CGFloat(heightMm / mmPerPoint)
                HStack {
                    Text("E")
                        .font(.system(size: heightPt))
                        .frame(maxWidth: .infinity, alignment: .center)
                    Text(label)
                }
            }
            .listStyle(.plain)
        }
        .navigationTitle("Tabella acuità visiva")
        .navigationBarTitleDisplayMode(.inline)
    }
}
