import SwiftUI
import Charts
import HeadUpCore

struct StatsScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Your progress", "姿勢數據"), symbol: "chart.bar.xaxis")
            HeadUpCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text(copy.text("Current posture angle", "目前姿勢角度")).foregroundStyle(HeadUpStyle.secondary)
                    Text(monitor.metrics.map { "\($0.angleDegrees)°" } ?? "—")
                        .font(.system(size: 60, weight: .bold, design: .rounded))
                        .foregroundStyle(HeadUpStyle.color(for: monitor.metrics?.zone))
                        .monospacedDigit()
                    Text(copy.zone(monitor.metrics?.zone))
                        .font(.headline).foregroundStyle(HeadUpStyle.color(for: monitor.metrics?.zone))
                }
            }
            HeadUpSectionTitle(title: copy.text("Family dashboard", "家長守護報表"), symbol: "heart.text.clipboard")
            distribution
            weeklyChart
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 145), spacing: 14)], spacing: 14) {
                HeadUpStatTile(title: copy.text("Health points", "健康積分"), value: store.coins.formatted(), symbol: "star.circle.fill", color: HeadUpStyle.warning)
                HeadUpStatTile(title: copy.text("Good posture today", "今日好姿勢"), value: copy.duration(store.today.safeSeconds), symbol: "figure.seated.side", color: HeadUpStyle.safe)
                HeadUpStatTile(title: copy.text("Good posture share", "正確姿勢比例"), value: "\(store.today.protectEyesPercent)%", symbol: "heart.fill")
                HeadUpStatTile(title: copy.text("Day streak", "連續天數"), value: "\(store.consecutiveDays)", symbol: "flame.fill", color: HeadUpStyle.purple)
            }
            HeadUpSectionTitle(title: copy.text("Daily tasks", "每日任務"), symbol: "checklist")
            ForEach(store.tasks) { task in
                HeadUpTaskRow(store: store, task: task, copy: copy)
            }
        }
        .accessibilityIdentifier("statsScreen")
    }

    private var distribution: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 20) {
                Text(copy.text("Today's posture", "今日姿勢分布")).font(.headline)
                if store.today.totalTrackedSeconds == 0 {
                    ContentUnavailableView {
                        Label(copy.text("A fresh start", "開始今天的紀錄"), systemImage: "chart.pie")
                    } description: {
                        Text(copy.text("Start monitoring to see your daily posture balance.", "開始姿勢守護後，即可查看今天的姿勢分布。"))
                    }
                } else {
                    Chart {
                        SectorMark(angle: .value(copy.text("Seconds", "秒數"), store.today.safeSeconds), innerRadius: .ratio(0.7), angularInset: 2)
                            .foregroundStyle(HeadUpStyle.safe)
                        SectorMark(angle: .value(copy.text("Seconds", "秒數"), store.today.warningSeconds), innerRadius: .ratio(0.7), angularInset: 2)
                            .foregroundStyle(HeadUpStyle.warning)
                        SectorMark(angle: .value(copy.text("Seconds", "秒數"), store.today.dangerSeconds), innerRadius: .ratio(0.7), angularInset: 2)
                            .foregroundStyle(HeadUpStyle.danger)
                    }
                    .frame(height: 180)
                    .chartBackground { _ in
                        VStack(spacing: 4) {
                            Text("\(store.today.protectEyesPercent)%").font(.title.bold()).monospacedDigit()
                            Text(copy.text("Good posture", "正確姿勢")).font(.caption).foregroundStyle(HeadUpStyle.secondary)
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(copy.text("Good posture \(store.today.protectEyesPercent) percent", "正確姿勢 \(store.today.protectEyesPercent) 百分比"))

                    VStack(spacing: 10) {
                        summaryRow(copy.text("Good posture", "正確姿勢"), seconds: store.today.safeSeconds, color: HeadUpStyle.safe)
                        summaryRow(copy.text("Warning", "姿勢警戒"), seconds: store.today.warningSeconds, color: HeadUpStyle.warning)
                        summaryRow(copy.text("Needs attention", "姿勢不良"), seconds: store.today.dangerSeconds, color: HeadUpStyle.danger)
                    }
                    Text(copy.text("Tracked today: \(copy.duration(store.today.totalTrackedSeconds))", "今日已紀錄：\(copy.duration(store.today.totalTrackedSeconds))"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                }
            }
        }
    }

    private var weeklyChart: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 18) {
                Text(copy.text("Posture alerts · last 7 days", "近七日姿勢不良次數")).font(.headline)
                Chart(store.week) { day in
                    BarMark(x: .value(copy.text("Day", "日期"), day.dayStart, unit: .day),
                            y: .value(copy.text("Events", "次數"), day.dangerEvents))
                        .foregroundStyle(HeadUpStyle.purple.gradient)
                        .cornerRadius(5)
                        .accessibilityLabel(day.dayStart.formatted(.dateTime.month().day()))
                        .accessibilityValue(copy.text("\(day.dangerEvents) events", "\(day.dangerEvents) 次"))
                }
                .chartXAxis {
                    AxisMarks(values: .stride(by: .day)) { _ in
                        AxisValueLabel(format: .dateTime.weekday(.abbreviated))
                    }
                }
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) {
                        AxisGridLine().foregroundStyle(.white.opacity(0.08))
                        AxisValueLabel()
                    }
                }
                .chartYScale(domain: 0...max(3, store.week.map(\.dangerEvents).max() ?? 0))
                .frame(height: 180)
                Text(copy.text("An event starts each time posture enters the danger zone.", "每次姿勢進入不良區間時，計為一次事件。"))
                    .font(.caption).foregroundStyle(HeadUpStyle.secondary)
            }
        }
    }

    private func summaryRow(_ title: String, seconds: Int64, color: Color) -> some View {
        HStack {
            Circle().fill(color).frame(width: 9, height: 9)
            Text(title).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
            Spacer()
            Text(copy.duration(seconds)).font(.subheadline.bold()).monospacedDigit()
        }
        .accessibilityElement(children: .combine)
    }
}

