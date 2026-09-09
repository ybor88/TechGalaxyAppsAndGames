// © Roberto Di Flumeri
import SwiftUI

struct ContrastSensitivityView: View {
    @State private var contrastPercent: Double = 50
    @State private var spatialFrequency: Double = 6

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Contrasto: \(Int(contrastPercent))%")
            Slider(value: $contrastPercent, in: 1...100)
            Text("Frequenza spaziale: \(Int(spatialFrequency)) cicli")
            Slider(value: $spatialFrequency, in: 1...20)

            Canvas { context, size in
                let steps = 400
                let w = Double(size.width), h = Double(size.height)
                let amplitude = contrastPercent / 100 * 0.5
                for i in 0..<steps {
                    let x = Double(i) / Double(steps)
                    let gray = 0.5 + amplitude * sin(2 * Double.pi * spatialFrequency * x)
                    let c = min(max(gray, 0), 1)
                    let rect = CGRect(x: CGFloat(x * w), y: 0, width: CGFloat(w / Double(steps) + 1), height: CGFloat(h))
                    context.fill(Path(rect), with: .color(Color(red: c, green: c, blue: c)))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(16)
        .navigationTitle("Contrast Sensitivity Chart")
        .navigationBarTitleDisplayMode(.inline)
    }
}
