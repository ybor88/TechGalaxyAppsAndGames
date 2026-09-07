// © Roberto Di Flumeri
import SwiftUI

private struct WellsCriterion { let label: String; let points: Int }

private let dvtCriteria: [WellsCriterion] = [
    WellsCriterion(label: "Cancro attivo (trattamento in corso o entro 6 mesi)", points: 1),
    WellsCriterion(label: "Paralisi/paresi o immobilizzazione recente arto inferiore", points: 1),
    WellsCriterion(label: "Allettamento >3gg o chirurgia maggiore <12 settimane", points: 1),
    WellsCriterion(label: "Dolorabilità localizzata lungo il sistema venoso profondo", points: 1),
    WellsCriterion(label: "Gonfiore di tutto l'arto inferiore", points: 1),
    WellsCriterion(label: "Gonfiore del polpaccio >3cm rispetto al controlaterale", points: 1),
    WellsCriterion(label: "Edema improntabile limitato all'arto sintomatico", points: 1),
    WellsCriterion(label: "Vene collaterali superficiali (non varicose)", points: 1),
    WellsCriterion(label: "TVP pregressa documentata", points: 1),
    WellsCriterion(label: "Diagnosi alternativa altrettanto o più probabile", points: -2),
]

/// Wells' Criteria per Trombosi Venosa Profonda (TVP/DVT).
struct WellsDvtCalculatorView: View {
    @State private var checked = Array(repeating: false, count: dvtCriteria.count)

    private var score: Int {
        dvtCriteria.indices.reduce(0) { $0 + (checked[$1] ? dvtCriteria[$1].points : 0) }
    }
    private var risk: String {
        switch score {
        case 3...: return "Alta probabilità"
        case 1...2: return "Probabilità moderata"
        default: return "Bassa probabilità"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            List(dvtCriteria.indices, id: \.self) { i in
                let pts = dvtCriteria[i].points
                Toggle("\(dvtCriteria[i].label) (\(pts > 0 ? "+" : "")\(pts))", isOn: $checked[i])
            }
            .listStyle(.plain)
            Text("Punteggio: \(score)  →  \(risk)").font(.title2).padding(16)
        }
        .navigationTitle("Wells' Criteria - TVP")
        .navigationBarTitleDisplayMode(.inline)
    }
}
