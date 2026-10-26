import AVKit
import Combine
import HeadUpCore
import SwiftUI

/// Owns the system window independently of SwiftUI tab and scene lifetimes.
@MainActor
final class PosturePictureInPicture: NSObject, ObservableObject, @preconcurrency AVPictureInPictureControllerDelegate {
    @Published private(set) var state: MonitoringVisibility.PictureInPicture = .stopped
    @Published private(set) var restoreRequest = 0
    @Published private(set) var errorMessage: String?
    var stateChanged: ((MonitoringVisibility.PictureInPicture) -> Void)?
    private weak var sourceView: UIView?
    private var controller: AVPictureInPictureController?
    private let content = AVPictureInPictureVideoCallViewController()
    private let preview = PreviewSurface()
    private let label = UILabel()
    private var startTimeout: Task<Void, Never>?
    private var restoreCompletion: ((Bool) -> Void)?
    private var restoreTimeout: Task<Void, Never>?
    private var copy = HeadUpCopy(language: .system)
    private var ownsAudioSession = false
    private var needsSourceUpdate = false
    private var stopping = false

    override init() {
        super.init()
        content.preferredContentSize = CGSize(width: 270, height: 360)
        content.view.backgroundColor = .black
        preview.translatesAutoresizingMaskIntoConstraints = false
        content.view.addSubview(preview)
        label.translatesAutoresizingMaskIntoConstraints = false
        label.textAlignment = .center
        label.font = .systemFont(ofSize: 16, weight: .semibold)
        label.numberOfLines = 2
        label.adjustsFontSizeToFitWidth = true
        label.minimumScaleFactor = 0.65
        label.backgroundColor = UIColor.black.withAlphaComponent(0.8)
        content.view.addSubview(label)
        NSLayoutConstraint.activate([
            preview.leadingAnchor.constraint(equalTo: content.view.leadingAnchor),
            preview.trailingAnchor.constraint(equalTo: content.view.trailingAnchor),
            preview.topAnchor.constraint(equalTo: content.view.topAnchor),
            preview.bottomAnchor.constraint(equalTo: content.view.bottomAnchor),
            label.leadingAnchor.constraint(equalTo: content.view.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: content.view.trailingAnchor),
            label.bottomAnchor.constraint(equalTo: content.view.bottomAnchor),
            label.heightAnchor.constraint(equalToConstant: 54)
        ])
        preview.layer.borderWidth = 5
    }

    func attach(source: UIView, session: AVCaptureSession) {
        if sourceView !== source { sourceView = source; needsSourceUpdate = true }
        preview.preview.session = session
        configureSourceIfNeeded()
    }

    private func configureSourceIfNeeded() {
        guard state == .stopped, !stopping, needsSourceUpdate, let sourceView else { return }
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        let source = AVPictureInPictureController.ContentSource(activeVideoCallSourceView: sourceView, contentViewController: content)
        controller = AVPictureInPictureController(contentSource: source)
        controller?.delegate = self
        // Explicit opt-in each session; merely leaving HeadUp must not start a camera window.
        controller?.canStartPictureInPictureAutomaticallyFromInline = false
        needsSourceUpdate = false
    }

