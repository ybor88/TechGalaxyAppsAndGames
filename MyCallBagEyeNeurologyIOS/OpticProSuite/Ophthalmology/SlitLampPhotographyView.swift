// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

struct SlitLampPhotographyView: View {
    var body: some View {
        CameraCaptureView(
            title: "Fotografia lampada a fessura",
            instructions: "Allinea la fotocamera all'oculare della lampada a fessura o avvicinala al segmento anteriore del paziente. Usa la torcia per illuminazione supplementare se necessario, poi scatta.",
            facing: .back
        )
    }
}
