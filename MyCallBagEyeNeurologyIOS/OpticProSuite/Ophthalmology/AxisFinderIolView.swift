// © Roberto Di Flumeri
import SwiftUI

/// Marcatore d'asse per IOL toriche: ruota la linea trascinandola fino all'asse target
/// pre-calcolato.
struct AxisFinderIolView: View {
    @State private var targetAxis: Double = 90
    @State private var currentAngleDeg: Double = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Asse target (da biometria): \(Int(targetAxis.rounded()))°")
            Slider(value: $targetAxis, in: 0...180)
            Text("Ruota la linea trascinandola per marcare l'asse sull'occhio del paziente.")

            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let radius = min(size.width, size.height) / 2 * 0.85
                let radiusD = Double(radius)

                context.stroke(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)), with: .color(.gray), lineWidth: 2)

                func axisLine(_ deg: Double) -> Path {
                    let rad = deg * Double.pi / 180
                    let dx = CGFloat(radiusD * cos(rad))
                    let dy = CGFloat(radiusD * sin(rad))
                    var p = Path()
                    p.move(to: CGPoint(x: center.x - dx, y: center.y - dy))
                    p.addLine(to: CGPoint(x: center.x + dx, y: center.y + dy))
                    return p
                }
                context.stroke(axisLine(targetAxis), with: .color(.green), lineWidth: 4)
                context.stroke(axisLine(currentAngleDeg), with: .color(.red), lineWidth: 6)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .overlay(
                GeometryReader { geo in
                    Color.clear
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
                                    let dx = Double(value.location.x - center.x)
                                    let dy = Double(value.location.y - center.y)
                                    var angle = atan2(dy, dx) * 180 / Double.pi
                                    if angle < 0 { angle += 180 }
                                    if angle > 180 { angle -= 180 }
                                    currentAngleDeg = angle
                                }
                        )
                }
            )

            let diff = abs(currentAngleDeg - targetAxis)
            Text("Marcatura corrente: \(Int(currentAngleDeg.rounded()))°  |  scarto: \(Int(diff.rounded()))°")
                .font(.headline)
        }
        .padding(16)
        .navigationTitle("Axis Finder IOL toriche")
        .navigationBarTitleDisplayMode(.inline)
    }
}
