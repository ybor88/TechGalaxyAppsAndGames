// © Roberto Di Flumeri
import SwiftUI

/// Tavole pseudoisocromatiche generate proceduralmente (NON riproduzioni delle tavole
/// Ishihara originali, protette da copyright). Utile come screening di massima,
/// non sostituisce un test Ishihara/D-15 certificato.
private struct Plate { let figureDigit: Int; let figureColor: Color; let bgColor: Color }

private let plates: [Plate] = [
    Plate(figureDigit: 12, figureColor: Color(red: 0xE0 / 255, green: 0x7A / 255, blue: 0x5F / 255), bgColor: Color(red: 0x8A / 255, green: 0xA2 / 255, blue: 0x9E / 255)),
    Plate(figureDigit: 8, figureColor: Color(red: 0xD6 / 255, green: 0x28 / 255, blue: 0x28 / 255), bgColor: Color(red: 0x60 / 255, green: 0x6C / 255, blue: 0x38 / 255)),
    Plate(figureDigit: 29, figureColor: Color(red: 0xEE / 255, green: 0x96 / 255, blue: 0x4B / 255), bgColor: Color(red: 0x3D / 255, green: 0x40 / 255, blue: 0x5B / 255)),
]

struct ColorVisionTestView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                ForEach(plates.indices, id: \.self) { i in
                    let plate = plates[i]
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Numero visibile? (annota risposta paziente)").font(.footnote)
                        Canvas { context, size in
                            var rng = SeededGenerator(seed: UInt64(plate.figureDigit))
                            let w = Double(size.width), h = Double(size.height)
                            for _ in 0..<1200 {
                                let x = Double.random(in: 0..<w, using: &rng)
                                let y = Double.random(in: 0..<h, using: &rng)
                                let r = 4 + Double.random(in: 0..<6, using: &rng)
                                context.fill(Path(ellipseIn: CGRect(x: CGFloat(x - r / 2), y: CGFloat(y - r / 2), width: CGFloat(r), height: CGFloat(r))), with: .color(plate.bgColor.opacity(0.9)))
                            }
                            for _ in 0..<500 {
                                let x = w * (0.3 + Double.random(in: 0..<0.4, using: &rng))
                                let y = h * (0.3 + Double.random(in: 0..<0.4, using: &rng))
                                let r = 4 + Double.random(in: 0..<6, using: &rng)
                                context.fill(Path(ellipseIn: CGRect(x: CGFloat(x - r / 2), y: CGFloat(y - r / 2), width: CGFloat(r), height: CGFloat(r))), with: .color(plate.figureColor.opacity(0.9)))
                            }
                        }
                        .frame(height: 220)
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle("Test colori (pseudoisocromatico)")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Generatore pseudo-casuale deterministico (seedato) per riprodurre lo stesso pattern
/// ad ogni ridisegno, equivalente a `kotlin.random.Random(seed)`.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed &+ 0x9E3779B97F4A7C15 }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
