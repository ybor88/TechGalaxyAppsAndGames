// © Roberto Di Flumeri
import SwiftUI

private struct Dermatome { let level: String; let region: String }

private let dermatomes: [Dermatome] = [
    Dermatome(level: "C2", region: "Cuoio capelluto posteriore, occipite"),
    Dermatome(level: "C3", region: "Collo laterale"),
    Dermatome(level: "C4", region: "Spalla (acromion)"),
    Dermatome(level: "C5", region: "Faccia laterale del braccio"),
    Dermatome(level: "C6", region: "Avambraccio laterale, pollice"),
    Dermatome(level: "C7", region: "Dito medio"),
    Dermatome(level: "C8", region: "Mignolo, avambraccio mediale"),
    Dermatome(level: "T1", region: "Faccia mediale del braccio"),
    Dermatome(level: "T4", region: "Linea dei capezzoli"),
    Dermatome(level: "T10", region: "Ombelico"),
    Dermatome(level: "T12", region: "Regione inguinale"),
    Dermatome(level: "L1", region: "Inguine, parte alta della coscia"),
    Dermatome(level: "L2", region: "Coscia anteriore"),
    Dermatome(level: "L3", region: "Ginocchio mediale"),
    Dermatome(level: "L4", region: "Malleolo mediale"),
    Dermatome(level: "L5", region: "Dorso del piede, alluce"),
    Dermatome(level: "S1", region: "Malleolo laterale, mignolo del piede"),
    Dermatome(level: "S2-S4", region: "Regione perianale"),
]

/// Riferimento rapido dei livelli dermatomerici per la localizzazione clinica di deficit sensitivi.
struct DermatomeMapView: View {
    var body: some View {
        List(dermatomes, id: \.level) { d in
            VStack(alignment: .leading) {
                Text(d.level).font(.headline)
                Text(d.region).font(.subheadline)
            }
        }
        .listStyle(.plain)
        .navigationTitle("Mappa dei dermatomeri")
        .navigationBarTitleDisplayMode(.inline)
    }
}
