import SwiftUI

extension HeadUpCopy {
    @MainActor func monitoringTitle(_ monitor: MonitoringCoordinator) -> String {
        switch monitor.status {
        case .idle: return text("Ready to look up?", "讓視力龍陪你抬頭")
        case .permissionDenied: return text("Allow camera access", "請開啟相機權限")
        case .unavailable, .failed: return text("Camera needs attention", "目前無法使用相機")
        default:
            let state = PostureActivityAttributes.ContentState(
                status: monitor.postureStatus, safeSeconds: 0, totalSeconds: 0,
                dayStart: .distantPast, updatedAt: .distantPast, language: text("en", "zh-Hant"))
            return state.statusTitle(isStale: false)
        }
    }

    @MainActor func monitoringHint(_ monitor: MonitoringCoordinator) -> String {
        switch monitor.status {
        case .idle, .paused:
            return text("Start monitoring and keep your face and both shoulders in view.", "開始偵測，讓臉部和雙肩清楚入鏡。")
        case .starting:
            return text("Preparing your camera. You can cancel below.", "正在準備鏡頭，可按下方按鈕取消。")
        case .calibrating:
            return text("Sit upright and hold still for three seconds. Calibration time is not counted.", "請坐正並保持三秒；校準期間不計入姿勢紀錄。")
        case .noPose:
            return text("Move into good light and show your face and shoulders. No time is counted yet.", "請移至光線充足處，讓臉部與雙肩入鏡；目前不累計時間。")
        case .permissionDenied:
            return text("Enable Camera in Settings, then return here. Images stay on this device.", "到系統設定允許使用相機後返回。影像只在裝置上處理。")
        case .unavailable, .failed:
            return text("Close other apps using the camera, then try again. No time is counted while stopped.", "請關閉其他正在使用相機的 App 後重試；停止期間不計時。")
        case .monitoring:
            if monitor.metrics?.isTooClose == true {
                return text("Move the screen a little farther away and sit upright.", "請將螢幕稍微移遠，並保持坐正。")
            }
            if monitor.metrics?.isDeviceFlat == true {
                return text("Raise your phone toward eye level so you can look ahead.", "請抬高手機，讓視線能自然向前。")
            }
            return monitor.postureStatus == .good
                ? text("Keep this comfortable position. Only detected posture time counts.", "保持現在的姿勢，僅累計有效偵測時間。")
                : text("Raise your phone, relax your shoulders and gently lift your head.", "請抬高手機、放鬆肩膀，再慢慢抬頭。")
        }
    }
}

struct MonitoringAction: View {
    @ObservedObject var monitor: MonitoringCoordinator
    let copy: HeadUpCopy
    var fillsWidth = false
    @Environment(\.openURL) private var openURL

    private var running: Bool { monitor.isMonitoring || monitor.status == .starting }
    private var title: String {
        if monitor.status == .permissionDenied { return copy.text("Open Settings", "開啟系統設定") }
        if monitor.status == .starting { return copy.text("Cancel starting", "取消啟動") }
        if running { return copy.text("Pause monitoring", "暫停偵測") }
        if monitor.status == .failed || monitor.status == .unavailable { return copy.text("Try again", "重新嘗試") }
        return copy.text("Start monitoring", "開始偵測")
    }

