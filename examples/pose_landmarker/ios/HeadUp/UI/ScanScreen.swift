import SwiftUI
import HeadUpCore

struct ScanScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    var onCalibrate: () -> Void = {}
    var onGuide: () -> Void = {}
    @State private var showsDetails = false
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.dynamicTypeSize) private var typeSize
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        HeadUpPage {
            HStack {
                HeadUpSectionTitle(title: copy.text("Posture companion", "姿勢守護"), symbol: "viewfinder")
                Spacer(minLength: 4)
                Button(action: onGuide) {
                    Image(systemName: "questionmark.circle").font(.title3).frame(width: 44, height: 44)
                }
                .accessibilityLabel(copy.text("Getting started", "使用指南"))
                .accessibilityIdentifier("monitoringGuide")
            }
            statusCard
            if sizeClass == .regular && !typeSize.isAccessibilitySize {
                HStack(alignment: .top, spacing: 20) {
                    cameraCard.frame(maxWidth: .infinity)
                    VStack(spacing: 20) { setupCard; floatingControls; results }
                        .frame(maxWidth: .infinity)
                }
            } else {
                setupCard
                cameraCard
                floatingControls
                results
            }
        }
        .accessibilityIdentifier("scanScreen")
    }

    private var statusCard: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 16) {
                    if !typeSize.isAccessibilitySize {
                        PostureDragonView(status: monitor.postureStatus, size: 76)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Label(copy.monitoringTitle(monitor), systemImage: monitor.postureStatus.symbol)
                            .font(.title2.bold()).foregroundStyle(monitor.postureStatus.dragonTint)
                        Text(copy.monitoringHint(monitor))
                            .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    }
                }
                MonitoringAction(monitor: monitor, copy: copy, fillsWidth: true)
                Label(copy.text("Tracked today · \(copy.duration(store.today.totalTrackedSeconds))",
                                "今日有效監測・\(copy.duration(store.today.totalTrackedSeconds))"), systemImage: "clock")
                    .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                if let error = monitor.errorMessage, !error.isEmpty {
                    Text(error).font(.footnote).foregroundStyle(HeadUpStyle.warning).textSelection(.enabled)
                }
            }
        }
    }

    private var setupCard: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 12) {
                Label(copy.text(store.calibration == nil ? "Personalize your detection" : "Your baseline is ready",
                                store.calibration == nil ? "建立你的姿勢基準" : "個人姿勢基準已就緒"),
                      systemImage: store.calibration == nil ? "scope" : "checkmark.seal.fill")
                    .font(.headline).foregroundStyle(store.calibration == nil ? HeadUpStyle.cyan : HeadUpStyle.safe)
                if monitor.isCalibrating {
                    ProgressView().accessibilityLabel(copy.text("Calibrating", "校準中"))
                    Text(copy.text("Hold still · \(monitor.calibrationRemaining)", "請保持穩定・\(monitor.calibrationRemaining)"))
                        .font(.headline).monospacedDigit()
                } else if store.calibration == nil {
                    Text(copy.text("Sit upright with your face and shoulders visible. A three-second calibration helps tailor the readings to you.", "先坐正並讓臉部與雙肩入鏡，再用三秒校準建立個人基準。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                } else {
                    Text(copy.text("Recalibrate if your camera or phone orientation changes.", "切換鏡頭或旋轉手機後，請重新校準。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                }
                calibrationButton
            }
        }
    }

    private var floatingControls: some View {
        FloatingWindowControls(store: store, monitor: monitor, window: monitor.pictureInPicture, copy: copy)
    }

    private var cameraCard: some View {
        ZStack(alignment: .topTrailing) {
            CameraPreview(monitor: monitor)
                .background(.black)
                .accessibilityLabel(copy.text("Live posture camera", "即時姿勢鏡頭"))
                .accessibilityIdentifier("cameraPreview")

            if !monitor.isMonitoring {
                VStack(spacing: 14) {
                    Image(systemName: "person.crop.rectangle.badge.plus")
                        .font(.system(size: 44, weight: .light))
                        .foregroundStyle(HeadUpStyle.cyan)
                    Text(copy.text("A little lift makes a difference.", "抬起頭，照顧自己。"))
                        .font(.headline)
                    Text(copy.text("Start a session to see your posture.", "開始姿勢守護，查看即時狀態。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                }
                .multilineTextAlignment(.center)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(HeadUpStyle.top.opacity(0.85))
            }

            if monitor.isCalibrating {
                VStack(spacing: 10) {
                    Text("\(monitor.calibrationRemaining)")
                        .font(.system(size: 70, weight: .bold, design: .rounded))
                        .contentTransition(.numericText())
                    Text(copy.text("Sit upright and hold still", "請坐正並保持穩定"))
                        .font(.headline)
                }
                .padding(20)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(.black.opacity(0.5))
                .accessibilityElement(children: .combine)
            }

            HStack(spacing: 8) {
                Button { monitor.setSkeletonEnabled(!store.skeletonEnabled) } label: {
                    Image(systemName: store.skeletonEnabled ? "figure.stand" : "person.fill")
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(copy.text(store.skeletonEnabled ? "Hide posture skeleton" : "Show posture skeleton",
                                               store.skeletonEnabled ? "隱藏姿勢骨架" : "顯示姿勢骨架"))
                .accessibilityValue(copy.text(store.skeletonEnabled ? "On" : "Off", store.skeletonEnabled ? "已開啟" : "已關閉"))
                .accessibilityIdentifier("skeletonToggle")
                Button { store.setAlarmEnabled(!store.alarmEnabled) } label: {
                    Image(systemName: store.alarmEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(copy.text(store.alarmEnabled ? "Turn warning sound off" : "Turn warning sound on", store.alarmEnabled ? "關閉提醒音效" : "開啟提醒音效"))
                .accessibilityIdentifier("alarmToggle")
                Button { monitor.switchCamera() } label: {
                    Image(systemName: "camera.rotate")
                        .frame(width: 44, height: 44)
                }
                .disabled(monitor.isCalibrating || monitor.isFloatingWindowOpen)
                .accessibilityLabel(copy.text("Switch camera", "切換鏡頭"))
                .accessibilityIdentifier("switchCamera")
            }
            .foregroundStyle(.white)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(12)
        }
        .frame(height: typeSize.isAccessibilitySize ? 430 : sizeClass == .regular ? 430 : 300)
        .clipShape(RoundedRectangle(cornerRadius: 26))
        .overlay(RoundedRectangle(cornerRadius: 26).strokeBorder(HeadUpStyle.cyan.opacity(0.35), lineWidth: 1))
        .overlay(alignment: .bottomLeading) {
            Label(copy.text(monitor.isFrontCamera ? "Front camera" : "Back camera", monitor.isFrontCamera ? "前置鏡頭" : "後置鏡頭"), systemImage: "camera")
                .font(.caption.weight(.medium))
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(.ultraThinMaterial, in: Capsule())
                .padding(12)
                .accessibilityHidden(true)
        }
    }

    private var results: some View {
        HeadUpCard {
            DisclosureGroup(isExpanded: $showsDetails) {
                VStack(alignment: .leading, spacing: 14) {
                    Text(copy.text("These estimates explain the posture reading. Follow the dragon's guidance above.", "以下估算數據用來說明判定結果；請以上方視力龍的提醒調整姿勢。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                    metricRow(copy.text("Posture angle", "姿勢角度"), symbol: "angle",
                              value: liveMetrics.map { "\($0.angleDegrees)°" } ?? "—")
                    metricRow(copy.text("Head forward tilt", "頭部前傾"), symbol: "person.crop.circle",
                              value: liveMetrics.map { "\($0.relativeAngleDegrees)°" } ?? "—")
                    metricRow(copy.text("Neck flexion", "頸部彎曲"), symbol: "figure.seated.side",
                              value: liveMetrics.map { "\($0.neckFlexionDegrees)°" } ?? "—")
                    metricRow(copy.text("Shoulder balance", "肩膀平衡"), symbol: "arrow.left.and.right",
                              value: liveMetrics.map { "\($0.shoulderBalanceDegrees)°" } ?? "—")
                    metricRow(copy.text("Estimated screen distance", "估算螢幕距離"), symbol: "arrow.up.left.and.arrow.down.right",
                              value: liveMetrics?.screenDistanceCm.map { copy.text("\($0) cm", "\($0) 公分") } ?? "—")
                    if let metrics = liveMetrics {
                        ProgressView(value: Double(metrics.landmarkConfidence), total: 1)
                            .tint(HeadUpStyle.cyan)
                            .accessibilityLabel(copy.text("Detection confidence", "偵測信心"))
                        Text(copy.text("Detection confidence \(Int(metrics.landmarkConfidence * 100))%", "偵測信心 \(Int(metrics.landmarkConfidence * 100))%"))
                            .font(.caption).foregroundStyle(HeadUpStyle.secondary)
                    }
                }
                .padding(.top, 16)
            } label: {
                Label(copy.text("Posture details", "姿勢詳細數據"), systemImage: "slider.horizontal.3")
                    .font(.headline).frame(minHeight: 44)
            }
            .accessibilityIdentifier("postureDetails")
        }
    }

    private var liveMetrics: PostureMetrics? { monitor.postureStatus.hasLivePose ? monitor.metrics : nil }

    private var calibrationButton: some View {
        Button(action: onCalibrate) {
            Label(copy.text(store.calibration == nil ? "Prepare calibration" : "Recalibrate",
                            store.calibration == nil ? "準備三秒校準" : "重新校準"), systemImage: "scope")
                .font(.subheadline.bold()).frame(minHeight: 44)
        }
        .buttonStyle(.bordered)
        .disabled(monitor.isCalibrating)
        .accessibilityIdentifier("calibrateButton")
    }

    private func metricRow(_ title: String, symbol: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol).foregroundStyle(HeadUpStyle.cyan).frame(width: 22)
            Text(title).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
            Spacer(minLength: 8)
            Text(value).font(.headline).monospacedDigit().fixedSize()
        }
        .accessibilityElement(children: .combine)
    }
}

private struct FloatingWindowControls: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    @ObservedObject var window: PosturePictureInPicture
    let copy: HeadUpCopy

    var body: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 12) {
                Label(copy.text("Monitor while using other apps", "使用其他 App 時繼續偵測"), systemImage: "pip")
                    .font(.headline)
                Text(copy.text("Start monitoring, then open the small camera window before switching apps. Keep your face and shoulders visible. Closing or hiding the window may pause detection.", "開始偵測後，先開啟相機小視窗，再切換至其他 App。請讓臉部與雙肩入鏡；關閉或隱藏視窗可能暫停偵測。"))
                    .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                if !monitor.isMonitoring {
                    Label(copy.text("First, tap Start monitoring above.", "請先按上方「開始偵測」。"), systemImage: "arrow.up.circle")
                        .font(.subheadline).foregroundStyle(HeadUpStyle.warning)
                } else if monitor.isCalibrating {
                    Text(copy.text("Finish calibration before opening the window.", "請先完成校準，再開啟小視窗。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.warning)
                }
                Button {
                    if window.state == .stopped { monitor.openPictureInPicture() }
                    else { window.stop() }
                } label: {
                    Label(copy.text(window.state == .stopped ? "Open camera window" : window.state == .starting ? "Cancel opening" : "Close camera window",
                                    window.state == .stopped ? "開啟相機小視窗" : window.state == .starting ? "取消開啟" : "關閉相機小視窗"),
                          systemImage: window.state == .stopped ? "pip.enter" : "pip.exit")
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                .disabled(window.state == .stopped && (!monitor.isMonitoring || monitor.isCalibrating || monitor.status == .starting))
                .accessibilityIdentifier("pictureInPictureToggle")
                if window.state == .starting {
                    Text(copy.text("Opening camera window…", "正在開啟相機小視窗…")).font(.footnote)
                } else if window.state == .active {
                    Text(copy.text("Small window active. You can switch apps now.", "小視窗已開啟，現在可切換至其他 App。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.cyan)
                }
                if let message = window.errorMessage {
                    Text(message).font(.footnote).foregroundStyle(HeadUpStyle.danger)
                }
                Divider().overlay(.white.opacity(0.08))
                Toggle(copy.text("Island reminders", "靈動島提醒"),
                       isOn: Binding(get: { store.liveActivitiesEnabled }, set: monitor.setLiveActivitiesEnabled))
                    .accessibilityIdentifier("scanLiveActivitiesToggle")
                LiveActivityAvailability(store: store, service: monitor.liveActivity, copy: copy)
                #if targetEnvironment(simulator)
                Text(copy.text("A physical device is required to test camera monitoring in the small window.", "小視窗相機偵測需以實體裝置測試。"))
                    .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                    .accessibilityIdentifier("pictureInPictureDeviceHint")
                #endif
                #if HEADUP_PIP_PREVIEW
                if ProcessInfo.processInfo.arguments.contains("--pip-device-test") {
                    Text("\(monitor.backgroundFrameCount)")
                        .accessibilityIdentifier("backgroundFrameCount")
                }
                #endif
            }
        }
    }
}
