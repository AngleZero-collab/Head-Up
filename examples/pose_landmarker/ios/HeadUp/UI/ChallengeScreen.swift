import SwiftUI
import HeadUpCore

struct ChallengeScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    var onShop: () -> Void = {}
    @State private var confirmsEyeRest = false
    @State private var recordedBreak = false
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.dynamicTypeSize) private var typeSize
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }
    private var claimablePoints: Int { store.tasks.filter { $0.isComplete && !$0.claimed }.reduce(0) { $0 + $1.reward } }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Small habits, brighter days", "每天一點，照顧自己"), symbol: "sparkles")
            progressSummary
            HeadUpCard {
                if sizeClass == .regular && !typeSize.isAccessibilitySize {
                    HStack(spacing: 28) { dragon; dragonDetails }
                } else {
                    VStack(spacing: 18) { dragon; dragonDetails }
                        .frame(maxWidth: .infinity)
                }
            }
            HeadUpCard {
                VStack(alignment: .leading, spacing: 14) {
                    Label(copy.text("20-20-20 eye break", "20-20-20 護眼休息"), systemImage: "eye")
                        .font(.headline)
                        .foregroundStyle(HeadUpStyle.cyan)
                    Text(copy.text("Every 20 minutes, look about 20 feet away for 20 seconds.", "每使用螢幕 20 分鐘，望向約 6 公尺遠處，讓眼睛休息 20 秒。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    ViewThatFits(in: .horizontal) {
                        HStack { breakCount; Spacer(minLength: 16); breakButton }
                            .fixedSize(horizontal: true, vertical: false)
                        VStack(alignment: .leading, spacing: 12) { breakCount; breakButton }
                    }
                    if recordedBreak {
                        Label(copy.text("Break recorded", "已記錄這次休息"), systemImage: "checkmark.circle.fill")
                            .font(.footnote).foregroundStyle(HeadUpStyle.safe)
                    }
                }
            }
            HeadUpSectionTitle(title: copy.text("Today's challenges", "今日挑戰"), symbol: "flag.checkered")
            ForEach(store.tasks) { task in
                HeadUpTaskRow(store: store, task: task, copy: copy)
            }
        }
        .accessibilityIdentifier("challengeScreen")
        .onDisappear { recordedBreak = false }
        .alert(copy.text("Give your eyes a break", "讓眼睛休息一下"), isPresented: $confirmsEyeRest) {
            Button(copy.text("I've finished my break", "我已完成休息")) {
                recordedBreak = store.recordEyeRest()
            }
            Button(copy.text("Not yet", "稍後再記錄"), role: .cancel) { }
        } message: {
            Text(copy.text("Look into the distance and relax your eyes. Confirm after you finish your break.", "請望向遠方，放鬆眼睛。完成休息後再按下確認。"))
        }
    }

    private var progressSummary: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 12) {
                Label(copy.text("Today's challenges", "今日挑戰進度"), systemImage: "flag.checkered")
                    .font(.headline)
                Text(copy.text("\(store.tasks.filter { $0.isComplete || $0.claimed }.count) of \(store.tasks.count) completed", "已完成 \(store.tasks.filter { $0.isComplete || $0.claimed }.count)／\(store.tasks.count) 項"))
                    .font(.title2.bold()).monospacedDigit()
                if claimablePoints > 0 {
                    Label(copy.text("\(claimablePoints) points ready to claim below", "下方有 \(claimablePoints) 點獎勵可領取"), systemImage: "gift.fill")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(HeadUpStyle.warning)
                } else if store.tasks.allSatisfy(\.claimed) {
                    Label(copy.text("All today's rewards claimed", "今日獎勵已全部領取"), systemImage: "checkmark.seal.fill")
                        .font(.subheadline.weight(.medium)).foregroundStyle(HeadUpStyle.safe)
                } else {
                    Text(copy.text("Complete a challenge, then claim its points below.", "完成挑戰後，在下方領取對應積分。"))
                        .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                }
                Button(action: onShop) {
                    Label(copy.text("View rewards · \(store.coins) points", "查看獎勵 · \(store.coins) 點"), systemImage: "bag")
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("challengeViewShop")
            }
        }
    }

    private var dragon: some View {
        DragonCompanionView(status: monitor.postureStatus, copy: copy)
            .frame(width: 220, height: 220)
            .background(monitor.postureStatus.dragonTint.opacity(0.08), in: RoundedRectangle(cornerRadius: 32))
            .overlay(RoundedRectangle(cornerRadius: 32).strokeBorder(monitor.postureStatus.dragonTint.opacity(0.45), lineWidth: 2))
    }

    private var dragonDetails: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(copy.text("My Vision Dragon", "我的視力龍"))
                .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
            Text(copy.text(monitor.postureStatus == .liftHead ? "Angry Dragon" : "Little Blue", monitor.postureStatus == .liftHead ? "生氣視力龍" : "藍色視力龍"))
                .font(.title2.bold())
            Label(dragonMood, systemImage: monitor.postureStatus.symbol)
                .font(.headline).foregroundStyle(monitor.postureStatus.dragonTint)
                .fixedSize(horizontal: false, vertical: true)
            Divider()
            Text(copy.text("Level \(store.dragonLevel) · Energy \(store.dragonEnergy)%", "等級 \(store.dragonLevel) · 能量 \(store.dragonEnergy)%"))
                .font(.subheadline.weight(.medium)).monospacedDigit()
            ProgressView(value: Double(store.dragonEnergy), total: 100)
                .tint(HeadUpStyle.cyan)
                .accessibilityLabel(copy.text("Dragon energy", "視力龍能量"))
                .accessibilityValue("\(store.dragonEnergy)%")
            Text(copy.text("Good posture builds energy while valid monitoring is active.", "有效偵測期間維持正確姿勢，就能累積能量。"))
                .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var breakCount: some View {
        Text(copy.text("Today: \(store.today.eyeRestCount) breaks", "今日已休息 \(store.today.eyeRestCount) 次"))
            .font(.subheadline).monospacedDigit()
    }

    private var breakButton: some View {
        Button { recordedBreak = false; confirmsEyeRest = true } label: {
            Text(copy.text("Record break", "紀錄休息")).frame(minHeight: 44)
        }
        .buttonStyle(.borderedProminent)
        .foregroundStyle(HeadUpStyle.top)
        .accessibilityIdentifier("recordEyeRest")
    }

    private var dragonMood: String {
        let snapshot = PostureActivityAttributes.ContentState(
            status: monitor.postureStatus, safeSeconds: 0, totalSeconds: 0,
            dayStart: .distantPast, updatedAt: .distantPast, language: copy.text("en", "zh-Hant"))
        return snapshot.statusTitle(isStale: false)
    }
}
