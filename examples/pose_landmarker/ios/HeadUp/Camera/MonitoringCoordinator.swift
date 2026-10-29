import AVFoundation
import AudioToolbox
import Combine
import CoreMotion
import HeadUpCore
import UIKit

enum MonitoringStatus { case idle, starting, monitoring, noPose, calibrating, paused, permissionDenied, unavailable, failed }

@MainActor
final class MonitoringCoordinator: ObservableObject {
    @Published private(set) var status: MonitoringStatus = .idle
    @Published private(set) var isMonitoring = false
    @Published private(set) var isCalibrating = false
    @Published private(set) var calibrationRemaining = 3
    @Published private(set) var metrics: PostureMetrics?
    @Published private(set) var landmarks: [LandmarkPoint] = []
    @Published private(set) var warningActive = false
    @Published private(set) var isFrontCamera = true
    @Published private(set) var isFloatingWindowOpen = false
    // Exposed only by the opt-in device test UI; counts real inference callbacks, not poses.
    @Published private(set) var backgroundFrameCount = 0
    @Published private(set) var frameSize = CGSize(width: 480, height: 640)
    @Published var errorMessage: String?
    let camera = CameraPipeline()
    let pictureInPicture = PosturePictureInPicture()
    let liveActivity = PostureActivityService()
    private(set) var monitoringSessionID: UUID?
    private var cameraStartedForSession = false
    private var languageObserver: AnyCancellable?
    private let motion = CMMotionManager()
    private let store: HeadUpStore
    private let analyzer = PostureAnalyzer()
    private let alerts = AlertEngine()
    private var token = UUID()
    private var wantsMonitoring = false
    private var visibility = MonitoringVisibility()
    private var interrupted = false
    private var previousValidMs: Int64?
    private var lastFrameAt: TimeInterval = 0
    private var watchdog: Timer?
    private var calibrationStart: Int64?
    private var calibrationLastFrameMs: Int64?
    private var calibrationSamples: [PostureMetrics] = []
    private var pendingCalibration = false
    private var rotation: CGFloat = 90
    var captureRotation: CGFloat { rotation }
    private var observers: [NSObjectProtocol] = []