    func start(cameraSupported: Bool, copy: HeadUpCopy) {
        guard state == .stopped else { return }
        self.copy = copy
        errorMessage = nil
        guard !stopping else {
            errorMessage = copy.text("The previous window is closing. Please try again in a moment.", "上一個小視窗正在關閉，請稍後重試。")
            return
        }
        guard cameraSupported else {
            errorMessage = copy.text("This device or build cannot keep the camera active in a small window.", "此裝置或版本不支援在小視窗中持續使用相機。")
            return
        }
        guard let controller, sourceView?.window != nil else {
            errorMessage = copy.text("Keep the Scan screen open to start the small window.", "請在掃描頁面開啟小視窗。")
            return
        }
        do {
            // PiP requires a playback audio session; do not record audio or play silent audio.
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.playback, mode: .moviePlayback, options: [.mixWithOthers])
            try audio.setActive(true)
            ownsAudioSession = true
        } catch {
            errorMessage = copy.text("Could not prepare the small window. Please try again.", "無法準備小視窗，請重試。")
            return
        }
        setState(.starting)
        // Readiness can lag behind camera start/audio-session activation. Wait without
        // blocking the main thread, and bound the handoff if AVKit never completes it.
        startTimeout = Task { [weak self, weak controller] in
            for _ in 0..<30 {
                guard let self, self.state == .starting, !Task.isCancelled else { return }
                if controller?.isPictureInPicturePossible == true {
                    controller?.startPictureInPicture()
                    break
                }
                try? await Task.sleep(for: .milliseconds(100))
            }
            try? await Task.sleep(for: .seconds(5))
            guard let self, self.state == .starting, !Task.isCancelled else { return }
            controller?.stopPictureInPicture()
            self.fail()
        }
    }

    func stop() {
        startTimeout?.cancel()
        startTimeout = nil
        stopping = stopping || controller?.isPictureInPictureActive == true
        controller?.stopPictureInPicture()
        finish()
    }

    func update(metrics: PostureMetrics?, points: [LandmarkPoint], size: CGSize,
                front: Bool, rotation: CGFloat, warning: Bool, copy: HeadUpCopy) {
        self.copy = copy
        preview.points = points
        preview.imageSize = size
        preview.front = front
        preview.rotationOverride = rotation
        preview.setNeedsLayout()
        let color: UIColor = warning ? .systemRed : metrics == nil ? .white : UIColor(HeadUpStyle.color(for: metrics?.zone))
        label.textColor = color
        label.text = metrics.map { "HeadUp · \(copy.zone($0.zone))\n\($0.angleDegrees)°" }
            ?? copy.text("HeadUp · Keep face and shoulders in view", "HeadUp · 請讓臉部與雙肩入鏡")
        preview.layer.borderColor = (warning ? UIColor.systemRed : .clear).cgColor
    }

    func completeRestorationIfActive() {
        guard UIApplication.shared.applicationState == .active, sourceView?.window != nil,
              let completion = restoreCompletion else { return }
        restoreCompletion = nil
        restoreTimeout?.cancel()
        completion(true)
    }

    private func setState(_ value: MonitoringVisibility.PictureInPicture) {
        guard state != value else { return }
        state = value
        stateChanged?(value)
    }

    private func finish() {
        startTimeout?.cancel()
        startTimeout = nil
        if ownsAudioSession {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            ownsAudioSession = false
        }
        setState(.stopped)
        configureSourceIfNeeded()
    }

    private func fail() {
        errorMessage = copy.text("The system could not start the camera window. Continue in HeadUp or try again on a supported device.", "系統無法啟動相機小視窗。請在 HeadUp 內繼續，或於支援的裝置重試。")
        finish()
    }

    func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        guard pictureInPictureController === controller else { return }
        guard state == .starting else { pictureInPictureController.stopPictureInPicture(); return }
        startTimeout?.cancel()
        startTimeout = nil
        setState(.active)
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    failedToStartPictureInPictureWithError error: Error) {
        guard pictureInPictureController === controller else { return }
        stopping = false
        fail()
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        guard pictureInPictureController === controller else { return }
        stopping = false
        finish()
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        guard pictureInPictureController === controller else { completionHandler(false); return }
        restoreCompletion?(false)
        restoreCompletion = completionHandler
        restoreRequest += 1
        restoreTimeout?.cancel()
        restoreTimeout = Task { [weak self] in
            for _ in 0..<30 {
                try? await Task.sleep(for: .milliseconds(100))
                guard let self, !Task.isCancelled else { return }
                self.completeRestorationIfActive()
                if self.restoreCompletion == nil { return }
            }
            self?.restoreCompletion?(false)
            self?.restoreCompletion = nil
        }
    }
}
