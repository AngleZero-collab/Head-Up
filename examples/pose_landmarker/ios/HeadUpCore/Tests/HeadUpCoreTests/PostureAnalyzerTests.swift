import Foundation
import XCTest
@testable import HeadUpCore

final class PostureAnalyzerTests: XCTestCase {
    private var analyzer: PostureAnalyzer!

    override func setUp() { analyzer = PostureAnalyzer() }

    // These first eleven cases reproduce the fixtures in Android's PostureAnalyzerTest.
    func testUprightPoseIsSafe() throws {
        let metrics = try analyze(samplePose(rawAngle: 5))
        XCTAssertEqual(metrics.zone, .safe)
        XCTAssertEqual(metrics.angleDegrees, 5)
        XCTAssertEqual(metrics.shoulderBalanceDegrees, 0)
    }

    func testTwentyFiveDegreesIsDanger() throws {
        let metrics = try analyze(samplePose(rawAngle: 25))
        XCTAssertEqual(metrics.angleDegrees, 25)
        XCTAssertEqual(metrics.zone, .danger)
    }

    func testEMATrustsFifteenPercentOfNewFrame() throws {
        _ = try analyze(samplePose(rawAngle: 0))
        let metrics = try analyze(samplePose(rawAngle: 60))
        XCTAssertEqual(metrics.angleDegrees, 9)
        XCTAssertEqual(metrics.zone, .safe)
    }

    func testRapidFallIsIndependentOfDangerThreshold() throws {
        _ = try analyze(samplePose(rawAngle: 20))
        let metrics = try analyze(samplePose(rawAngle: 40))
        XCTAssertEqual(metrics.angleDegrees, 23)
        XCTAssertEqual(metrics.zone, .warning)
        XCTAssertTrue(metrics.isRapidFall)
    }

    func testSlowMovementDoesNotTriggerRapidFall() throws {
        _ = try analyze(samplePose(rawAngle: 22))
        XCTAssertFalse(try analyze(samplePose(rawAngle: 23)).isRapidFall)
    }

    func testShoulderImbalance() throws {
        let metrics = try analyze(samplePose(rawAngle: 5, leftShoulderY: 0.76, rightShoulderY: 0.88))
        XCTAssertGreaterThanOrEqual(metrics.shoulderBalanceDegrees, 8)
    }

    func testOtherFacePointsWorkWithoutTrackedNose() throws {
        var points = samplePose(rawAngle: 5)
        points[0] = LandmarkPoint(x: points[0].x, y: points[0].y, z: points[0].z, visibility: 0, presence: 0)
        XCTAssertNotNil(analyzer.analyze(landmarks: points, timestampMs: 0))
    }

    func testMissingLandmarksReturnsNil() {
        XCTAssertNil(analyzer.analyze(landmarks: [], timestampMs: 0))
    }

    func testCalibrationMakesUprightPosePersonalZero() throws {
        let calibration = CalibrationProfile(angleDegrees: 18, postureRatio: 0.8, shoulderWidth: 0.6)
        let metrics = try analyze(samplePose(rawAngle: 18), calibration: calibration)
        XCTAssertEqual(metrics.relativeAngleDegrees, 0)
        XCTAssertEqual(metrics.zone, .safe)
    }

    func testCalibratedScreenDistanceDetectsMovingTooClose() throws {
        let calibration = CalibrationProfile(angleDegrees: 0, postureRatio: 0.8, shoulderWidth: 0.3)
        let metrics = try analyze(samplePose(rawAngle: 5), calibration: calibration)
        XCTAssertTrue(metrics.isTooClose)
        XCTAssertEqual(metrics.zone, .danger)
    }

    func testNeckCompressionRaisesRisk() throws {
        let calibration = CalibrationProfile(angleDegrees: 0, postureRatio: 0.82, shoulderWidth: 0.6)
        let metrics = try analyze(samplePose(rawAngle: 5, faceY: 0.55), calibration: calibration)
        XCTAssertGreaterThanOrEqual(metrics.neckFlexionDegrees, 25)
        XCTAssertEqual(metrics.zone, .danger)
    }

    func testAngleZoneBoundaries() throws {
        for (angle, zone) in [(14, PostureZone.safe), (15, .warning), (24, .warning), (25, .danger)] {
            XCTAssertEqual(PostureAnalyzer.zone(forAngleDegrees: angle), zone)
            analyzer.reset()
            XCTAssertEqual(try analyze(samplePose(rawAngle: Float(angle))).zone, zone)
        }
    }

    func testDistanceBoundariesAndClamping() throws {
        for (distance, expectedDistance, zone) in [
            (10, 15, PostureZone.danger), (24, 24, .danger), (25, 25, .warning),
            (34, 34, .warning), (35, 35, .safe), (45, 45, .safe), (150, 120, .safe)
        ] {
            analyzer.reset()
            let profile = CalibrationProfile(angleDegrees: 0, postureRatio: 0.8, shoulderWidth: Float(distance) * 0.6 / 45)
            let metrics = try analyze(samplePose(rawAngle: 0), calibration: profile)
            XCTAssertEqual(metrics.screenDistanceCm, expectedDistance)
            XCTAssertEqual(metrics.zone, zone)
            XCTAssertEqual(metrics.isTooClose, expectedDistance < 35)
        }
    }

