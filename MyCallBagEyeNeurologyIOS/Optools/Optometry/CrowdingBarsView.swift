// © Roberto Di Flumeri
import SwiftUI

// Stessa scala Snellen a 6m usata in VisionChartView.
private let singleOptotypeLines: [(String, Double)] = [
    ("6/60", 87.3), ("6/36", 52.4), ("6/24", 34.9), ("6/18", 26.2),
    ("6/12", 17.5), ("6/9", 13.1), ("6/6", 8.7)
]
private let mmPerPoint = 0.35

struct CrowdingBarsView: View {
    @State private var distanceM: Double = 3
    @State private var lineIndex: Double = 4 // 6/12 di default

    var body: some View {
        let idx = Int(lineIndex.rounded())
        let (label, baseHeightMm) = singleOptotypeLines[idx]
        let heightMm = baseHeightMm * (distanceM / 6.0)
        let heightPt = CGFloat(heightMm / mmPerPoint)
        let gapPt = heightPt * 0.5

        VStack(alignment: .leading, spacing: 8) {
            Text("Ottotipo singolo affiancato da barre di affollamento, utile per screening pediatrico (amblyopia).")
            Text("Distanza di test: \(String(format: "%.1f", distanceM)) m")
            Slider(value: $distanceM, in: 1...6)
            Text("Riga: \(label)")
            Slider(value: $lineIndex, in: 0...Double(singleOptotypeLines.count - 1), step: 1)

            HStack(spacing: gapPt) {
                Rectangle().fill(Color.black).frame(width: 4, height: heightPt + gapPt)
                Text("E").font(.system(size: heightPt))
                Rectangle().fill(Color.black).frame(width: 4, height: heightPt + gapPt)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
        .padding(16)
        .navigationTitle("Crowding bars")
        .navigationBarTitleDisplayMode(.inline)
    }
}
