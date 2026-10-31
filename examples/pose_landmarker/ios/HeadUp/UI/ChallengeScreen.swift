import SwiftUI
import HeadUpCore

struct ChallengeScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    @State private var confirmsEyeRest = false
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Small habits, brighter days", "每天一點，照顧自己"), symbol: "sparkles")
            HeadUpCard {
                VStack(spacing: 18) {
                    HStack {
                        Text(copy.text("My Vision Dragon", "我的視力龍")).font(.title3.bold())
                        Spacer()
                        Text("Lv. \(store.dragonLevel)")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(HeadUpStyle.purple.opacity(0.2), in: Capsule())
                    }
                    DragonCompanionView(status: monitor.postureStatus, copy: copy)
                        .frame(width: 220, height: 220)
                        .background(monitor.postureStatus.dragonTint.opacity(0.08), in: RoundedRectangle(cornerRadius: 32))
                        .overlay(RoundedRectangle(cornerRadius: 32).strokeBorder(monitor.postureStatus.dragonTint.opacity(0.45), lineWidth: 2))
                        .shadow(color: HeadUpStyle.cyan.opacity(0.15), radius: 25)
                    Text(copy.text(monitor.postureStatus == .liftHead ? "Angry Dragon" : "Little Blue", monitor.postureStatus == .liftHead ? "生氣視力龍" : "藍色視力龍")).font(.title.bold())
                    ProgressView(value: Double(store.dragonEnergy), total: 100)
                        .tint(HeadUpStyle.cyan)
                        .accessibilityLabel(copy.text("Dragon energy", "視力龍能量"))
                    Text(copy.text("Energy \(store.dragonEnergy)%", "能量 \(store.dragonEnergy)%"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    Text(dragonMood)
                        .font(.headline)
                        .foregroundStyle(monitor.postureStatus.dragonTint)
                }
                .frame(maxWidth: .infinity)
            }
            HeadUpCard {
                VStack(alignment: .leading, spacing: 14) {
                    Label(copy.text("20-20-20 eye break", "20-20-20 護眼休息"), systemImage: "eye")
                        .font(.headline)
                        .foregroundStyle(HeadUpStyle.cyan)
                    Text(copy.text("Every 20 minutes, look about 20 feet away for 20 seconds.", "每使用螢幕 20 分鐘，望向約 6 公尺遠處，讓眼睛休息 20 秒。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    HStack {
                        Text(copy.text("Today: \(store.today.eyeRestCount) breaks", "今日已休息 \(store.today.eyeRestCount) 次"))
                            .font(.subheadline).monospacedDigit()
                        Spacer()
                        Button(copy.text("Record break", "紀錄休息")) { confirmsEyeRest = true }
                            .buttonStyle(.borderedProminent)
                            .foregroundStyle(HeadUpStyle.top)
                            .accessibilityIdentifier("recordEyeRest")
                    }
                }
            }
            HeadUpSectionTitle(title: copy.text("Today's challenges", "今日挑戰"), symbol: "flag.checkered")
            ForEach(store.tasks) { task in
                HeadUpTaskRow(store: store, task: task, copy: copy)
            }
        }
        .accessibilityIdentifier("challengeScreen")
        .alert(copy.text("Give your eyes a break", "讓眼睛休息一下"), isPresented: $confirmsEyeRest) {
            Button(copy.text("I've finished my break", "我已完成休息")) { store.recordEyeRest() }
            Button(copy.text("Not yet", "稍後再記錄"), role: .cancel) { }
        } message: {
            Text(copy.text("Look into the distance and relax your eyes. Confirm after you finish your break.", "請望向遠方，放鬆眼睛。完成休息後再按下確認。"))
        }
    }

    private var dragonMood: String {
        let snapshot = PostureActivityAttributes.ContentState(
            status: monitor.postureStatus, safeSeconds: 0, totalSeconds: 0,
            dayStart: .distantPast, updatedAt: .distantPast, language: copy.text("en", "zh-Hant"))
        return snapshot.statusTitle(isStale: false)
    }
}
