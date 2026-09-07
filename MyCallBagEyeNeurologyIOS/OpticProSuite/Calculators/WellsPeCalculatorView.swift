// © Roberto Di Flumeri
import SwiftUI

private struct WellsPeCriterion { let label: String; let points: Double }

private let peCriteria: [WellsPeCriterion] = [
    WellsPeCriterion(label: "Segni clinici di TVP", points: 3.0),
    WellsPeCriterion(label: "Diagnosi alternativa meno probabile della PE", points: 3.0),
    WellsPeCriterion(label: "Frequenza cardiaca >100 bpm", points: 1.5),
    WellsPeCriterion(label: "Immobilizzazione >3gg o chirurgia nelle 4 settimane precedenti", points: 1.5),
    WellsPeCriterion(label: "TVP/PE pregressa", points: 1.5),
    WellsPeCriterion(label: "Emottisi", points: 1.0),
    WellsPeCriterion(label: "Neoplasia attiva (trattamento entro 6 mesi o palliativo)", points: 1.0),
]

/// Wells' Criteria per Embolia Polmonare (PE).
struct WellsPeCalculatorView: View {
    @State private var checked = Array(repeating: false, count: peCriteria.count)

    private var score: Double {
        peCriteria.indices.reduce(0.0) { $0 + (checked[$1] ? peCriteria[$1].points : 0.0) }
    }
    private var risk: String {
        if score > 6.0 { return "Alta probabilità" }
        if score >= 2.0 { return "Probabilità moderata" }
        return "Bassa probabilità"
    }

    var body: some View {
        VStack(spacing: 0) {
            List(peCriteria.indices, id: \.self) { i in
                Toggle("\(peCriteria[i].label) (+\(String(format: "%.1f", peCriteria[i].points)))", isOn: $checked[i])
            }
            .listStyle(.plain)
            Text("Punteggio: \(String(format: "%.1f", score))  →  \(risk)").font(.title2).padding(16)
        }
        .navigationTitle("Wells' Criteria - PE")
        .navigationBarTitleDisplayMode(.inline)
    }
}