    init(store: HeadUpStore) {
        self.store = store
        MonitoringIntentRouter.shared.handler = { [weak self] sessionID in
            guard let self, self.monitoringSessionID == sessionID else { return }
            self.pause()
            await self.liveActivity.waitForPendingUpdates()
        }
        languageObserver = store.$language.removeDuplicates().sink { [weak self] _ in
            Task { @MainActor in self?.publishActivity(force: true) }
        }
        visibility.protectedDataAvailable = UIApplication.shared.isProtectedDataAvailable
        pictureInPicture.stateChanged = { [weak self] state in
            guard let self else { return }
            self.visibility.pictureInPicture = state
            self.isFloatingWindowOpen = state != .stopped
            if !self.visibility.shouldKeepCapture && self.isMonitoring {
                // Closing the window while another app is foreground ends this session.
                self.wantsMonitoring = false
                self.suspend()
                self.endActivitySession()
            }
        }
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: UIApplication.protectedDataWillBecomeUnavailableNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.visibility.protectedDataAvailable = false
                self.suspend()
            }
        })
        observers.append(center.addObserver(forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.visibility.protectedDataAvailable = true
                if self.wantsMonitoring && self.visibility.scene == .active { self.start() }
            }
        })
        observers.append(center.addObserver(forName: AVCaptureSession.wasInterruptedNotification, object: camera.session, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.handleInterruption() }
        })
        observers.append(center.addObserver(forName: AVCaptureSession.interruptionEndedNotification, object: camera.session, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.interrupted = false
                if self.wantsMonitoring && self.visibility.shouldKeepCapture { self.start() }
            }
        })
        observers.append(center.addObserver(forName: AVCaptureSession.runtimeErrorNotification, object: camera.session, queue: .main) { [weak self] notification in
            let message = (notification.userInfo?[AVCaptureSessionErrorKey] as? Error)?.localizedDescription
            Task { @MainActor in
                guard let self, self.isMonitoring else { return }
                self.suspend()
                self.status = .failed
                self.errorMessage = message
                self.publishActivity(force: true)
            }
        })
        observers.append(center.addObserver(forName: UIDevice.orientationDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene else { return }
                let angle: CGFloat
                switch scene.interfaceOrientation {
                case .landscapeLeft: angle = 180
                case .landscapeRight: angle = 0
                case .portraitUpsideDown: angle = 270
                default: angle = 90
                }
                self?.setRotation(angle)
            }
        })
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
        watchdog?.invalidate()
        motion.stopDeviceMotionUpdates()
        camera.stop()
    }

    func start() {
        #if targetEnvironment(simulator)
        status = .unavailable
        errorMessage = HeadUpCopy(language: store.language).text("Camera monitoring requires a physical iPhone or iPad. You can explore all other features in the simulator.", "相機偵測需要實體 iPhone 或 iPad。模擬器可操作其他所有功能。")
        return
        #else
        if !wantsMonitoring {
            monitoringSessionID = UUID()
            cameraStartedForSession = false
        }
        wantsMonitoring = true
        if interrupted && !camera.session.isInterrupted { interrupted = false }
        guard visibility.shouldKeepCapture, !interrupted, !isMonitoring else { return }
        errorMessage = nil
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: beginCapture()
        case .notDetermined:
            status = .starting
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                Task { @MainActor in
                    guard let self, self.wantsMonitoring else { return }
                    if granted { self.start() } else { self.status = .permissionDenied }
                }
            }
        default: status = .permissionDenied
        }
        #endif
    }

    func pause() {
        wantsMonitoring = false
        pendingCalibration = false
        suspend()
        endActivitySession()
    }

    private func endActivitySession(immediately: Bool = false) {
        monitoringSessionID = nil
        cameraStartedForSession = false
        liveActivity.end(immediately: immediately)
    }

    func setLiveActivitiesEnabled(_ enabled: Bool) {
        store.setLiveActivitiesEnabled(enabled)
        if !store.liveActivitiesEnabled { liveActivity.end(immediately: true) }
        else { publishActivity(force: true) }
    }

    func setScene(_ scene: MonitoringVisibility.Scene) {
        visibility.scene = scene
        if scene == .active {
            visibility.protectedDataAvailable = UIApplication.shared.isProtectedDataAvailable
            store.refresh()
            if wantsMonitoring { start() }
            pictureInPicture.completeRestorationIfActive()
            publishActivity(force: true)
        } else if !visibility.shouldKeepCapture { suspend() }
        UIApplication.shared.isIdleTimerDisabled = isMonitoring && scene == .active
    }

    func openPictureInPicture() {
        guard isMonitoring, status != .starting, !isCalibrating, visibility.scene == .active else { return }
        updatePictureInPicture()
        pictureInPicture.start(cameraSupported: camera.session.isMultitaskingCameraAccessEnabled,
                               copy: HeadUpCopy(language: store.language))
    }

    func switchCamera() {
        let resume = wantsMonitoring
        suspend()
        isFrontCamera.toggle()
        // A baseline is camera-specific; using a different lens requires calibration again.
        store.setCalibration(nil)
        if resume { start() }
    }

    func startCalibration() {
        pictureInPicture.stop()
        guard isMonitoring, status != .starting else { pendingCalibration = true; start(); return }
        pendingCalibration = false
        clearContinuity()
        isCalibrating = true
        calibrationRemaining = 3
        calibrationStart = nil
        calibrationLastFrameMs = nil
        calibrationSamples = []
        status = .calibrating
        publishActivity(force: true)
    }

    func setRotation(_ angle: CGFloat) {
        guard visibility.pictureInPicture == .stopped else { return }
        guard angle != rotation else { return }
        rotation = angle
        let resume = wantsMonitoring
        suspend()
        camera.setRotation(angle)
        store.setCalibration(nil)
        if resume { start() }
    }

    func resetData() {
        pause()
        liveActivity.end(immediately: true)
        store.resetAllData()
    }

    private func beginCapture() {
        token = UUID()
        let currentToken = token
        isMonitoring = true
        status = .starting
        clearContinuity()
        publishActivity(force: true)
        if motion.isDeviceMotionAvailable { motion.deviceMotionUpdateInterval = 0.1; motion.startDeviceMotionUpdates() }
        lastFrameAt = ProcessInfo.processInfo.systemUptime
        watchdog?.invalidate()
        watchdog = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.isMonitoring, self.status != .starting else { return }
                if ProcessInfo.processInfo.systemUptime - self.lastFrameAt > 2 {
                    self.clearContinuity()
                    self.status = .noPose
                    self.cancelCalibration()
                    self.publishActivity()
                }
            }
        }
        camera.start(token: currentToken, front: isFrontCamera, handler: { [weak self] token, result in
            guard let self, self.token == token, self.isMonitoring, self.visibility.canProcessFrames else { return }
            switch result {
            case .success(let frame): self.receive(frame)
            case .failure(let error):
                self.suspend(); self.status = .failed; self.errorMessage = error.localizedDescription
                self.publishActivity(force: true)
            }
        }, completion: { [weak self] token, error in
            guard let self, self.token == token, self.isMonitoring else { return }
            if let error {
                self.suspend()
                self.status = (error as? CameraPipeline.CameraError) == .noCamera ? .unavailable : .failed
                self.errorMessage = error.localizedDescription
                self.publishActivity(force: true)
            } else {
                self.cameraStartedForSession = true
                self.status = .noPose
                UIApplication.shared.isIdleTimerDisabled = self.visibility.scene == .active
                if self.pendingCalibration { self.startCalibration() }
                self.publishActivity(force: true)
            }
        })
    }

    private func receive(_ frame: PoseFrame) {
        defer { publishActivity() }
        if ProcessInfo.processInfo.arguments.contains("--pip-device-test"),
           visibility.scene == .background, visibility.pictureInPicture == .active {
            backgroundFrameCount += 1
        }
        lastFrameAt = ProcessInfo.processInfo.systemUptime
        frameSize = frame.size
        if let previousValidMs, frame.monotonicMs - previousValidMs > 2_000 { clearContinuity(); cancelCalibration() }
        if isCalibrating, let last = calibrationLastFrameMs, frame.monotonicMs - last > 2_000 {
            cancelCalibration()
            clearContinuity()
        }
        let gravity = motion.deviceMotion?.gravity
        let tilt = gravity.map { Int(acos(min(1, max(-1, abs($0.z)))) * 180 / .pi) } ?? 0
        guard let value = analyzer.analyze(landmarks: frame.points, deviceTiltDegrees: tilt,
                                           isDeviceFlat: gravity.map { abs($0.z) > 0.9 } ?? false,
                                           calibration: store.calibration, timestampMs: frame.wallClockMs) else {
            clearContinuity()
            status = isCalibrating ? .calibrating : .noPose
            if isCalibrating, calibrationStart != nil { cancelCalibration(); status = .noPose }
            return
        }
        metrics = value
        landmarks = frame.points
        if isCalibrating {
            guard value.landmarkConfidence >= 0.35 else {
                if calibrationStart != nil { cancelCalibration() }
                return
            }
            if calibrationStart == nil { calibrationStart = frame.monotonicMs }
            calibrationLastFrameMs = frame.monotonicMs
            calibrationSamples.append(value)
            let elapsed = frame.monotonicMs - (calibrationStart ?? frame.monotonicMs)
            calibrationRemaining = max(0, 3 - Int(elapsed / 1000))
            if elapsed >= 3_000 {
                let n = Float(calibrationSamples.count)
                store.setCalibration(CalibrationProfile(
                    angleDegrees: calibrationSamples.reduce(0) { $0 + $1.rawAngleDegrees } / n,
                    postureRatio: calibrationSamples.reduce(0) { $0 + $1.postureRatio } / n,
                    shoulderWidth: calibrationSamples.reduce(0) { $0 + $1.shoulderWidth } / n,
                    calibratedAtMs: frame.wallClockMs))
                isCalibrating = false
                calibrationSamples = []
                clearContinuity()
                status = .monitoring
            }
            return
        }
        status = .monitoring
        let elapsed = previousValidMs.map { frame.monotonicMs - $0 } ?? 0
        previousValidMs = frame.monotonicMs
        store.recordMetrics(value, elapsedMilliseconds: elapsed)
        let output = alerts.process(metrics: value, monotonicTimeMs: frame.monotonicMs, alarmEnabled: store.alarmEnabled)
        warningActive = output.warningActive
        updatePictureInPicture()
        if let haptic = output.haptic {
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(haptic == .danger ? .warning : .success)
        }
        if output.shouldPlaySound { AudioServicesPlaySystemSound(1057) }
    }

    private func clearContinuity() {
        metrics = nil
        landmarks = []
        previousValidMs = nil
        analyzer.reset()
        alerts.reset()
        warningActive = false
        updatePictureInPicture()
        store.endMonitoring()
    }

    private func updatePictureInPicture() {
        pictureInPicture.update(metrics: metrics, points: landmarks, size: frameSize,
                                front: isFrontCamera, rotation: rotation, warning: warningActive,
                                copy: HeadUpCopy(language: store.language))
    }

    static func activityStatus(status: MonitoringStatus, metrics: PostureMetrics?, warningActive: Bool) -> PostureActivityAttributes.Status {
        switch status {
        case .idle, .paused: return .paused
        case .starting: return .starting
        case .calibrating: return .calibrating
        case .noPose: return .noPose
        case .failed, .permissionDenied, .unavailable: return .failed
        case .monitoring:
            guard let metrics else { return .noPose }
            if warningActive || metrics.isRapidFall { return .liftHead }
            return metrics.zone == .safe ? .good : .attention
        }
    }

    private func publishActivity(force: Bool = false) {
        guard let sessionID = monitoringSessionID, cameraStartedForSession, store.liveActivitiesEnabled else { return }
        let now = Date()
        if !Calendar.current.isDate(store.today.dayStart, inSameDayAs: now) { store.refresh() }
        let copy = HeadUpCopy(language: store.language)
        let snapshot = PostureActivityAttributes.ContentState(
            status: Self.activityStatus(status: status, metrics: metrics, warningActive: warningActive),
            safeSeconds: store.today.safeSeconds, totalSeconds: store.today.totalTrackedSeconds,
            dayStart: store.today.dayStart, updatedAt: now, language: copy.text("en", "zh-Hant"))
        liveActivity.offer(sessionID: sessionID, content: snapshot,
                           canStart: isMonitoring && visibility.scene == .active, force: force)
    }

    private func cancelCalibration() {
        guard isCalibrating else { return }
        isCalibrating = false
        calibrationStart = nil
        calibrationLastFrameMs = nil
        calibrationSamples = []
        calibrationRemaining = 3
        let copy = HeadUpCopy(language: store.language)
        errorMessage = copy.text("Calibration interrupted. Keep your face and both shoulders visible and try again.", "校準中斷，請讓臉部與雙肩清楚可見後重試。")
    }

    private func suspend() {
        token = UUID()
        isMonitoring = false
        pictureInPicture.stop()
        camera.stop()
        motion.stopDeviceMotionUpdates()
        watchdog?.invalidate()
        watchdog = nil
        cancelCalibration()
        clearContinuity()
        UIApplication.shared.isIdleTimerDisabled = false
        status = .paused
        publishActivity(force: true)
    }

    private func handleInterruption() {
        guard isMonitoring else { return }
        interrupted = true
        suspend()
    }
}
