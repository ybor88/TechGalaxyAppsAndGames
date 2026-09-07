// © Roberto Di Flumeri
import SwiftUI

struct ClockDrawingView: View {
    @State private var paths: [Path] = []
    @State private var currentPath: Path?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Chiedi al paziente di disegnare un orologio indicando le ore 11:10. Valuta: cerchio, numeri (posizione/completezza), lancette.")

            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white))
                for path in paths {
                    context.stroke(path, with: .color(.black), lineWidth: 5)
                }
                if let currentPath {
                    context.stroke(currentPath, with: .color(.black), lineWidth: 5)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if currentPath == nil {
                            var p = Path()
                            p.move(to: value.startLocation)
                            currentPath = p
                        }
                        currentPath?.addLine(to: value.location)
                    }
                    .onEnded { _ in
                        if let currentPath { paths.append(currentPath) }
                        currentPath = nil
                    }
            )
        }
        .padding(16)
        .navigationTitle("Clock Drawing Test")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Cancella") { paths.removeAll() }
            }
        }
    }
}
