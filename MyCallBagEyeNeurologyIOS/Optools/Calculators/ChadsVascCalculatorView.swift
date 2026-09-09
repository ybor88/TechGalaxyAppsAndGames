// © Roberto Di Flumeri
import SwiftUI

private struct Criterion { let label: String; let points: Int }

private let chadsVascCriteria: [Criterion] = [
    Criterion(label: "Scompenso cardiaco/disfunzione VS", points: 1),
    Criterion(label: "Ipertensione", points: 1),
    Criterion(label: "Età ≥75 anni", points: 2),
    Criterion(label: "Diabete mellito", points: 1),
    Criterion(label: "Ictus/TIA/tromboembolismo pregresso", points: 2),
    Criterion(label: "Patologia vascolare (IMA, PAD, placca aortica)", points: 1),
    Criterion(label: "Età 65-74 anni", points: 1),
    Criterion(label: "Sesso femminile", points: 1),
]

struct ChadsVascCalculatorView: View {
    @State private var checked = Array(repeating: false, count: chadsVascCriteria.count)

    private var score: Int {
        chadsVascCriteria.indices.reduce(0) { $0 + (checked[$1] ? chadsVascCriteria[$1].points : 0) }
    }

    var body: some View {
        VStack(spacing: 0) {
            List(chadsVascCriteria.indices, id: \.self) { i in
                Toggle("\(chadsVascCriteria[i].label) (+\(chadsVascCriteria[i].points))", isOn: $checked[i])
            }
            .listStyle(.plain)
            Text("Punteggio: \(score)").font(.title2).padding(16)
        }
        .navigationTitle("CHA₂DS₂-VASc")
        .navigationBarTitleDisplayMode(.inline)
    }
}
