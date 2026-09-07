// © Roberto Di Flumeri
import SwiftUI

private struct NihsItem { let label: String; let maxScore: Int }

private let nihsItems: [NihsItem] = [
    NihsItem(label: "1a. Livello di coscienza", maxScore: 3),
    NihsItem(label: "1b. Domande LOC", maxScore: 2),
    NihsItem(label: "1c. Comandi LOC", maxScore: 2),
    NihsItem(label: "2. Sguardo", maxScore: 2),
    NihsItem(label: "3. Visus", maxScore: 3),
    NihsItem(label: "4. Paresi facciale", maxScore: 3),
    NihsItem(label: "5a. Motorio arto sup. SX", maxScore: 4),
    NihsItem(label: "5b. Motorio arto sup. DX", maxScore: 4),
    NihsItem(label: "6a. Motorio arto inf. SX", maxScore: 4),
    NihsItem(label: "6b. Motorio arto inf. DX", maxScore: 4),
    NihsItem(label: "7. Atassia arti", maxScore: 2),
    NihsItem(label: "8. Sensibilità", maxScore: 2),
    NihsItem(label: "9. Linguaggio", maxScore: 3),
    NihsItem(label: "10. Disartria", maxScore: 2),
    NihsItem(label: "11. Estinzione/negligenza", maxScore: 2),
]

struct NihStrokeScaleView: View {
    @State private var scores = Array(repeating: 0, count: nihsItems.count)

    private var total: Int { scores.reduce(0, +) }

    var body: some View {
        VStack(spacing: 0) {
            List {
                ForEach(nihsItems.indices, id: \.self) { i in
                    VStack(alignment: .leading) {
                        Text("\(nihsItems[i].label)  (score: \(scores[i]))")
                        Slider(
                            value: Binding(get: { Double(scores[i]) }, set: { scores[i] = Int($0) }),
                            in: 0...Double(nihsItems[i].maxScore),
                            step: 1
                        )
                    }
                }
            }
            .listStyle(.plain)
            Divider()
            Text("Punteggio totale NIHSS: \(total)")
                .font(.title3)
                .padding(16)
        }
        .navigationTitle("NIH Stroke Scale")
        .navigationBarTitleDisplayMode(.inline)
    }
}
