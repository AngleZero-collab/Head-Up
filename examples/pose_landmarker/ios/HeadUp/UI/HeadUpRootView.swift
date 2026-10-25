import SwiftUI
import HeadUpCore

struct HeadUpRootView: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    @State private var selection = 0
    @State private var showsSettings = false

    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                MonitoringBanner(monitor: monitor, copy: copy)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 10)
                    .frame(maxWidth: 1090)

                TabView(selection: $selection) {
                    ScanScreen(store: store, monitor: monitor)
                        .tabItem { Label(copy.text("Scan", "掃描"), systemImage: "viewfinder") }
                        .tag(0)
                    StatsScreen(store: store, monitor: monitor)
                        .tabItem { Label(copy.text("Stats", "數據"), systemImage: "chart.bar.xaxis") }
                        .tag(1)
                    ChallengeScreen(store: store, monitor: monitor)
                        .tabItem { Label(copy.text("Challenge", "挑戰"), systemImage: "sparkles") }
                        .tag(2)
                    ShopScreen(store: store)
                        .tabItem { Label(copy.text("Shop", "商店"), systemImage: "bag") }
                        .tag(3)
                }
                .toolbarBackground(HeadUpStyle.bottom, for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)
            }
            .background {
                LinearGradient(colors: [HeadUpStyle.top, HeadUpStyle.bottom], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .ignoresSafeArea()
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.up.right.circle.fill")
                            .foregroundStyle(HeadUpStyle.cyan)
                        Text("HeadUp").fontWeight(.bold)
                    }
                    .font(.title2)
                    .accessibilityElement(children: .combine)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showsSettings = true } label: {
                        Image(systemName: "gearshape")
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(copy.text("Settings", "設定"))
                    .accessibilityIdentifier("settingsButton")
                }
            }
            .toolbarBackground(HeadUpStyle.top, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .navigationBarTitleDisplayMode(.inline)
        }
        .tint(HeadUpStyle.cyan)
        .preferredColorScheme(.dark)
        .environment(\.locale, copy.locale)
        .overlay {
            if monitor.warningActive {
                RoundedRectangle(cornerRadius: 26)
                    .strokeBorder(HeadUpStyle.danger, lineWidth: 7)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .sheet(isPresented: $showsSettings) {
            HeadUpSettingsView(store: store, monitor: monitor) {
                selection = 0
                monitor.startCalibration()
            }
        }
        .alert(copy.text("Could not save changes", "無法儲存變更"), isPresented: Binding(
            get: { store.errorMessage != nil },
            set: { if !$0 { store.errorMessage = nil } }
        )) {
            Button(copy.text("OK", "確定"), role: .cancel) { store.errorMessage = nil }
        } message: {
            Text(store.errorMessage ?? "")
        }
    }
}

private struct MonitoringBanner: View {
    @ObservedObject var monitor: MonitoringCoordinator
    let copy: HeadUpCopy
    @Environment(\.openURL) private var openURL

    private var statusText: String {
        switch monitor.status {
        case .idle: return copy.text("Ready when you are", "準備開始姿勢守護")
        case .starting: return copy.text("Starting camera…", "正在啟動鏡頭…")
        case .monitoring: return copy.zone(monitor.metrics?.zone)
        case .noPose: return copy.text("Keep your face and shoulders in view", "請讓臉部與雙肩清楚入鏡")
        case .calibrating: return copy.text("Sit upright · \(monitor.calibrationRemaining)", "請坐正 · \(monitor.calibrationRemaining)")
        case .paused: return copy.text("Monitoring paused", "姿勢偵測已暫停")
        case .permissionDenied: return copy.text("Camera permission is needed", "請允許使用相機")
        case .unavailable: return copy.text("Camera is unavailable", "目前無法使用相機")
        case .failed: return copy.text("Could not start monitoring", "無法啟動姿勢偵測")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { status; Spacer(minLength: 8); control }
                VStack(alignment: .leading, spacing: 12) { status; control }
            }
            if case .permissionDenied = monitor.status {
                Button(copy.text("Open Settings", "開啟設定")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                .font(.subheadline.bold())
            }
            if let error = monitor.errorMessage, !error.isEmpty {
                Text(error).font(.caption).foregroundStyle(HeadUpStyle.secondary).textSelection(.enabled)
            }
        }
        .padding(12)
        .background(HeadUpStyle.card.opacity(0.65), in: RoundedRectangle(cornerRadius: 16))
    }

