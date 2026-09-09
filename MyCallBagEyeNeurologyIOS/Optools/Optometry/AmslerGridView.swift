// © Roberto Di Flumeri
import SwiftUI

struct AmslerGridView: View {
    var body: some View {
        VStack(alignment: .center, spacing: 16) {
            Text("Copri un occhio, fissa il punto centrale a ~30cm. Segnala eventuali linee ondulate, distorte o aree mancanti.")
                .padding(.horizontal, 16)
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white))
                let cols = 20, rows = 20
                let cellW = size.width / CGFloat(cols)
                let cellH = size.height / CGFloat(rows)
                var grid = Path()
                for i in 0...cols {
                    grid.move(to: CGPoint(x: CGFloat(i) * cellW, y: 0))
                    grid.addLine(to: CGPoint(x: CGFloat(i) * cellW, y: size.height))
                }
                for j in 0...rows {
                    grid.move(to: CGPoint(x: 0, y: CGFloat(j) * cellH))
                    grid.addLine(to: CGPoint(x: size.width, y: CGFloat(j) * cellH))
                }
                context.stroke(grid, with: .color(.black), lineWidth: 1)
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                context.fill(Path(ellipseIn: CGRect(x: center.x - 4, y: center.y - 4, width: 8, height: 8)), with: .color(.red))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 16)
        .navigationTitle("Amsler Grid")
        .navigationBarTitleDisplayMode(.inline)
    }
}
