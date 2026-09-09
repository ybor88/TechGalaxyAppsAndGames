// © Roberto Di Flumeri
import SwiftUI

struct CockcroftGaultCalculatorView: View {
    @State private var ageStr = ""
    @State private var weightStr = ""
    @State private var creatinineStr = "" // mg/dL
    @State private var isFemale = false

    private var clearance: Double? {
        guard let age = Double(ageStr), let weight = Double(weightStr), let creatinine = Double(creatinineStr), creatinine > 0 else { return nil }
        let base = (140 - age) * weight / (72 * creatinine)
        return isFemale ? base * 0.85 : base
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Età (anni)", text: $ageStr).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            TextField("Peso (kg)", text: $weightStr).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            TextField("Creatinina sierica (mg/dL)", text: $creatinineStr).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            Toggle("Sesso femminile", isOn: $isFemale)

            if let clearance {
                Text("Clearance creatinina: \(String(format: "%.1f", clearance)) mL/min").font(.title2)
            }
        }
        .padding(16)
        .navigationTitle("Cockcroft-Gault")
        .navigationBarTitleDisplayMode(.inline)
    }
}