    private var status: some View {
        Label(statusText, systemImage: monitor.isMonitoring ? "camera.fill" : "pause.circle")
            .font(.subheadline.weight(.medium))
            .foregroundStyle(monitor.warningActive ? HeadUpStyle.danger : HeadUpStyle.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var control: some View {
        Button {
            if monitor.isMonitoring { monitor.pause() } else { monitor.start() }
        } label: {
            Label(copy.text(monitor.isMonitoring ? "Pause" : "Start", monitor.isMonitoring ? "暫停" : "開始"),
                  systemImage: monitor.isMonitoring ? "pause.fill" : "play.fill")
                .font(.subheadline.bold())
                .padding(.vertical, 4)
        }
        .buttonStyle(.bordered)
        .accessibilityIdentifier("monitoringToggle")
    }
}

struct HeadUpSettingsView: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    var calibrate: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirmsReset = false
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        NavigationStack {
            Form {
                Section(copy.text("Preferences", "偏好設定")) {
                    Picker(copy.text("Language", "應用程式語言"), selection: Binding(get: { store.language }, set: store.setLanguage)) {
                        Text(copy.text("System", "依照系統")).tag(AppLanguage.system)
                        Text("繁體中文").tag(AppLanguage.traditionalChinese)
                        Text("English").tag(AppLanguage.english)
                    }
                    .accessibilityIdentifier("languagePicker")
                    Toggle(copy.text("Warning sound", "姿勢提醒音效"), isOn: Binding(get: { store.alarmEnabled }, set: store.setAlarmEnabled))
                }
                Section {
                    Button(copy.text("3-second posture calibration", "3 秒個人姿勢校準")) {
                        dismiss()
                        calibrate()
                    }
                    .disabled(monitor.isCalibrating)
                    if let profile = store.calibration {
                        LabeledContent(copy.text("Last calibrated", "上次校準")) {
                            Text(Date(timeIntervalSince1970: Double(profile.calibratedAtMs) / 1000), format: .dateTime.month().day().hour().minute())
                        }
                    }
                } header: {
                    Text(copy.text("Calibration", "個人校準"))
                } footer: {
                    Text(copy.text("Sit upright with your face and both shoulders visible, about 45 cm from the screen. Distance readings are estimates.", "請坐正，讓臉部與雙肩清楚入鏡，並與螢幕保持約 45 公分。距離數值為估算結果。"))
                }
                Section {
                    Label(copy.text("Monitoring runs while HeadUp is visible", "HeadUp 顯示時才會偵測姿勢"), systemImage: "camera")
                    Text(copy.text("Changing tabs keeps monitoring active. Switching to another app or locking the screen pauses monitoring and tracked time.", "切換 HeadUp 內的分頁會持續偵測。切到其他 App 或鎖定螢幕時，偵測與時間紀錄都會暫停。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                    Text(copy.text("Your camera images are processed on this device. HeadUp stores posture measurements locally, not photos or video.", "相機影像只在裝置上處理。HeadUp 僅在本機儲存姿勢數據，不儲存照片或影片。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                } header: {
                    Text(copy.text("About monitoring", "關於姿勢守護"))
                }
                Section {
                    Button(copy.text("Reset all local data", "重設所有本機資料"), role: .destructive) { confirmsReset = true }
                } footer: {
                    Text(copy.text("Deletes posture history, calibration, task progress, points and owned rewards.", "刪除姿勢紀錄、校準、任務進度、積分與已兌換獎勵。"))
                }
            }
            .scrollContentBackground(.hidden)
            .background(HeadUpStyle.top)
            .navigationTitle(copy.text("Settings", "設定"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(copy.text("Done", "完成")) { dismiss() }
                }
            }
            .alert(copy.text("Reset all local data?", "重設所有本機資料？"), isPresented: $confirmsReset) {
                Button(copy.text("Reset", "重設"), role: .destructive) {
                    monitor.pause()
                    store.resetAllData()
                }
                Button(copy.text("Cancel", "取消"), role: .cancel) { }
            } message: {
                Text(copy.text("This cannot be undone. All local history, calibration, progress and rewards will be removed.", "此操作無法復原。本機所有紀錄、校準、任務進度及獎勵都將被刪除。"))
            }
        }
        .tint(HeadUpStyle.cyan)
        .preferredColorScheme(.dark)
        .environment(\.locale, copy.locale)
    }
}
