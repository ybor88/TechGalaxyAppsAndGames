// © Roberto Di Flumeri
import SwiftUI

struct Worth4DotView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Richiede occhiali rosso/verde. Il paziente riferisce quanti punti vede: 2=soppressione OD, 3=soppressione OS, 4=fusione, 5=diplopia.")

            Canvas { context, size in
                let w = size.width, h = size.height
                let r: CGFloat = 40
                let green = Color(red: 0, green: 0x66 / 255, blue: 0x51 / 255)
                func dot(_ p: CGPoint, _ color: Color) {
                    context.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)), with: .color(color))
                }
                dot(CGPoint(x: w / 2, y: h * 0.25), .red)
                dot(CGPoint(x: w * 0.3, y: h * 0.55), green)
                dot(CGPoint(x: w * 0.7, y: h * 0.55), green)
                dot(CGPoint(x: w / 2, y: h * 0.8), .white)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.black)
        }
        .padding(16)
        .navigationTitle("Worth 4 Dot Test")
        .navigationBarTitleDisplayMode(.inline)
    }
}
