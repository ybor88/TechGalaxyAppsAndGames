// © Roberto Di Flumeri
import CoreMotion
import SwiftUI

/// Goniometro basato su accelerometro: misura l'inclinazione del dispositivo rispetto
/// alla verticale per stimare l'ampiezza articolare (ROM).
struct GoniometroView: View {
    @StateObject private var motion = MotionAngleProvider()
    @State private var zeroOffset: Double = 0

    private var displayAngle: Double {
        (motion.rawAngleDeg - zeroOffset).truncatingRemainder(dividingBy: 360) + (motion.rawAngleDeg - zeroOffset < 0 ? 360 : 0)
    }

    var body: some View {
        VStack(spacing: 32) {
            Text("Appoggia il bordo del dispositivo lungo il segmento articolare da misurare (es. braccio), azzera nella posizione di partenza, poi leggi l'angolo dopo il movimento.")
                .font(.footnote)

            Text("\(Int(displayAngle.rounded()))°")
                .font(.system(size: 64, weight: .bold))

            Button("Azzera (posizione di partenza)") { zeroOffset = motion.rawAngleDeg }
                .buttonStyle(.borderedProminent)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle("Goniometro")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { motion.start() }
        .onDisappear { motion.stop() }
    }
}

private final class MotionAngleProvider: ObservableObject {
    @Published var rawAngleDeg: Double = 0
    private let manager = CMMotionManager()

    func start() {
        guard manager.isAccelerometerAvailable else { return }
        manager.accelerometerUpdateInterval = 1.0 / 30.0
        manager.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let self, let data else { return }
            let x = data.acceleration.x
            let y = data.acceleration.y
            self.rawAngleDeg = atan2(x, y) * 180 / .pi
        }
    }

    func stop() {
        manager.stopAccelerometerUpdates()
    }
}
