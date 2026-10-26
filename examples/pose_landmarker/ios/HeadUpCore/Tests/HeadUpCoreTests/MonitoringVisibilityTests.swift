import XCTest
@testable import HeadUpCore

final class MonitoringVisibilityTests: XCTestCase {
    func testForegroundAllowsCaptureWithoutPictureInPicture() {
        let visibility = MonitoringVisibility()
        XCTAssertTrue(visibility.canProcessFrames)
        XCTAssertTrue(visibility.shouldKeepCapture)
    }

    func testLeavingForegroundWithoutPictureInPicturePausesCapture() {
        var visibility = MonitoringVisibility()
        visibility.scene = .inactive
        XCTAssertFalse(visibility.canProcessFrames)
        visibility.scene = .background
        XCTAssertFalse(visibility.shouldKeepCapture)
    }

    func testPictureInPictureHandoffKeepsCaptureAcrossSceneTransitions() {
        var visibility = MonitoringVisibility(pictureInPicture: .starting)
        visibility.scene = .inactive
        XCTAssertTrue(visibility.shouldKeepCapture)
        visibility.scene = .background
        XCTAssertTrue(visibility.canProcessFrames)
        visibility.pictureInPicture = .active
        XCTAssertTrue(visibility.canProcessFrames)
    }

    func testFailedHandoffOrClosedPictureInPictureStopsBackgroundCapture() {
        for state: MonitoringVisibility.PictureInPicture in [.starting, .active] {
            var visibility = MonitoringVisibility(scene: .background, pictureInPicture: state)
            visibility.pictureInPicture = .stopped
            XCTAssertFalse(visibility.canProcessFrames)
            XCTAssertFalse(visibility.shouldKeepCapture)
        }
    }

    func testReturningToForegroundAllowsCaptureAfterPictureInPictureCloses() {
        var visibility = MonitoringVisibility(scene: .background, pictureInPicture: .active)
        visibility.scene = .active
        visibility.pictureInPicture = .stopped
        XCTAssertTrue(visibility.shouldKeepCapture)
    }

    func testLockBlocksAllVisibilityStatesIncludingActivePictureInPicture() {
        for scene: MonitoringVisibility.Scene in [.active, .inactive, .background] {
            for pictureInPicture: MonitoringVisibility.PictureInPicture in [.stopped, .starting, .active] {
                var visibility = MonitoringVisibility(scene: scene, pictureInPicture: pictureInPicture)
                visibility.protectedDataAvailable = false
                XCTAssertFalse(visibility.canProcessFrames)
                XCTAssertFalse(visibility.shouldKeepCapture)
            }
        }
    }

    func testUnlockDoesNotResumeOrdinaryBackgroundCapture() {
        var visibility = MonitoringVisibility(scene: .background, protectedDataAvailable: false)
        visibility.protectedDataAvailable = true
        XCTAssertFalse(visibility.shouldKeepCapture)
    }
}
