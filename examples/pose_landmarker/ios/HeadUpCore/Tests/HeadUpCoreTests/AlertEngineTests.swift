import XCTest
@testable import HeadUpCore

final class AlertEngineTests: XCTestCase {
    func testDangerActivatesAtTwoSecondsAndDoesNotRepeatWhileActive() {
        let engine = AlertEngine()
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 0).warningActive)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 1_999).warningActive)
        let activation = engine.process(metrics: danger, monotonicTimeMs: 2_000, alarmEnabled: true)
        XCTAssertTrue(activation.warningActive)
        XCTAssertEqual(activation.haptic, .danger)
        XCTAssertTrue(activation.shouldPlaySound)
        let continuing = engine.process(metrics: danger, monotonicTimeMs: 6_000, alarmEnabled: true)
        XCTAssertTrue(continuing.warningActive)
        XCTAssertNil(continuing.haptic)
        XCTAssertFalse(continuing.shouldPlaySound)
    }

    func testBriefDangerDoesNotActivateAndRecoveryRestartsTimer() {
        let engine = AlertEngine()
        _ = engine.process(metrics: danger, monotonicTimeMs: 0)
        XCTAssertFalse(engine.process(metrics: warning, monotonicTimeMs: 1_999).warningActive)
        _ = engine.process(metrics: danger, monotonicTimeMs: 2_000)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 3_999).warningActive)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 4_000).warningActive)
        XCTAssertFalse(engine.process(metrics: safe, monotonicTimeMs: 4_001).warningActive)
    }

    func testMissingPoseCancelsWarningAndDangerContinuity() {
        let engine = AlertEngine()
        _ = engine.process(metrics: danger, monotonicTimeMs: 0)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 2_000).warningActive)
        XCTAssertEqual(engine.process(metrics: nil, monotonicTimeMs: 2_001), AlertOutput())
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 6_000).warningActive)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 7_999).warningActive)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 8_000).warningActive)
    }

    func testRapidFallFiresOnRisingEdgeAndHonorsCooldown() {
        let engine = AlertEngine()
        XCTAssertEqual(engine.process(metrics: rapidFall, monotonicTimeMs: 0).haptic, .rapidFall)
        XCTAssertNil(engine.process(metrics: rapidFall, monotonicTimeMs: 3_000).haptic)
        _ = engine.process(metrics: safe, monotonicTimeMs: 3_001)
        XCTAssertEqual(engine.process(metrics: rapidFall, monotonicTimeMs: 3_002).haptic, .rapidFall)
        _ = engine.process(metrics: safe, monotonicTimeMs: 3_003)
        XCTAssertNil(engine.process(metrics: rapidFall, monotonicTimeMs: 4_501).haptic)
        _ = engine.process(metrics: safe, monotonicTimeMs: 4_502)
        XCTAssertEqual(engine.process(metrics: rapidFall, monotonicTimeMs: 4_503).haptic, .rapidFall)
    }

    func testHapticCooldownIsSharedBetweenRapidFallAndDanger() {
        let engine = AlertEngine()
        _ = engine.process(metrics: danger, monotonicTimeMs: 0)
        let fallingDanger = PostureMetrics(angleDegrees: 30, zone: .danger, timestampMs: 0, isRapidFall: true)
        XCTAssertEqual(engine.process(metrics: fallingDanger, monotonicTimeMs: 1_000).haptic, .rapidFall)
        let output = engine.process(metrics: danger, monotonicTimeMs: 2_000, alarmEnabled: true)
        XCTAssertTrue(output.warningActive)
        XCTAssertNil(output.haptic)
        XCTAssertTrue(output.shouldPlaySound)
    }

    func testAlarmDisabledAndSoundCooldown() {
        let engine = AlertEngine()
        _ = engine.process(metrics: danger, monotonicTimeMs: 0)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 2_000).shouldPlaySound)
        _ = engine.process(metrics: safe, monotonicTimeMs: 2_001)
        _ = engine.process(metrics: danger, monotonicTimeMs: 2_002)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 4_002, alarmEnabled: true).shouldPlaySound)
        _ = engine.process(metrics: safe, monotonicTimeMs: 4_003)
        _ = engine.process(metrics: danger, monotonicTimeMs: 4_004)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 6_004, alarmEnabled: true).shouldPlaySound)
        _ = engine.process(metrics: safe, monotonicTimeMs: 6_005)
        _ = engine.process(metrics: danger, monotonicTimeMs: 6_006)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 8_006, alarmEnabled: true).shouldPlaySound)
    }

    func testResetCancelsWarningAndKeepsCooldown() {
        let engine = AlertEngine()
        XCTAssertEqual(engine.process(metrics: rapidFall, monotonicTimeMs: 0).haptic, .rapidFall)
        XCTAssertEqual(engine.reset(), AlertOutput())
        XCTAssertNil(engine.process(metrics: rapidFall, monotonicTimeMs: 1_000).haptic)
        engine.reset()
        XCTAssertEqual(engine.process(metrics: rapidFall, monotonicTimeMs: 1_500).haptic, .rapidFall)
    }

    func testClockRegressionRestartsContinuity() {
        let engine = AlertEngine()
        _ = engine.process(metrics: danger, monotonicTimeMs: 10_000)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 12_000).warningActive)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 100).warningActive)
        XCTAssertFalse(engine.process(metrics: danger, monotonicTimeMs: 2_099).warningActive)
        XCTAssertTrue(engine.process(metrics: danger, monotonicTimeMs: 2_100).warningActive)
    }

    private var safe: PostureMetrics { PostureMetrics(angleDegrees: 5, zone: .safe, timestampMs: 0) }
    private var warning: PostureMetrics { PostureMetrics(angleDegrees: 20, zone: .warning, timestampMs: 0) }
    private var danger: PostureMetrics { PostureMetrics(angleDegrees: 30, zone: .danger, timestampMs: 0) }
    private var rapidFall: PostureMetrics { PostureMetrics(angleDegrees: 23, zone: .warning, timestampMs: 0, isRapidFall: true) }
}
