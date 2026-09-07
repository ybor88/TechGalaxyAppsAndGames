// © Roberto Di Flumeri
import SwiftUI

struct DuochromeView: View {
    var body: some View {
        VStack(spacing: 0) {
            Text("Chiedi al paziente su quale sfondo le lettere appaiono più nitide (rosso = miopizzare, verde = ipermetropizzare).")
                .padding(16)
            HStack(spacing: 0) {
                ZStack {
                    Color.red
                    Text("O X V").font(.largeTitle).foregroundColor(.black)
                }
                ZStack {
                    Color(red: 0, green: 0x66 / 255, blue: 0x51 / 255)
                    Text("O X V").font(.largeTitle).foregroundColor(.black)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("Duochrome Test")
        .navigationBarTitleDisplayMode(.inline)
    }
}
