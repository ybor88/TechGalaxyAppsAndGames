// © Roberto Di Flumeri
import SwiftUI

struct AnisometropiaView: View {
    @State private var odSphere = ""
    @State private var osSphere = ""
    @State private var odCylinder = ""
    @State private var osCylinder = ""

    private var odSe: Double { (Double(odSphere) ?? 0) + (Double(odCylinder) ?? 0) / 2 }
    private var osSe: Double { (Double(osSphere) ?? 0) + (Double(osCylinder) ?? 0) / 2 }
    private var diff: Double { abs(odSe - osSe) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Inserisci sfera e cilindro per calcolare l'equivalente sferico e la differenza tra i due occhi.")
                TextField("OD Sfera (D)", text: $odSphere).keyboardType(.numbersAndPunctuation).textFieldStyle(.roundedBorder)
                TextField("OD Cilindro (D)", text: $odCylinder).keyboardType(.numbersAndPunctuation).textFieldStyle(.roundedBorder)
                TextField("OS Sfera (D)", text: $osSphere).keyboardType(.numbersAndPunctuation).textFieldStyle(.roundedBorder)
                TextField("OS Cilindro (D)", text: $osCylinder).keyboardType(.numbersAndPunctuation).textFieldStyle(.roundedBorder)

                Text("Equivalente sferico OD: \(String(format: "%.2f", odSe)) D").font(.headline)
                Text("Equivalente sferico OS: \(String(format: "%.2f", osSe)) D").font(.headline)
                Text("Anisometropia: \(String(format: "%.2f", diff)) D").font(.title2)
                if diff >= 2.0 {
                    Text("Clinicamente significativa (≥2D): valutare rischio aniseiconia.")
                        .foregroundColor(.red)
                }
            }
            .padding(16)
        }
        .navigationTitle("Anisometropia")
        .navigationBarTitleDisplayMode(.inline)
    }
}
