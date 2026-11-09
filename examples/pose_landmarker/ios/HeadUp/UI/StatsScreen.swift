import SwiftUI
import Charts
import HeadUpCore

struct StatsScreen: View {
    @ObservedObject var store: HeadUpStore
    @ObservedObject var monitor: MonitoringCoordinator
    var onStart: () -> Void = {}
    var onChallenges: () -> Void = {}
    @State private var weeklyMetric = WeeklyMetric.share
    @State private var selectedDate: Date?
    @Environment(\.dynamicTypeSize) private var typeSize
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }
    private enum WeeklyMetric: Hashable { case share, time }
    private var selectedDay: DailyPostureSummary {
        store.week.first { day in selectedDate.map { Calendar.current.isDate(day.date, inSameDayAs: $0) } ?? false } ?? store.today
    }
    private func snapshot(_ day: DailyPostureSummary) -> PostureActivityAttributes.ContentState {
        .init(status: .paused, safeSeconds: day.safeSeconds, totalSeconds: day.totalTrackedSeconds,
              dayStart: day.dayStart, updatedAt: Date(), language: copy.text("en", "zh-Hant"))
    }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Your day, at a glance", "今天的姿勢表現"), symbol: "chart.bar.xaxis")
            todaySummary
            if store.today.totalTrackedSeconds > 0 { distribution }
            weeklyChart
            HeadUpCard {
                VStack(alignment: .leading, spacing: 12) {
                    Label(copy.text("Turn progress into rewards", "把進步變成獎勵"), systemImage: "star.circle.fill")
                        .font(.headline).foregroundStyle(HeadUpStyle.warning)
                    Text(challengeSummary).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    Button(action: onChallenges) {
                        Label(copy.text("View today's challenges", "查看今日挑戰"), systemImage: "arrow.right")
                            .frame(minHeight: 44)
                    }
                    .buttonStyle(.bordered).accessibilityIdentifier("statsChallenges")
                }
            }
        }
        .accessibilityIdentifier("statsScreen")
    }

    private var todaySummary: some View {
        let today = snapshot(store.today)
        return HeadUpCard {
            VStack(alignment: .leading, spacing: 16) {
                Text(today.dayLabel()).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                Text(today.percentText)
                    .font(.system(size: 56, weight: .bold, design: .rounded))
                    .monospacedDigit().foregroundStyle(HeadUpStyle.cyan)
                    .accessibilityLabel(copy.text("Good posture share: \(today.percentText)", "正確姿勢比例：\(today.percentText)"))
                Text(copy.text("Good posture during monitoring", "有效偵測期間的正確姿勢"))
                    .font(.headline)
                Label(today.goalText, systemImage: store.today.totalTrackedSeconds >= 600 && store.today.protectEyesPercent >= 80 ? "checkmark.seal.fill" : "chart.line.uptrend.xyaxis")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(HeadUpStyle.cyan)
                Divider()
                summaryRow(copy.text("Valid monitoring", "有效偵測時間"), value: today.trackedDuration, color: HeadUpStyle.cyan)
                Text(copy.text("Only time with a detected posture counts. Pauses and time away from the camera are excluded.", "只計入有偵測到姿勢的時間；暫停與未入鏡時間不會算成正確姿勢。"))
                    .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                if store.today.totalTrackedSeconds == 0 {
                    Button(action: onStart) {
                        Label(copy.text("Go to Scan", "前往掃描，開始紀錄"), systemImage: "viewfinder")
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderedProminent).foregroundStyle(HeadUpStyle.top)
                    .accessibilityIdentifier("statsStartMonitoring")
                } else if store.today.totalTrackedSeconds < 600 {
                    ProgressView(value: Double(store.today.totalTrackedSeconds), total: 600)
                        .tint(HeadUpStyle.cyan).accessibilityLabel(copy.text("Progress to 10 monitored minutes", "累積偵測 10 分鐘進度"))
                    Text(copy.text("Keep collecting. Your daily goal is assessed after 10 monitored minutes.", "繼續累積，滿 10 分鐘有效偵測後再判定今日目標。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                } else {
                    Text(copy.text("Daily goal: at least 80% good posture.", "今日目標：正確姿勢比例達 80%。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                }
            }
        }
    }

    private var distribution: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 16) {
                Text(copy.text("Where your time went", "今天的姿勢分布")).font(.headline)
                Chart {
                    SectorMark(angle: .value("Seconds", store.today.safeSeconds), innerRadius: .ratio(0.72), angularInset: 2)
                        .foregroundStyle(HeadUpStyle.safe)
                    SectorMark(angle: .value("Seconds", store.today.warningSeconds), innerRadius: .ratio(0.72), angularInset: 2)
                        .foregroundStyle(HeadUpStyle.warning)
                    SectorMark(angle: .value("Seconds", store.today.dangerSeconds), innerRadius: .ratio(0.72), angularInset: 2)
                        .foregroundStyle(HeadUpStyle.danger)
                }
                .frame(height: 160)
                .accessibilityHidden(true)
                summaryRow(copy.text("Good posture", "姿勢良好"), value: preciseDuration(store.today.safeSeconds), color: HeadUpStyle.safe)
                summaryRow(copy.text("Attention", "需要注意"), value: preciseDuration(store.today.warningSeconds), color: HeadUpStyle.warning)
                summaryRow(copy.text("Poor posture", "姿勢不良"), value: preciseDuration(store.today.dangerSeconds), color: HeadUpStyle.danger)
            }
        }
    }

    private var weeklyChart: some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 16) {
                Text(copy.text("Explore the last 7 days", "回顧近 7 天")).font(.headline)
                if typeSize.isAccessibilitySize {
                    metricPicker.pickerStyle(.menu).frame(minHeight: 44)
                } else {
                    metricPicker.pickerStyle(.segmented)
                }
                Chart(Array(store.week.enumerated()), id: \.element.id) { index, day in
                    if day.totalTrackedSeconds > 0 {
                        BarMark(x: .value("Day", index), y: .value("Value", chartValue(day)))
                            .foregroundStyle(HeadUpStyle.cyan.gradient).cornerRadius(5)
                            .accessibilityLabel(dateLabel(day.date))
                            .accessibilityValue(chartDescription(day))
                        if chartValue(day) == 0 {
                            PointMark(x: .value("Day", index), y: .value("Value", 0))
                                .foregroundStyle(HeadUpStyle.cyan).symbolSize(28)
                                .accessibilityLabel(dateLabel(day.date))
                                .accessibilityValue(chartDescription(day))
                        }
                    }
                }
                .chartXScale(domain: -0.5...6.5)
                .chartYScale(domain: 0...chartMaximum)
                .chartXAxis {
                    AxisMarks(values: Array(0..<store.week.count)) { value in
                        if let index = value.as(Int.self), store.week.indices.contains(index) {
                            AxisValueLabel { Text(dateLabel(store.week[index].date)) }
                        }
                    }
                }
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) {
                        AxisGridLine().foregroundStyle(.white.opacity(0.08))
                        AxisValueLabel()
                    }
                }
                .frame(height: 170)
                Text(copy.text(weeklyMetric == .share ? "Percent · blank days have no monitoring records." : "Minutes · blank days have no monitoring records.", weeklyMetric == .share ? "單位：% · 空白日期代表沒有偵測紀錄。" : "單位：分鐘 · 空白日期代表沒有偵測紀錄。"))
                    .font(.caption).foregroundStyle(HeadUpStyle.secondary)
                Text(copy.text("Choose a day for details", "選一天，查看詳細紀錄"))
                    .font(.subheadline.weight(.medium))
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(store.week) { day in
                            Button { selectedDate = day.date } label: {
                                VStack(spacing: 6) {
                                    Text(dateLabel(day.date)).font(.caption)
                                    Text(chartDescription(day)).font(.caption.bold()).monospacedDigit()
                                }
                                .padding(10).frame(minWidth: 60, minHeight: 54)
                                .background(Calendar.current.isDate(day.date, inSameDayAs: selectedDay.date) ? HeadUpStyle.cyan.opacity(0.2) : HeadUpStyle.top, in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(dateLabel(day.date))
                            .accessibilityValue(day.totalTrackedSeconds > 0 ? chartDescription(day) : copy.text("No monitoring records", "沒有偵測紀錄"))
                            .accessibilityAddTraits(Calendar.current.isDate(day.date, inSameDayAs: selectedDay.date) ? .isSelected : [])
                        }
                    }
                }
                let chosen = snapshot(selectedDay)
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(dateLabel(selectedDay.date)) · \(chosen.goalText)").font(.subheadline.bold())
                    summaryRow(copy.text("Good posture", "正確姿勢"), value: chosen.percentText, color: HeadUpStyle.safe)
                    summaryRow(copy.text("Valid monitoring", "有效偵測"), value: preciseDuration(selectedDay.totalTrackedSeconds), color: HeadUpStyle.cyan)
                }
                .padding(12).background(HeadUpStyle.top.opacity(0.65), in: RoundedRectangle(cornerRadius: 14))
            }
        }
    }

    private var metricPicker: some View {
        Picker(copy.text("Chart metric", "圖表內容"), selection: $weeklyMetric) {
            Text(copy.text("Good posture %", "正確比例")).tag(WeeklyMetric.share)
            Text(copy.text("Monitored time", "偵測時間")).tag(WeeklyMetric.time)
        }
        .accessibilityIdentifier("weeklyMetricPicker")
    }

    private var chartMaximum: Double {
        weeklyMetric == .share ? 100 : max(10, store.week.map { Double($0.totalTrackedSeconds) / 60 }.max() ?? 10)
    }
    private func chartValue(_ day: DailyPostureSummary) -> Double {
        weeklyMetric == .share ? Double(day.protectEyesPercent) : Double(day.totalTrackedSeconds) / 60
    }
    private func chartDescription(_ day: DailyPostureSummary) -> String {
        guard day.totalTrackedSeconds > 0 else { return "—" }
        return weeklyMetric == .share ? snapshot(day).percentText : preciseDuration(day.totalTrackedSeconds)
    }
    private func preciseDuration(_ seconds: Int64) -> String {
        seconds > 0 && seconds < 60 ? copy.text("\(seconds) sec", "\(seconds) 秒") : copy.duration(seconds)
    }
    private func dateLabel(_ date: Date) -> String {
        date.formatted(.dateTime.month(.defaultDigits).day().locale(copy.locale))
    }
    private var challengeSummary: String {
        let ready = store.tasks.filter { $0.isComplete && !$0.claimed }.count
        if ready > 0 { return copy.text("\(ready) rewards ready to claim.", "有 \(ready) 項獎勵可以領取。") }
        let claimed = store.tasks.filter(\.claimed).count
        return copy.text("\(claimed) of \(store.tasks.count) daily rewards claimed.", "今日已領取 \(claimed)／\(store.tasks.count) 項獎勵。")
    }
    private func summaryRow(_ title: String, value: String, color: Color) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Label(title, systemImage: "circle.fill").font(.subheadline).foregroundStyle(color)
            Spacer(minLength: 8)
            Text(value).font(.subheadline.bold()).monospacedDigit()
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
                Label(copy.taskTitle(task.id), systemImage: task.claimed ? "checkmark.seal.fill" : "target")
                    .font(.headline).foregroundStyle(task.claimed ? HeadUpStyle.safe : .white)
                Text(copy.taskDetail(task.id)).font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                if task.id == "posture_challenge" && !task.claimed {
                    progress(copy.text("Monitoring: \(min(10, store.today.totalTrackedSeconds / 60))/10 min", "有效偵測：\(min(10, store.today.totalTrackedSeconds / 60))／10 分鐘"), value: Double(store.today.totalTrackedSeconds), total: 600)
                    let percent = store.today.totalTrackedSeconds > 0 ? "\(store.today.protectEyesPercent)%" : "—"
                    progress(copy.text("Good posture: \(percent) · goal 80%", "正確姿勢：\(percent) · 目標 80%"), value: Double(store.today.protectEyesPercent), total: 80)
                } else {
                    progress(progressText, value: task.claimed ? Double(task.max) : Double(task.progress), total: Double(task.max))
                }
                Text(task.claimed ? copy.text("Reward claimed", "獎勵已領取") : task.isComplete ? copy.text("Completed · ready to claim", "已完成，可以領獎") : copy.text("In progress · +\(task.reward) points when complete", "累積中 · 完成可獲得 \(task.reward) 點"))
                    .font(.footnote.weight(.medium)).foregroundStyle(task.claimed || task.isComplete ? HeadUpStyle.safe : HeadUpStyle.secondary)
                if task.isComplete && !task.claimed {
                    Button { showsReward = store.claimTask(task.id) } label: {
                        Label(copy.text("Claim +\(task.reward) points", "領取 \(task.reward) 點"), systemImage: "star.fill")
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.bordered).tint(HeadUpStyle.warning)
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

    private var progressText: String {
        if task.claimed { return copy.text("Completed today", "今日已完成") }
        if task.id == "good_posture" { return copy.text("\(task.progress)/30 min of good posture", "正確姿勢 \(task.progress)／30 分鐘") }
        if task.id == "eye_rest" { return copy.text("\(task.progress)/3 breaks", "已休息 \(task.progress)／3 次") }
        return copy.text("Daily goal completed", "今日目標已完成")
    }
    private func progress(_ label: String, value: Double, total: Double) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
            ProgressView(value: min(max(0, value), total), total: max(1, total))
                .tint(task.claimed ? HeadUpStyle.safe : HeadUpStyle.cyan)
                .accessibilityLabel(label)
        }
    }
}
