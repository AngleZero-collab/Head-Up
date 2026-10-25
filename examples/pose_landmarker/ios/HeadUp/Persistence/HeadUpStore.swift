import Foundation
import Combine
import SwiftData
import HeadUpCore

enum AppLanguage: String, Codable, CaseIterable { case system, traditionalChinese, english }
struct HeadUpTask: Identifiable {
    let id: String; let reward: Int; let progress: Int; let max: Int; let claimed: Bool
    var isComplete: Bool { progress >= max }
}
struct ShopItem: Identifiable { let id: String; let cost: Int; let isOwned: Bool }
struct DailyPostureSummary: Identifiable {
    let date: Date
    var safeSeconds: Int64 = 0; var warningSeconds: Int64 = 0; var dangerSeconds: Int64 = 0
    var dangerEvents = 0; var eyeRestCount = 0
    var id: Date { date }
    var dayStart: Date { date }
    var totalTrackedSeconds: Int64 { safeSeconds + warningSeconds + dangerSeconds }
    var protectEyesPercent: Int { totalTrackedSeconds == 0 ? 0 : Int(safeSeconds * 100 / totalTrackedSeconds) }
}

@Model final class StoredProfile {
    @Attribute(.unique) var key: String
    var payload: Data
    init(payload: Data) { key = "headup-v1"; self.payload = payload }
}
@Model final class StoredPosture {
    var timestamp: Date
    var durationMs: Int64
    var payload: Data
    var source: String
    init(metrics: PostureMetrics, durationMs: Int64, source: String) throws {
        timestamp = Date(timeIntervalSince1970: Double(metrics.timestampMs) / 1000)
        self.durationMs = durationMs; self.source = source
        payload = try JSONEncoder().encode(metrics)
    }
}

private struct DayTotals: Codable {
    var safe: Int64 = 0; var warning: Int64 = 0; var danger: Int64 = 0
    var events = 0; var rests = 0; var claimed: Set<String> = []
}
private struct SavedState: Codable {
    var days: [String: DayTotals] = [:]
    var coins = 0; var energy = 50; var level = 1
    var energyRemainderMs: Int64 = 0
    var owned: Set<String> = []
    var calibration: CalibrationProfile?
    var alarm = false
    var language = AppLanguage.system
}

@MainActor final class HeadUpStore: ObservableObject {
    @Published private(set) var metrics: PostureMetrics?
    @Published private(set) var today = DailyPostureSummary(date: Date())
    @Published private(set) var week: [DailyPostureSummary] = []
    @Published private(set) var coins = 0
    @Published private(set) var dragonEnergy = 50
    @Published private(set) var dragonLevel = 1
    @Published private(set) var consecutiveDays = 0
    @Published private(set) var ownedShopItems: Set<String> = []
    @Published private(set) var claimedTaskIDs: Set<String> = []
    @Published private(set) var calibration: CalibrationProfile?
    @Published private(set) var alarmEnabled = false
    @Published private(set) var language = AppLanguage.system
    @Published var errorMessage: String?
    private let container: ModelContainer
    private let context: ModelContext
    private let now: () -> Date
    private let calendar: Calendar
    private var profile: StoredProfile
    private var state: SavedState
    private var savedPayload: Data
    private var previousTimestamp: Int64?
    private var previousZone: PostureZone?
    private var pendingMs: Int64 = 0
    private var pendingMetrics: PostureMetrics?
    private var pendingSource = "foreground"

