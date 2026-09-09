// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Disco di Placido: anelli concentrici proiettati sullo schermo, osservati per riflesso
/// attraverso la fotocamera frontale per screening di irregolarità corneali.
struct PlacidoTopographyView: View {
    var body: some View {
        VStack(spacing: 0) {
            Text("Avvicina l'occhio del paziente allo schermo e osserva, tramite la fotocamera frontale, la regolarità del riflesso degli anelli sulla cornea. Anelli irregolari/ovalizzati suggeriscono astigmatismo o cheratocono.")
                .font(.footnote)
                .padding(16)

            ZStack {
                CameraPreview(facing: .front)
                Canvas { context, size in
                    let center = CGPoint(x: size.width / 2, y: size.height / 2)
                    let maxRadius = min(size.width, size.height) / 2 * 0.9
                    for i in 1...8 {
                        let r = maxRadius * CGFloat(i) / 8
                        context.stroke(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)), with: .color(.white), lineWidth: 2)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("Topografia con disco di Placido")
        .navigationBarTitleDisplayMode(.inline)
    }
}
