// © Roberto Di Flumeri
import SwiftUI

/// Retinoscopia al comodino: neutralizza il riflesso e sottrae la correzione per la
/// distanza di lavoro per ottenere il potere netto.
struct RefractionBedsideView: View {
    @State private var grossPower = ""
    @State private var workingDistanceCm = "67"

    private var gross: Double? { Double(grossPower) }
    private var wd: Double? { Double(workingDistanceCm) }
    private var workingDistanceCorrection: Double? {
        guard let wd, wd > 0 else { return nil }
        return 100 / wd
    }
    private var netPower: Double? {
        guard let g = gross, let c = workingDistanceCorrection else { return nil }
        return g - c
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Esegui la retinoscopia con lenti di neutralizzazione al letto del paziente. Inserisci il potere lordo di neutralizzazione e la distanza di lavoro per ottenere il potere netto (retinoscopia).")
                TextField("Potere lordo di neutralizzazione (D)", text: $grossPower).keyboardType(.numbersAndPunctuation).textFieldStyle(.roundedBorder)
                TextField("Distanza di lavoro (cm)", text: $workingDistanceCm).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)

                if let c = workingDistanceCorrection {
                    Text("Correzione distanza di lavoro: -\(String(format: "%.2f", c)) D")
                }
                if let n = netPower {
                    Text("Potere netto stimato: \(String(format: "%.2f", n)) D").font(.title2)
                }
            }
            .padding(16)
        }
        .navigationTitle("Refrazione al comodino")
        .navigationBarTitleDisplayMode(.inline)
    }
}
