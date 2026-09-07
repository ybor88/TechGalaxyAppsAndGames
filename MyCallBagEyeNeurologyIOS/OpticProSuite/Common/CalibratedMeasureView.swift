// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Strumento generico "misura calibrata a 2 punti": l'utente allinea 2 marker su un
/// oggetto di larghezza nota e 2 marker sulla grandezza da misurare (es. PD, esoftalmometria,
/// riflesso corneale). Riutilizzato da più strumenti con etichette/formule diverse.
/// Equivalente a `CalibratedMeasureScreen.kt`.
struct CalibratedMeasureView: View {
    let title: String
    let instructions: String
    var defaultRefWidthMm: String = "85.6"
    let resultLabel: String
    var facing: AVCaptureDevice.Position = .front
    let computeResult: (Double) -> String

    @State private var refWidthMm: String
    @State private var calA = CGPoint(x: 100, y: 220)
    @State private var calB = CGPoint(x: 260, y: 220)
    @State private var targetA = CGPoint(x: 120, y: 360)
    @State private var targetB = CGPoint(x: 240, y: 360)

    init(title: String, instructions: String, defaultRefWidthMm: String = "85.6", resultLabel: String, facing: AVCaptureDevice.Position = .front, computeResult: @escaping (Double) -> String) {
        self.title = title
        self.instructions = instructions
        self.defaultRefWidthMm = defaultRefWidthMm
        self.resultLabel = resultLabel
        self.facing = facing
        self.computeResult = computeResult
        _refWidthMm = State(initialValue: defaultRefWidthMm)
    }

    private var measuredMm: Double {
        let calPx = hypot(Double(calB.x - calA.x), Double(calB.y - calA.y))
        let targetPx = hypot(Double(targetB.x - targetA.x), Double(targetB.y - targetA.y))
        let widthMm = Double(refWidthMm) ?? 0
        return calPx > 0 ? targetPx / calPx * widthMm : 0
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text(instructions).font(.footnote)
                TextField("Larghezza oggetto di riferimento (mm)", text: $refWidthMm)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)
            }
            .padding(12)

            GeometryReader { geo in
                ZStack {
                    CameraPreview(facing: facing)
                    markerOverlay
                }
                .contentShape(Rectangle())
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack {
                Text(resultLabel).font(.headline)
                Spacer()
                Text(computeResult(measuredMm)).font(.title3)
            }
            .padding(16)
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var markerOverlay: some View {
        Canvas { context, _ in
            var path = Path()
            path.move(to: calA); path.addLine(to: calB)
            context.stroke(path, with: .color(.blue), lineWidth: 4)

            var path2 = Path()
            path2.move(to: targetA); path2.addLine(to: targetB)
            context.stroke(path2, with: .color(.green), lineWidth: 4)

            for point in [calA, calB] {
                context.fill(Path(ellipseIn: CGRect(x: point.x - 11, y: point.y - 11, width: 22, height: 22)), with: .color(.blue))
            }
            for point in [targetA, targetB] {
                context.fill(Path(ellipseIn: CGRect(x: point.x - 11, y: point.y - 11, width: 22, height: 22)), with: .color(.green))
            }
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    let markers: [(CGPoint, (CGPoint) -> Void)] = [
                        (calA, { calA = $0 }),
                        (calB, { calB = $0 }),
                        (targetA, { targetA = $0 }),
                        (targetB, { targetB = $0 }),
                    ]
                    func distanceSquared(_ p: CGPoint) -> CGFloat {
                        let dx = p.x - value.location.x
                        let dy = p.y - value.location.y
                        return dx * dx + dy * dy
                    }
                    let nearest = markers.min { distanceSquared($0.0) < distanceSquared($1.0) }
                    nearest?.1(value.location)
                }
        )
    }
}
