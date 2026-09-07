// © Roberto Di Flumeri
import AVFoundation
import Photos
import SwiftUI

/// Schermata generica: anteprima fotocamera + scatto foto salvata nella libreria foto + torcia.
/// Riutilizzata per Funduscopia, Fotografia lampada a fessura, Penlight, ecc.
/// Equivalente a `CameraCaptureScreen.kt`.
struct CameraCaptureView: View {
    let title: String
    let instructions: String
    var facing: AVCaptureDevice.Position = .back

    @StateObject private var controller = CameraController()
    @State private var torchOn = false

    var body: some View {
        VStack(spacing: 0) {
            Text(instructions)
                .font(.footnote)
                .padding(16)

            ZStack {
                if controller.isAuthorized {
                    CameraPreviewLayerView(controller: controller)
                } else {
                    Color.black
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            HStack(spacing: 32) {
                Button(action: {
                    torchOn.toggle()
                    controller.setTorch(on: torchOn)
                }) {
                    Image(systemName: torchOn ? "bolt.fill" : "bolt")
                        .font(.title2)
                }

                Button(action: {
                    controller.capturePhoto { image in
                        guard let image else {
                            controller.lastSavedMessage = "Errore scatto"
                            return
                        }
                        saveToLibrary(image)
                    }
                }) {
                    Label("Scatta", systemImage: "camera.fill")
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(AppColor.onBackground)
                        .foregroundColor(AppColor.background)
                        .cornerRadius(12)
                }
            }
            .padding(16)

            if let message = controller.lastSavedMessage {
                Text(message)
                    .font(.footnote)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { controller.requestPermissionAndStart(position: facing) }
        .onDisappear { controller.stop() }
    }

    private func saveToLibrary(_ image: UIImage) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { controller.lastSavedMessage = "Permesso libreria foto negato" }
                return
            }
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { success, error in
                DispatchQueue.main.async {
                    controller.lastSavedMessage = success ? "Foto salvata nella libreria" : "Errore salvataggio: \(error?.localizedDescription ?? "sconosciuto")"
                }
            }
        }
    }
}