    var body: some View {
        Button {
            if monitor.status == .permissionDenied {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            } else if running { monitor.pause() }
            else { monitor.start() }
        } label: {
            Label(title, systemImage: monitor.status == .permissionDenied ? "gearshape" : running ? "pause.fill" : "play.fill")
                .font(.headline)
                .frame(maxWidth: fillsWidth ? .infinity : nil, minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .tint(running ? HeadUpStyle.card : HeadUpStyle.cyan)
        .foregroundStyle(running ? .white : HeadUpStyle.top)
        .accessibilityIdentifier("monitoringToggle")
    }
}

struct LiveActivityAvailability: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var service: PostureActivityService
    let copy: HeadUpCopy
    @Environment(\.openURL) private var openURL

    private var message: String {
        if !store.liveActivitiesEnabled { return copy.text("Island reminders are off in Heads Up.", "Heads Up 的靈動島提醒目前關閉。") }
        if !service.systemEnabled { return copy.text("Live Activities are unavailable in system settings. Camera monitoring can continue.", "系統目前不允許即時動態，相機仍可繼續偵測。") }
        switch service.presentation {
        case .idle: return copy.text("Starts after the camera is ready. Requires a supported iPhone for Dynamic Island.", "鏡頭啟動後顯示摘要；靈動島需支援的 iPhone。")
        case .active: return copy.text("Posture updates are sent to your Live Activity. The system controls when they appear.", "姿勢狀態正傳送至即時動態，實際顯示由系統安排。")
        case .dismissed: return copy.text("This activity was closed. Pause and start a new session to show it again.", "本次即時動態已關閉；暫停後重新開始偵測可再次顯示。")
        case .notAllowed: return copy.text("Live Activities are now available. Pause and start a new session to show one.", "系統已允許即時動態，請暫停後重新開始偵測以顯示。")
        case .failed: return copy.text("Could not show the activity. Camera monitoring can continue; pause and start to retry.", "暫時無法顯示即時動態，相機仍可偵測；暫停後重新開始可重試。")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(copy.text("Dynamic Island & Lock Screen", "靈動島與鎖定畫面"), systemImage: "iphone")
                .font(.subheadline.bold())
            Text(message).font(.footnote).foregroundStyle(HeadUpStyle.secondary)
            if store.liveActivitiesEnabled && !service.systemEnabled {
                Button(copy.text("Open system settings", "開啟系統設定")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                .frame(minHeight: 44)
            }
        }
        .onAppear { service.refreshAvailability() }
    }
}

struct MonitoringGuide: View {
    let copy: HeadUpCopy

    var body: some View {
        HeadUpPage {
            Text(copy.text("A clear view, a gentle reminder", "看得清楚，才能提醒你"))
                .font(.title2.bold())
            guideStep("1", copy.text("Start the camera", "開始偵測"),
                      copy.text("Allow camera access. Keep your face and both shoulders visible in good light.", "允許相機權限，在光線充足處讓臉部與雙肩清楚入鏡。"))
            guideStep("2", copy.text("Find your baseline", "完成個人校準"),
                      copy.text("Sit upright, roughly 45 cm from the screen, then hold still for three seconds. Recalibrate after switching cameras or rotating your phone.", "坐正並與螢幕保持約 45 公分，再穩定三秒。切換鏡頭或旋轉手機後，請重新校準。"))
            guideStep("3", copy.text("Take your reminder with you", "切換 App 也能提醒"),
                      copy.text("Open the small camera window before switching apps, on a supported device. Keep the window visible. Dynamic Island displays status; it does not keep the camera running.", "在支援的裝置上，先開啟相機小視窗，再切換 App，並保持視窗可見。靈動島負責顯示狀態，本身不會讓相機持續運作。"))
            HeadUpCard {
                VStack(alignment: .leading, spacing: 16) {
                    Text(copy.text("Read your dragon", "看懂視力龍")).font(.headline)
                    dragonRow(.good, copy.text("Blue · good posture", "藍龍・目前姿勢良好"))
                    dragonRow(.attention, copy.text("Yellow symbol · adjust your posture", "黃色圖示・請注意姿勢"))
                    dragonRow(.liftHead, copy.text("Angry red · lift your head", "生氣紅龍・請抬頭調整"))
                    dragonRow(.paused, copy.text("Gray · no current reading", "灰龍・沒有目前姿勢資料"))
                }
            }
            Text(copy.text("Locking, camera interruptions and missing poses pause tracked time. No unmonitored time is counted as good posture. Images stay on this device.", "鎖屏、相機中斷或未偵測到人體時暫停計時，未監測時間不算正確姿勢。影像只在裝置上處理。"))
                .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
        }
        .navigationTitle(copy.text("Getting started", "使用指南"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func guideStep(_ number: String, _ title: String, _ detail: String) -> some View {
        HeadUpCard {
            HStack(alignment: .top, spacing: 14) {
                Text(number).font(.headline).foregroundStyle(HeadUpStyle.cyan)
                    .frame(width: 32, height: 32).background(HeadUpStyle.cyan.opacity(0.12), in: Circle())
                VStack(alignment: .leading, spacing: 8) {
                    Text(title).font(.headline)
                    Text(detail).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                }
            }
        }
    }

    private func dragonRow(_ status: PostureActivityAttributes.Status, _ title: String) -> some View {
        HStack(spacing: 12) {
            PostureDragonView(status: status, size: 40)
            Text(title).font(.subheadline).foregroundStyle(status.dragonTint)
        }
        .accessibilityElement(children: .combine)
    }
}

struct CalibrationPreparation: View {
    let copy: HeadUpCopy
    let onStart: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        HeadUpPage {
            Image(systemName: "person.crop.rectangle").font(.system(size: 56)).foregroundStyle(HeadUpStyle.cyan)
                .frame(maxWidth: .infinity).accessibilityHidden(true)
            Text(copy.text("Sit comfortably before you begin", "先坐正，再開始校準")).font(.title2.bold())
            Text(copy.text("Keep your face and both shoulders in view, about 45 cm from the screen. Tap Start when ready and hold still for three seconds.", "讓臉部與雙肩清楚入鏡，與螢幕保持約 45 公分。準備好後按開始，並維持三秒。"))
                .foregroundStyle(HeadUpStyle.secondary)
            Text(copy.text("Calibration establishes your baseline. It closes an open camera window and pauses progress while you calibrate.", "校準會建立你的個人基準，並關閉已開啟的相機小視窗；校準期間不累計進度。"))
                .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
            Button { dismiss(); onStart() } label: {
                Label(copy.text("Start 3-second calibration", "開始三秒校準"), systemImage: "scope")
                    .font(.headline).frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent).foregroundStyle(HeadUpStyle.top)
            .accessibilityIdentifier("startCalibration")
        }
        .navigationTitle(copy.text("Personal calibration", "個人校準"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