    init(inMemory: Bool = false, now: @escaping () -> Date = Date.init,
         calendar: Calendar = .current, storeURL: URL? = nil) throws {
        self.now = now; self.calendar = calendar
        let schema = Schema([StoredProfile.self, StoredPosture.self])
        let config: ModelConfiguration
        if let storeURL { config = ModelConfiguration(schema: schema, url: storeURL, cloudKitDatabase: .none) }
        else { config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory, cloudKitDatabase: .none) }
        container = try ModelContainer(for: schema, configurations: [config])
        context = ModelContext(container); context.autosaveEnabled = false
        if let existing = try context.fetch(FetchDescriptor<StoredProfile>()).first {
            profile = existing; savedPayload = existing.payload
            state = try JSONDecoder().decode(SavedState.self, from: existing.payload)
        } else {
            state = SavedState(); savedPayload = try JSONEncoder().encode(state)
            profile = StoredProfile(payload: savedPayload); context.insert(profile); try context.save()
        }
        refresh()
    }

    var tasks: [HeadUpTask] {
        [HeadUpTask(id: "good_posture", reward: 50, progress: min(30, Int(today.safeSeconds / 60)), max: 30, claimed: claimedTaskIDs.contains("good_posture")),
         HeadUpTask(id: "eye_rest", reward: 50, progress: min(3, today.eyeRestCount), max: 3, claimed: claimedTaskIDs.contains("eye_rest")),
         HeadUpTask(id: "posture_challenge", reward: 100, progress: today.totalTrackedSeconds >= 600 && today.protectEyesPercent >= 80 ? 1 : 0, max: 1, claimed: claimedTaskIDs.contains("posture_challenge"))]
    }
    var shopItems: [ShopItem] {
        [("starlight_armor",300),("ocean_background",180),("eye_time_ticket",500),("focus_badge",220)].map {
            ShopItem(id: $0.0, cost: $0.1, isOwned: ownedShopItems.contains($0.0))
        }
    }

    func recordMetrics(_ value: PostureMetrics, source: String = "foreground", elapsedMilliseconds: Int64? = nil) {
        metrics = value
        let end = Date(timeIntervalSince1970: Double(value.timestampMs) / 1000)
        if value.zone == .danger && previousZone != .danger {
            let day = key(end); var totals = state.days[day] ?? DayTotals(); totals.events += 1; state.days[day] = totals
        }
        let elapsed = previousTimestamp.map { elapsedMilliseconds ?? (value.timestampMs - $0) } ?? 0
        previousTimestamp = value.timestampMs; previousZone = value.zone
        guard elapsed > 0 && elapsed <= 2_000 else { publish(); return }
        var cursor = end.addingTimeInterval(-Double(elapsed) / 1000)
        var remaining = elapsed
        while remaining > 0 {
            let day = key(cursor)
            let nextMidnight = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: cursor))!
            let segment = min(remaining, max(1, Int64((nextMidnight.timeIntervalSince(cursor) * 1000).rounded())))
            var totals = state.days[day] ?? DayTotals()
            switch value.zone { case .safe: totals.safe += segment; case .warning: totals.warning += segment; case .danger: totals.danger += segment }
            state.days[day] = totals
            cursor = cursor.addingTimeInterval(Double(segment) / 1000); remaining -= segment
        }
        if value.zone != .warning {
            state.energyRemainderMs += value.zone == .safe ? elapsed : -elapsed
            while state.energyRemainderMs >= 1000 {
                state.energyRemainderMs -= 1000
                let old = state.energy
                state.energy = min(100, old + 1)
                if old < 100 && state.energy == 100 { state.level += 1 }
            }
            while state.energyRemainderMs <= -1000 {
                state.energyRemainderMs += 1000
                state.energy = max(0, state.energy - 1)
            }
        }
        pendingMs += elapsed; pendingMetrics = value; pendingSource = source
        if pendingMs >= 1_000 { _ = persist() }
        publish()
    }

    func endMonitoring() {
        if pendingMs > 0 || previousZone != nil { _ = persist() }
        previousTimestamp = nil; previousZone = nil; metrics = nil
    }
    func refresh() {
        let cutoff = calendar.date(byAdding: .day, value: -90, to: now())!
        let minimum = key(cutoff)
        state.days = state.days.filter { $0.key >= minimum }
        do {
            let old = try context.fetch(FetchDescriptor<StoredPosture>(predicate: #Predicate { $0.timestamp < cutoff }))
            old.forEach(context.delete)
            let payload = try JSONEncoder().encode(state)
            if !old.isEmpty || profile.payload != payload { _ = persist() }
        } catch { errorMessage = error.localizedDescription }
        publish()
    }
    func setCalibration(_ profile: CalibrationProfile?) { state.calibration = profile; _ = persist(); publish() }
    func setAlarmEnabled(_ enabled: Bool) { state.alarm = enabled; _ = persist(); publish() }
    func setLanguage(_ value: AppLanguage) { state.language = value; _ = persist(); publish() }
    func recordEyeRest() {
        let day = key(now()); var totals = state.days[day] ?? DayTotals(); totals.rests += 1; state.days[day] = totals
        _ = persist(); publish()
    }
    @discardableResult func claimTask(_ id: String) -> Bool {
        publish()
        guard let task = tasks.first(where: { $0.id == id }), task.isComplete, !task.claimed else { return false }
        let day = key(now()); var totals = state.days[day] ?? DayTotals(); totals.claimed.insert(id); state.days[day] = totals
        state.coins += task.reward
        let success = persist(); publish(); return success
    }
    @discardableResult func purchaseItem(_ id: String) -> Bool {
        guard let item = shopItems.first(where: { $0.id == id }), !state.owned.contains(id), state.coins >= item.cost else { return false }
        state.coins -= item.cost; state.owned.insert(id)
        let success = persist(); publish(); return success
    }
    func resetAllData() {
        do {
            try context.delete(model: StoredPosture.self)
            state = SavedState(); pendingMs = 0; pendingMetrics = nil
            previousTimestamp = nil; previousZone = nil; metrics = nil
            _ = persist(); publish()
        } catch { context.rollback(); errorMessage = error.localizedDescription }
    }
    private func persist() -> Bool {
        do {
            if let pendingMetrics, pendingMs > 0 {
                context.insert(try StoredPosture(metrics: pendingMetrics, durationMs: pendingMs, source: pendingSource))
            }
            let payload = try JSONEncoder().encode(state)
            profile.payload = payload
            try context.save()
            savedPayload = payload; pendingMs = 0; pendingMetrics = nil
            return true
        } catch {
            context.rollback()
            if let restored = try? JSONDecoder().decode(SavedState.self, from: savedPayload) { state = restored }
            pendingMs = 0; pendingMetrics = nil; previousTimestamp = nil; previousZone = nil
            errorMessage = error.localizedDescription
            return false
        }
    }
    private func key(_ date: Date) -> String {
        let c = calendar.dateComponents([.year,.month,.day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
    private func summary(_ date: Date) -> DailyPostureSummary {
        let d = state.days[key(date)] ?? DayTotals()
        return DailyPostureSummary(date: calendar.startOfDay(for: date), safeSeconds: d.safe / 1000,
                                   warningSeconds: d.warning / 1000, dangerSeconds: d.danger / 1000,
                                   dangerEvents: d.events, eyeRestCount: d.rests)
    }
    private func publish() {
        let date = now()
        today = summary(date)
        week = (0..<7).reversed().map { summary(calendar.date(byAdding: .day, value: -$0, to: date)!) }
        coins = state.coins; dragonEnergy = state.energy; dragonLevel = state.level
        ownedShopItems = state.owned; claimedTaskIDs = state.days[key(date)]?.claimed ?? []
        calibration = state.calibration; alarmEnabled = state.alarm; language = state.language
        consecutiveDays = 0
        for day in week.reversed() { if day.safeSeconds > 0 { consecutiveDays += 1 } else { break } }
    }
}
