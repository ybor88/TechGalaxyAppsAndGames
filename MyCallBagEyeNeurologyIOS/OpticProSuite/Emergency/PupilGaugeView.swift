// © Roberto Di Flumeri
import SwiftUI

/// Anelli di riferimento da 1 a 9 mm da confrontare con la pupilla del paziente sullo
/// schermo (calibrare con oggetto noto).
struct PupilGaugeView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Accosta lo schermo alla guancia del paziente e confronta il diametro pupillare con gli anelli (mm). Nota: la scala reale dipende dal dispositivo; calibrare prima con un oggetto di dimensione nota.")

            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let mmToPx: CGFloat = 8 // fattore approssimativo, calibrare per dispositivo
                for mm in 1...9 {
                    let r = CGFloat(mm) * mmToPx
                    context.stroke(Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)), with: .color(.black), lineWidth: 2)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(16)
        .navigationTitle("Calibro pupillare")
        .navigationBarTitleDisplayMode(.inline)
    }
}
