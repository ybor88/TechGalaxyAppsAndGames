// © Roberto Di Flumeri
import SwiftUI

struct BmiCalculatorView: View {
    @State private var weightKg = ""
    @State private var heightCm = ""

    private var bmi: Double? {
        guard let w = Double(weightKg), let hCm = Double(heightCm), hCm > 0 else { return nil }
        let h = hCm / 100
        return w / (h * h)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Peso (kg)", text: $weightKg).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            TextField("Altezza (cm)", text: $heightCm).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)

            if let bmi {
                Text("BMI: \(String(format: "%.1f", bmi))").font(.title2)
                let category: String
                switch bmi {
                case ..<18.5: category = "Sottopeso"
                case ..<25.0: category = "Normopeso"
                case ..<30.0: category = "Sovrappeso"
                default: category = "Obesità"
                }
                Text(category).font(.headline)
            }
        }
        .padding(16)
        .navigationTitle("BMI Calculator")
        .navigationBarTitleDisplayMode(.inline)
    }
}
