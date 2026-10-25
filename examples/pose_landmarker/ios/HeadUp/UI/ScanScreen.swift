import SwiftUI
import HeadUpCore

struct ScanScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.dynamicTypeSize) private var typeSize
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Posture scan", "姿勢掃描"), symbol: "viewfinder")
            if sizeClass == .regular && !typeSize.isAccessibilitySize {
                HStack(alignment: .top, spacing: 20) {
                    cameraCard.frame(maxWidth: .infinity)
                    results.frame(maxWidth: .infinity)
                }
            } else {
                cameraCard
                results
            }
            Label(copy.text("Monitoring pauses when you leave HeadUp.", "離開 HeadUp 時會暫停姿勢偵測。"), systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(HeadUpStyle.secondary)
        }
        .accessibilityIdentifier("scanScreen")
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
                .disabled(monitor.isCalibrating)
                .accessibilityLabel(copy.text("Switch camera", "切換鏡頭"))
                .accessibilityIdentifier("switchCamera")
            }
            .foregroundStyle(.white)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(12)
        }
        .frame(height: sizeClass == .regular ? 430 : 360)
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
            VStack(alignment: .leading, spacing: 18) {
                ViewThatFits(in: .horizontal) {
                    HStack { Text(copy.text("Your posture", "姿勢掃描結果")).font(.title3.bold()); Spacer(); calibrationButton }
                    VStack(alignment: .leading, spacing: 10) { Text(copy.text("Your posture", "姿勢掃描結果")).font(.title3.bold()); calibrationButton }
                }
                HStack(alignment: .firstTextBaseline, spacing: 14) {
                    Text(monitor.metrics.map { "\($0.angleDegrees)°" } ?? "—")
                        .font(.system(size: 60, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(HeadUpStyle.color(for: monitor.metrics?.zone))
                    Text(copy.zone(monitor.metrics?.zone))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(HeadUpStyle.color(for: monitor.metrics?.zone))
                }
                if let metrics = monitor.metrics {
                    ProgressView(value: Double(metrics.landmarkConfidence), total: 1)
                        .tint(HeadUpStyle.cyan)
                        .accessibilityLabel(copy.text("Detection confidence", "偵測信心"))
                    Text(copy.text("Detection confidence \(Int(metrics.landmarkConfidence * 100))%", "偵測信心 \(Int(metrics.landmarkConfidence * 100))%"))
                        .font(.caption).foregroundStyle(HeadUpStyle.secondary)
                }
                Divider().overlay(.white.opacity(0.08))
                metricRow(copy.text("Head forward tilt", "頭部前傾"), symbol: "person.crop.circle", value: monitor.metrics.map { "\($0.relativeAngleDegrees)°" } ?? "—")
                metricRow(copy.text("Neck flexion", "頸部彎曲"), symbol: "figure.seated.side", value: monitor.metrics.map { "\($0.neckFlexionDegrees)°" } ?? "—")
                metricRow(copy.text("Shoulder balance", "肩膀平衡"), symbol: "arrow.left.and.right", value: monitor.metrics.map { "\($0.shoulderBalanceDegrees)°" } ?? "—")
                metricRow(copy.text("Estimated screen distance", "估算螢幕距離"), symbol: "arrow.up.left.and.arrow.down.right", value: monitor.metrics?.screenDistanceCm.map { copy.text("\($0) cm", "\($0) 公分") } ?? "—")
                if store.calibration == nil {
                    Text(copy.text("Calibrate while sitting upright, about 45 cm from the screen, to personalize your posture and distance estimates.", "請坐正並與螢幕保持約 45 公分，再完成校準，以建立個人姿勢基準與距離估算。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                }
            }
        }
    }

    private var calibrationButton: some View {
        Button { monitor.startCalibration() } label: {
            Label(copy.text("Calibrate", "3 秒校準"), systemImage: "scope")
                .font(.subheadline.bold())
        }
        .buttonStyle(.bordered)
        .disabled(monitor.isCalibrating)
        .accessibilityIdentifier("calibrateButton")
    }

    private func metricRow(_ title: String, symbol: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Image(systemName: symbol).foregroundStyle(HeadUpStyle.cyan).frame(width: 22)
            Text(title).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
            Spacer(minLength: 8)
            Text(value).font(.headline).monospacedDigit().fixedSize()
        }
        .accessibilityElement(children: .combine)
    }
}