struct HeadUpTaskRow: View {
    @ObservedObject var store: HeadUpStore
    let task: HeadUpTask
    let copy: HeadUpCopy
    @State private var showsReward = false

    var body: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    Image(systemName: task.claimed ? "checkmark.seal.fill" : "target")
                        .font(.title2).foregroundStyle(task.claimed ? HeadUpStyle.safe : HeadUpStyle.purple)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(copy.taskTitle(task.id)).font(.headline)
                        Text(copy.taskDetail(task.id)).font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                    }
                    Spacer(minLength: 0)
                }
                ProgressView(value: Double(task.progress), total: Double(max(1, task.max)))
                    .tint(task.claimed ? HeadUpStyle.safe : HeadUpStyle.cyan)
                    .accessibilityLabel(copy.taskTitle(task.id))
                HStack {
                    Text("\(task.progress)/\(task.max)").font(.subheadline).monospacedDigit().foregroundStyle(HeadUpStyle.secondary)
                    Spacer()
                    Button {
                        showsReward = store.claimTask(task.id)
                    } label: {
                        Label(task.claimed ? copy.text("Claimed", "已領取") : copy.text("Claim +\(task.reward)", "領取 +\(task.reward)"),
                              systemImage: task.claimed ? "checkmark" : "star.fill")
                            .font(.subheadline.bold())
                    }
                    .buttonStyle(.bordered)
                    .tint(task.claimed ? HeadUpStyle.safe : HeadUpStyle.warning)
                    .disabled(task.claimed || !task.isComplete)
                    .accessibilityIdentifier("claim_\(task.id)")
                }
            }
        }
        .alert(copy.text("Reward claimed", "獎勵已領取"), isPresented: $showsReward) {
            Button(copy.text("Great", "完成"), role: .cancel) { }
        } message: {
            Text(copy.text("\(task.reward) health points added.", "已獲得 \(task.reward) 點健康積分。"))
        }
    }
}
