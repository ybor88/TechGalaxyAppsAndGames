// © Roberto Di Flumeri
import SwiftUI

struct ComingSoonView: View {
    let title: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "hammer")
                .font(.system(size: 48))
            Text("In sviluppo")
                .font(.title3)
            Text("\"\(title)\" non è ancora implementato in questa versione.")
                .font(.body)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
