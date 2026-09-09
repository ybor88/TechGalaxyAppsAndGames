// © Roberto Di Flumeri
import SwiftUI

struct AstigmatismDialView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Chiedi al paziente quale linea appare più nera/nitida. L'asse indicato corrisponde all'asse del cilindro negativo (+90° all'asse dell'errore).")

            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let radius = Double(min(size.width, size.height)) / 2 * 0.9
                var deg = 0
                while deg < 180 {
                    let rad = Double(deg) * Double.pi / 180
                    let dx = CGFloat(radius * cos(rad))
                    let dy = CGFloat(radius * sin(rad))
                    var path = Path()
                    path.move(to: CGPoint(x: center.x - dx, y: center.y - dy))
                    path.addLine(to: CGPoint(x: center.x + dx, y: center.y + dy))
                    context.stroke(path, with: .color(.black), lineWidth: 3)
                    deg += 10
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.white)
        }
        .padding(16)
        .navigationTitle("Astigmatism Dial")
        .navigationBarTitleDisplayMode(.inline)
    }
}
