// © Roberto Di Flumeri
import Combine
import SwiftUI

/// Tamburo optocinetico: strisce verticali in movimento continuo per indurre ed osservare
/// il nistagmo optocinetico (OKN).
struct OknDrumView: View {
    @State private var speed: Double = 200 // px/s
    @State private var reversed = false
    @State private var offset: Double = 0
    private let stripeWidth: Double = 80

    private let timer = Timer.publish(every: 1.0 / 60.0, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Velocità: \(Int(speed)) px/s")
                Slider(value: $speed, in: 50...500)
                HStack {
                    Button(action: { reversed = false }) {
                        Text("→ Destra").padding(8).background(reversed ? Color.clear : AppColor.surfaceVariant).cornerRadius(8)
                    }
                    Button(action: { reversed = true }) {
                        Text("← Sinistra").padding(8).background(reversed ? AppColor.surfaceVariant : Color.clear).cornerRadius(8)
                    }
                }
            }
            .padding(16)

            Canvas { context, size in
                let sw = CGFloat(stripeWidth)
                var x = CGFloat(offset.truncatingRemainder(dividingBy: stripeWidth)) - sw
                var index = 0
                while x < size.width + sw {
                    let color: Color = index % 2 == 0 ? .black : .white
                    context.fill(Path(CGRect(x: x, y: 0, width: sw, height: size.height)), with: .color(color))
                    x += sw
                    index += 1
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .onReceive(timer) { _ in
                let delta = speed / 60.0
                offset += reversed ? -delta : delta
            }
        }
        .navigationTitle("OKN Drum")
        .navigationBarTitleDisplayMode(.inline)
    }
}
