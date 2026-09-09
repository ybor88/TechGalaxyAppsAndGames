// © Roberto Di Flumeri
import SwiftUI

/// CKD-EPI 2021 (creatinina, senza fattore razza).
struct CkdEpiCalculatorView: View {
    @State private var ageStr = ""
    @State private var creatinineStr = "" // mg/dL
    @State private var isFemale = false

    private var egfr: Double? {
        guard let age = Double(ageStr), let scr = Double(creatinineStr), age > 0, scr > 0 else { return nil }
        let kappa = isFemale ? 0.7 : 0.9
        let alpha = isFemale ? -0.241 : -0.302
        let sexFactor = isFemale ? 1.012 : 1.0
        let minRatio = min(scr / kappa, 1.0)
        let maxRatio = max(scr / kappa, 1.0)
        return 142 * pow(minRatio, alpha) * pow(maxRatio, -1.200) * pow(0.9938, age) * sexFactor
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Età (anni)", text: $ageStr).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            TextField("Creatinina sierica (mg/dL)", text: $creatinineStr).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            Toggle("Sesso femminile", isOn: $isFemale)

            if let egfr {
                Text("eGFR: \(String(format: "%.0f", egfr)) mL/min/1.73m²").font(.title2)
                let stage: String
                switch egfr {
                case 90...: stage = "G1 - Normale"
                case 60..<90: stage = "G2 - Lievemente ridotto"
                case 45..<60: stage = "G3a - Lieve-moderata riduzione"
                case 30..<45: stage = "G3b - Moderata-severa riduzione"
                case 15..<30: stage = "G4 - Severa riduzione"
                default: stage = "G5 - Insufficienza renale"
                }
                Text(stage).font(.headline)
            }
        }
        .padding(16)
        .navigationTitle("CKD-EPI 2021")
        .navigationBarTitleDisplayMode(.inline)
    }
}
