// © Roberto Di Flumeri
import SwiftUI

private let vomsComponents = [
    "Inseguimento lento (smooth pursuit)",
    "Saccadi orizzontali",
    "Saccadi verticali",
    "Convergenza (near point)",
    "VOR orizzontale",
    "VOR verticale",
    "Sensibilità al movimento visivo",
]

/// Vestibular/Ocular Motor Screening: punteggio sintomi 0-10 per componente
/// (mal di testa, capogiro, nausea, annebbiamento).
struct VomsView: View {
    @State private var scores = Array(repeating: 0, count: vomsComponents.count)
    @State private var npcCm = ""

    private var total: Int { scores.reduce(0, +) }
    private var npc: Double? { Double(npcCm) }

    var body: some View {
        VStack(spacing: 0) {
            List {
                ForEach(vomsComponents.indices, id: \.self) { i in
                    VStack(alignment: .leading) {
                        Text("\(vomsComponents[i])  (sintomi: \(scores[i])/10)")
                        Slider(
                            value: Binding(get: { Double(scores[i]) }, set: { scores[i] = Int($0) }),
                            in: 0...10, step: 1
                        )
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Near Point of Convergence (cm)", text: $npcCm)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                    if let npc, npc > 5.0 {
                        Text("NPC >5cm: anomalo").foregroundColor(.red)
                    }
                }
            }
            .listStyle(.plain)
            Divider()
            Text("Punteggio sintomi totale: \(total)  (soglia clinica di allarme: incremento ≥2 rispetto al basale per componente)")
                .font(.subheadline)
                .padding(16)
        }
        .navigationTitle("VOMS")
        .navigationBarTitleDisplayMode(.inline)
    }
}
