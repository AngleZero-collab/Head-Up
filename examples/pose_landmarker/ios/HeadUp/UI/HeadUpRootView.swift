import SwiftUI
import HeadUpCore

struct HeadUpRootView: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    @State private var selection = 0
    private enum Sheet: String, Identifiable {
        case settings, guide, calibration
        var id: String { rawValue }
    }
    @State private var sheet: Sheet?
    @State private var calibrationAfterSettings = false

    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if selection != 0 {
                    MonitoringBanner(monitor: monitor, copy: copy, onScan: { selection = 0 })
                        .padding(.horizontal, 20)
                        .padding(.bottom, 10)
                        .frame(maxWidth: 1090)
                }

                TabView(selection: $selection) {
                    ScanScreen(store: store, monitor: monitor, onCalibrate: { sheet = .calibration }, onGuide: { sheet = .guide })
                        .tabItem { Label(copy.text("Scan", "掃描"), systemImage: "viewfinder") }
                        .tag(0)
                    StatsScreen(store: store, monitor: monitor, onStart: { selection = 0 }, onChallenges: { selection = 2 })
                        .tabItem { Label(copy.text("Stats", "數據"), systemImage: "chart.bar.xaxis") }
                        .tag(1)
                    ChallengeScreen(store: store, monitor: monitor, onShop: { selection = 3 })
                        .tabItem { Label(copy.text("Challenge", "挑戰"), systemImage: "sparkles") }
                        .tag(2)
                    ShopScreen(store: store, onChallenges: { selection = 2 })
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
                        Text("Heads Up").fontWeight(.bold)
                    }
                    .font(.title2)
                    .accessibilityElement(children: .combine)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { sheet = .settings } label: {
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
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            store.refresh()
        }
        .onOpenURL { url in
            guard url.scheme == "headup", ["today", "scan"].contains(url.host) else { return }
            sheet = nil
            selection = url.host == "today" ? 1 : 0
        }
        .onReceive(monitor.pictureInPicture.$restoreRequest) { request in
            guard request > 0 else { return }
            selection = 0
            sheet = nil
        }
        .overlay {
            if monitor.warningActive {
                RoundedRectangle(cornerRadius: 26)
                    .strokeBorder(HeadUpStyle.danger, lineWidth: 7)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .sheet(item: $sheet, onDismiss: {
            if calibrationAfterSettings {
                calibrationAfterSettings = false
                sheet = .calibration
            }
        }) { destination in
            if destination == .settings {
                HeadUpSettingsView(store: store, monitor: monitor) {
                    selection = 0
                    calibrationAfterSettings = true
                }
            } else {
                NavigationStack {
                    Group {
                        if destination == .guide { MonitoringGuide(copy: copy) }
                        else { CalibrationPreparation(copy: copy, onStart: monitor.startCalibration) }
                    }
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button(copy.text("Done", "完成")) { sheet = nil }.frame(minHeight: 44)
                        }
                    }
                }
                .tint(HeadUpStyle.cyan)
                .preferredColorScheme(.dark)
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
    let onScan: () -> Void
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 8) { status; control }
            } else {
                HStack(spacing: 12) { status; Spacer(minLength: 0); control }
            }
        }
        .padding(12)
        .background(HeadUpStyle.card.opacity(0.65), in: RoundedRectangle(cornerRadius: 16))
    }

    private var status: some View {
        Button(action: onScan) {
            HStack(spacing: 10) {
                PostureDragonView(status: monitor.postureStatus, size: 40)
                VStack(alignment: .leading, spacing: 3) {
                    Text(copy.monitoringTitle(monitor)).font(.subheadline.weight(.medium))
                        .foregroundStyle(monitor.postureStatus.dragonTint)
                    Text(copy.text("Open Scan for details", "前往掃描查看詳情"))
                        .font(.caption).foregroundStyle(HeadUpStyle.secondary)
                }
            }
            .frame(minHeight: 44)
            .fixedSize(horizontal: false, vertical: true)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("returnToScan")
    }

    private var control: some View {
        Button {
            if monitor.isMonitoring || monitor.status == .starting { monitor.pause() }
            else { onScan() }
        } label: {
            Label(copy.text(monitor.isMonitoring || monitor.status == .starting ? "Pause" : "Scan",
                            monitor.isMonitoring || monitor.status == .starting ? "暫停" : "掃描"),
                  systemImage: monitor.isMonitoring || monitor.status == .starting ? "pause.fill" : "viewfinder")
                .font(.subheadline.bold()).frame(minHeight: 44)
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
                    Toggle(copy.text("Show posture skeleton", "顯示姿勢骨架"),
                           isOn: Binding(get: { store.skeletonEnabled }, set: monitor.setSkeletonEnabled))
                        .accessibilityIdentifier("skeletonSetting")
                    Toggle(copy.text("Dynamic Island and Lock Screen", "靈動島與鎖定畫面"),
                           isOn: Binding(get: { store.liveActivitiesEnabled }, set: monitor.setLiveActivitiesEnabled))
                        .accessibilityIdentifier("liveActivitiesToggle")
                }
                Section {
                    LiveActivityAvailability(store: store, service: monitor.liveActivity, copy: copy)
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
                    NavigationLink {
                        MonitoringGuide(copy: copy)
                    } label: {
                        Label(copy.text("Getting started & dragon states", "使用指南與視力龍狀態"), systemImage: "questionmark.circle")
                    }
                    Label(copy.text("Monitoring with Heads Up or its camera window", "透過 Heads Up 或相機小視窗偵測"), systemImage: "camera")
                    Text(copy.text("To monitor while using other apps, first open the camera window. Dynamic Island shows posture updates but does not keep the camera running. Locking or camera interruptions pause monitoring and tracked time.", "使用其他 App 時，請先開啟相機小視窗。靈動島顯示姿勢狀態，本身不會讓相機持續運作。鎖屏或相機中斷時會暫停偵測與時間紀錄。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                    Text(copy.text("Your camera images are processed on this device. Heads Up stores posture measurements locally, not photos or video.", "相機影像只在裝置上處理。Heads Up 僅在本機儲存姿勢數據，不儲存照片或影片。"))
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
                    monitor.resetData()
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
