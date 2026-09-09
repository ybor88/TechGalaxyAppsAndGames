// © Roberto Di Flumeri
import SwiftUI

struct QtcCalculatorView: View {
    @State private var qtMs = ""
    @State private var rrSec = "" // intervallo RR in secondi (60/FC)

    private var qtc: Double? {
        guard let qt = Double(qtMs), let rr = Double(rrSec), rr > 0 else { return nil }
        return qt / sqrt(rr)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("QT misurato (ms)", text: $qtMs).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            TextField("Intervallo RR (secondi, 60/FC)", text: $rrSec).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)

            if let qtc {
                Text("QTc: \(String(format: "%.0f", qtc)) ms").font(.title2)
                if qtc > 450 {
                    Text("Prolungato: valutare rischio aritmico.").foregroundColor(.red)
                }
            }
        }
        .padding(16)
        .navigationTitle("QTc (Bazett)")
        .navigationBarTitleDisplayMode(.inline)
    }
}
