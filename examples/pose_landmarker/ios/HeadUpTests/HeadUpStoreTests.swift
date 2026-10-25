import XCTest
import HeadUpCore
@testable import HeadUp

final class HeadUpStoreTests: XCTestCase {
    private let base = Date(timeIntervalSince1970: 1_800_000_000)
    private func metric(_ date: Date, zone: PostureZone = .safe) -> PostureMetrics {
        PostureMetrics(angleDegrees: zone == .danger ? 30 : 0, zone: zone,
                       timestampMs: Int64((date.timeIntervalSince1970 * 1000).rounded()))
    }
    @MainActor func testNoCreditForFirstFramePauseAndLongGap() async throws {
        let store = try HeadUpStore(inMemory: true, now: { self.base })
        store.recordMetrics(metric(base), elapsedMilliseconds: 2000)
        XCTAssertEqual(store.today.totalTrackedSeconds, 0)
        store.recordMetrics(metric(base.addingTimeInterval(1)), elapsedMilliseconds: 1000)
        XCTAssertEqual(store.today.safeSeconds, 1)
        store.endMonitoring()
        store.recordMetrics(metric(base.addingTimeInterval(100)), elapsedMilliseconds: 2000)
        XCTAssertEqual(store.today.safeSeconds, 1)
        store.recordMetrics(metric(base.addingTimeInterval(110)), elapsedMilliseconds: 10000)
        XCTAssertEqual(store.today.safeSeconds, 1)
    }
    @MainActor func testFractionalFramesAccumulateExactly() async throws {
        let store = try HeadUpStore(inMemory: true, now: { self.base })
        store.recordMetrics(metric(base), elapsedMilliseconds: 0)
        for i in 1...10 { store.recordMetrics(metric(base.addingTimeInterval(Double(i)/10)), elapsedMilliseconds: 100) }
        XCTAssertEqual(store.today.safeSeconds, 1)
        XCTAssertEqual(store.dragonEnergy, 51)
    }
    @MainActor func testDangerCountsEpisodesAndNotEveryFrame() async throws {
        let store = try HeadUpStore(inMemory: true, now: { self.base })
        for i in 0..<4 { store.recordMetrics(metric(base.addingTimeInterval(Double(i)), zone: .danger), elapsedMilliseconds: 1000) }
        XCTAssertEqual(store.today.dangerEvents, 1)
        store.recordMetrics(metric(base.addingTimeInterval(4)), elapsedMilliseconds: 1000)
        store.recordMetrics(metric(base.addingTimeInterval(5), zone: .danger), elapsedMilliseconds: 1000)
        XCTAssertEqual(store.today.dangerEvents, 2)
    }
    @MainActor func testDailyRewardsPurchasesAndReset() async throws {
        let store = try HeadUpStore(inMemory: true, now: { self.base })
        XCTAssertFalse(store.claimTask("eye_rest"))
        XCTAssertFalse(store.purchaseItem("ocean_background"))
        for _ in 0..<3 { store.recordEyeRest() }
        XCTAssertTrue(store.claimTask("eye_rest"))
        XCTAssertFalse(store.claimTask("eye_rest"))
        XCTAssertEqual(store.coins, 50)
        for i in 0...1800 { store.recordMetrics(metric(base.addingTimeInterval(Double(i))), elapsedMilliseconds: 1000) }
        XCTAssertTrue(store.claimTask("good_posture"))
        XCTAssertTrue(store.claimTask("posture_challenge"))
        XCTAssertEqual(store.coins, 200)
        XCTAssertTrue(store.purchaseItem("ocean_background"))
        XCTAssertEqual(store.coins, 20)
        XCTAssertFalse(store.purchaseItem("ocean_background"))
        store.setCalibration(.init(angleDegrees: 10, postureRatio: 0.82, shoulderWidth: 0.4))
        store.resetAllData()
        XCTAssertEqual(store.coins, 0); XCTAssertNil(store.calibration)
        XCTAssertEqual(store.today.totalTrackedSeconds, 0); XCTAssertTrue(store.ownedShopItems.isEmpty)
    }
    @MainActor func testMidnightSplitAndRewardRollover() async throws {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let midnight = calendar.startOfDay(for: base)
        var clock = midnight.addingTimeInterval(-1)
        let store = try HeadUpStore(inMemory: true, now: { clock }, calendar: calendar)
        for _ in 0..<3 { store.recordEyeRest() }; XCTAssertTrue(store.claimTask("eye_rest"))
        store.recordMetrics(metric(clock), elapsedMilliseconds: 0)
        clock = midnight.addingTimeInterval(1)
        store.recordMetrics(metric(clock), elapsedMilliseconds: 2000)
        XCTAssertEqual(store.today.safeSeconds, 1)
        XCTAssertEqual(store.week[5].safeSeconds, 1)
        XCTAssertEqual(store.today.eyeRestCount, 0); XCTAssertTrue(store.claimedTaskIDs.isEmpty)
        XCTAssertEqual(store.coins, 50)
    }
    @MainActor func testDiskRelaunchPreservesSettingsAndTotals() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("store.sqlite")
        do {
            let store = try HeadUpStore(now: { self.base }, storeURL: url)
            store.setLanguage(.traditionalChinese); store.setAlarmEnabled(true)
            store.recordEyeRest(); store.recordMetrics(metric(base), elapsedMilliseconds: 0)
            store.recordMetrics(metric(base.addingTimeInterval(1)), elapsedMilliseconds: 1000)
            store.endMonitoring()
        }
        let reopened = try HeadUpStore(now: { self.base }, storeURL: url)
        XCTAssertEqual(reopened.language, .traditionalChinese); XCTAssertTrue(reopened.alarmEnabled)
        XCTAssertEqual(reopened.today.safeSeconds, 1); XCTAssertEqual(reopened.today.eyeRestCount, 1)
    }
}