    func testShoulderThresholdsUseUnroundedAngle() throws {
        for (degrees, zone) in [(7.99, PostureZone.safe), (8.01, .warning), (13.99, .warning), (14.01, .danger)] {
            analyzer.reset()
            let delta = Float(tan(degrees * .pi / 180)) * 0.6
            let metrics = try analyze(samplePose(rawAngle: 0, leftShoulderY: 0.8 - delta / 2, rightShoulderY: 0.8 + delta / 2))
            XCTAssertEqual(metrics.zone, zone, "shoulder angle \(degrees)")
        }
    }

    func testMissingPoseResetsFilterInsteadOfKeepingOldAngle() throws {
        _ = try analyze(samplePose(rawAngle: 60))
        XCTAssertNil(analyzer.analyze(landmarks: [], timestampMs: 1))
        let metrics = try analyze(samplePose(rawAngle: 0))
        XCTAssertEqual(metrics.rawAngleDegrees, 0)
        XCTAssertEqual(metrics.zone, .safe)
        XCTAssertFalse(metrics.isRapidFall)
    }

    func testResetClearsSmoothingAndRapidFallHistory() throws {
        _ = try analyze(samplePose(rawAngle: 0))
        analyzer.reset()
        let metrics = try analyze(samplePose(rawAngle: 60))
        XCTAssertEqual(metrics.angleDegrees, 60)
        XCTAssertFalse(metrics.isRapidFall)
    }

    func testUntrackedShoulderAndDegenerateWidthRejectPose() {
        var points = samplePose(rawAngle: 5)
        points[11] = LandmarkPoint(x: 0.2, y: 0.8, visibility: 0.19)
        XCTAssertNil(analyzer.analyze(landmarks: points, timestampMs: 0))
        points[11] = points[12]
        XCTAssertNil(analyzer.analyze(landmarks: points, timestampMs: 0))
    }

    func testInvalidCoordinatesAreRejectedWithoutCrashing() {
        var points = samplePose(rawAngle: 5)
        points[11] = LandmarkPoint(x: .nan, y: .infinity)
        XCTAssertNil(analyzer.analyze(landmarks: points, timestampMs: 0))
    }

    func testInvalidCalibrationFallsBackToUncalibratedAnalysis() throws {
        for angle in [Float.nan, -.greatestFiniteMagnitude, .infinity] {
            analyzer.reset()
            let profile = CalibrationProfile(angleDegrees: angle, postureRatio: 0.8, shoulderWidth: 0.6)
            let metrics = try analyze(samplePose(rawAngle: 5), calibration: profile)
            XCTAssertEqual(metrics.angleDegrees, 5)
            XCTAssertEqual(metrics.zone, .safe)
            XCTAssertNil(metrics.screenDistanceCm)
        }
    }

    func testNoCalibrationHasNoDistanceAndMotionIsMetadataOnly() throws {
        let metrics = try XCTUnwrap(analyzer.analyze(landmarks: samplePose(rawAngle: 5), deviceTiltDegrees: 82, isDeviceFlat: true, timestampMs: 123_456))
        XCTAssertNil(metrics.screenDistanceCm)
        XCTAssertEqual(metrics.zone, .safe)
        XCTAssertEqual(metrics.deviceTiltDegrees, 82)
        XCTAssertTrue(metrics.isDeviceFlat)
        XCTAssertEqual(metrics.timestampMs, 123_456)
    }

    func testPublicModelsRoundTripThroughJSON() throws {
        let profile = CalibrationProfile(angleDegrees: 18, postureRatio: 0.8, shoulderWidth: 0.6, calibratedAtMs: 123)
        let metrics = try analyze(samplePose(rawAngle: 5), calibration: profile)
        XCTAssertEqual(try JSONDecoder().decode(PostureMetrics.self, from: JSONEncoder().encode(metrics)), metrics)
        XCTAssertEqual(try JSONDecoder().decode(CalibrationProfile.self, from: JSONEncoder().encode(profile)), profile)
    }

    private func analyze(_ points: [LandmarkPoint], calibration: CalibrationProfile? = nil) throws -> PostureMetrics {
        try XCTUnwrap(analyzer.analyze(landmarks: points, calibration: calibration, timestampMs: 0))
    }

    private func samplePose(rawAngle: Float, faceY: Float = 0.32, leftShoulderY: Float = 0.8, rightShoulderY: Float = 0.8) -> [LandmarkPoint] {
        let shoulderCenterY = (leftShoulderY + rightShoulderY) / 2
        let faceZ = -Float(tan(Double(rawAngle) * .pi / 180)) * (shoulderCenterY - faceY)
        func face(_ x: Float, _ y: Float) -> LandmarkPoint { LandmarkPoint(x: x, y: y, z: faceZ) }
        var points = Array(repeating: LandmarkPoint(x: 0.5, y: 0.5, visibility: 0, presence: 0), count: 33)
        points[0] = face(0.5, faceY)
        points[1] = face(0.42, faceY - 0.03)
        points[2] = face(0.40, faceY - 0.03)
        points[3] = face(0.38, faceY - 0.03)
        points[4] = face(0.58, faceY - 0.03)
        points[5] = face(0.60, faceY - 0.03)
        points[6] = face(0.62, faceY - 0.03)
        points[7] = face(0.34, faceY - 0.02)
        points[8] = face(0.66, faceY - 0.02)
        points[9] = face(0.45, faceY + 0.06)
        points[10] = face(0.55, faceY + 0.06)
        points[11] = LandmarkPoint(x: 0.2, y: leftShoulderY)
        points[12] = LandmarkPoint(x: 0.8, y: rightShoulderY)
        points[23] = LandmarkPoint(x: 0.3, y: 1.2)
        points[24] = LandmarkPoint(x: 0.7, y: 1.2)
        return points
    }
}
