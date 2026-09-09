// © Roberto Di Flumeri
import AVFoundation
import SwiftUI

/// Motore fotocamera riutilizzabile basato su AVFoundation, equivalente a CameraX
/// in CameraPreview.kt / CameraCaptureScreen.kt. Gestisce permesso, sessione,
/// anteprima, torcia e scatto foto.
final class CameraController: NSObject, ObservableObject {
    @Published var isAuthorized = false
    @Published var lastSavedMessage: String?

    let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private var device: AVCaptureDevice?
    private var currentPosition: AVCaptureDevice.Position = .back
    private var photoCompletion: ((UIImage?) -> Void)?
    private let sessionQueue = DispatchQueue(label: "com.opticpro.suite.camera.session")

    func requestPermissionAndStart(position: AVCaptureDevice.Position) {
        currentPosition = position
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            isAuthorized = true
            configureAndStart(position: position)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    self?.isAuthorized = granted
                    if granted { self?.configureAndStart(position: position) }
                }
            }
        default:
            isAuthorized = false
        }
    }

    private func configureAndStart(position: AVCaptureDevice.Position) {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.session.beginConfiguration()
            self.session.inputs.forEach { self.session.removeInput($0) }
            self.session.outputs.forEach { self.session.removeOutput($0) }
            self.session.sessionPreset = .photo

            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
                  let input = try? AVCaptureDeviceInput(device: device) else {
                self.session.commitConfiguration()
                return
            }
            self.device = device
            if self.session.canAddInput(input) { self.session.addInput(input) }
            if self.session.canAddOutput(self.photoOutput) { self.session.addOutput(self.photoOutput) }
            self.session.commitConfiguration()
            self.session.startRunning()
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            self?.session.stopRunning()
        }
    }

    func setTorch(on: Bool) {
        guard let device, device.hasTorch else { return }
        try? device.lockForConfiguration()
        device.torchMode = on ? .on : .off
        device.unlockForConfiguration()
    }

    func capturePhoto(completion: @escaping (UIImage?) -> Void) {
        photoCompletion = completion
        let settings = AVCapturePhotoSettings()
        photoOutput.capturePhoto(with: settings, delegate: self)
    }
}

extension CameraController: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else {
            DispatchQueue.main.async { self.photoCompletion?(nil) }
            return
        }
        DispatchQueue.main.async { self.photoCompletion?(image) }
    }
}

/// Layer di anteprima fotocamera, equivalente a `PreviewView` (CameraX) su AndroidView.
struct CameraPreviewLayerView: UIViewRepresentable {
    @ObservedObject var controller: CameraController

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.videoPreviewLayer.session = controller.session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}

    final class PreviewUIView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

/// Anteprima fotocamera semplice riutilizzabile (senza scatto), equivalente a `CameraPreview.kt`.
struct CameraPreview: View {
    var facing: AVCaptureDevice.Position = .back
    @StateObject private var controller = CameraController()

    var body: some View {
        ZStack {
            if controller.isAuthorized {
                CameraPreviewLayerView(controller: controller)
            } else {
                Color.black
            }
        }
        .onAppear { controller.requestPermissionAndStart(position: facing) }
        .onDisappear { controller.stop() }
    }
}
