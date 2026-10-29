import XCTest
import SwiftData
import HeadUpCore
@testable import HeadUp

@MainActor
final class PostureActivityIntegrationTests: XCTestCase {
    func testMappingUsesExistingTwoSecondAlertAndRapidFall() {
        let engine = AlertEngine()
        let danger = PostureMetrics(angleDegrees: 30, zone: .danger, timestampMs: 0)
        for time in [0, 1999, 2000] {
            let alert = engine.process(metrics: danger, monotonicTimeMs: Int64(time))
            let state = MonitoringCoordinator.activityStatus(status: .monitoring, metrics: danger, warningActive: alert.warningActive)
            XCTAssertEqual(state, time < 2000 ? .attention : .liftHead)
        }
        XCTAssertEqual(MonitoringCoordinator.activityStatus(status: .monitoring, metrics: .init(angleDegrees: 23, zone: .warning, timestampMs: 0, isRapidFall: true), warningActive: false), .liftHead)
        XCTAssertEqual(MonitoringCoordinator.activityStatus(status: .monitoring, metrics: .init(angleDegrees: 0, zone: .safe, timestampMs: 0), warningActive: false), .good)
        XCTAssertEqual(MonitoringCoordinator.activityStatus(status: .noPose, metrics: danger, warningActive: true), .noPose)
        XCTAssertEqual(MonitoringCoordinator.activityStatus(status: .paused, metrics: danger, warningActive: true), .paused)
        XCTAssertEqual(MonitoringCoordinator.activityStatus(status: .calibrating, metrics: danger, warningActive: true), .calibrating)
        XCTAssertEqual(MonitoringCoordinator.activityStatus(status: .monitoring, metrics: nil, warningActive: true), .noPose)
    }

    func testSnapshotIsFiniteAndDoesNotInventTimeAfterMidnight() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let day = Date(timeIntervalSince1970: 1_800_000_000)
        let midnight = calendar.startOfDay(for: day)
        var clock = midnight.addingTimeInterval(-1)
        let store = try HeadUpStore(inMemory: true, now: { clock }, calendar: calendar)
        store.recordMetrics(.init(angleDegrees: 0, zone: .safe, timestampMs: Int64(clock.timeIntervalSince1970 * 1000)), elapsedMilliseconds: 0)
        clock = midnight.addingTimeInterval(1)
        store.recordMetrics(.init(angleDegrees: 0, zone: .safe, timestampMs: Int64(clock.timeIntervalSince1970 * 1000)), elapsedMilliseconds: 2000)
        let value = PostureActivityAttributes.ContentState(status: .good, safeSeconds: store.today.safeSeconds,
            totalSeconds: store.today.totalTrackedSeconds, dayStart: store.today.dayStart, updatedAt: clock, language: "zh-Hant")
        XCTAssertEqual(value.goodPercent, store.today.protectEyesPercent)
        XCTAssertEqual(value.totalSeconds, 1)
        XCTAssertTrue(value.isOutdated(true))
        XCTAssertEqual(value.statusTitle(isStale: true), "資料未更新")
        XCTAssertNotEqual(value.dayLabel(now: midnight.addingTimeInterval(86400), calendar: calendar), "今日")
        XCTAssertLessThan(try JSONEncoder().encode(value).count, 4096)
    }

    func testLegacyProfileDefaultsEnabledAndPreferencePersists() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("migration.sqlite")
        do {
            let container = try ModelContainer(for: StoredProfile.self, StoredPosture.self,
                configurations: ModelConfiguration(url: url, cloudKitDatabase: .none))
            let context = ModelContext(container)
            let legacy = #"{"days":{},"coins":42,"energy":50,"level":1,"energyRemainderMs":0,"owned":[],"alarm":false,"language":"english"}"#
            context.insert(StoredProfile(payload: Data(legacy.utf8)))
            try context.save()
        }
        do {
            let store = try HeadUpStore(storeURL: url)
            XCTAssertTrue(store.liveActivitiesEnabled)
            XCTAssertEqual(store.coins, 42)
            store.setLiveActivitiesEnabled(false)
        }
        let reopened = try HeadUpStore(storeURL: url)
        XCTAssertFalse(reopened.liveActivitiesEnabled)
        reopened.resetAllData()
        XCTAssertTrue(reopened.liveActivitiesEnabled)
    }
}
