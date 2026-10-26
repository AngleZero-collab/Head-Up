import XCTest
@testable import HeadUp

@MainActor
final class PictureInPictureTests: XCTestCase {
    func testUnsupportedCameraDoesNotEnterBackgroundHandoff() {
        let window = PosturePictureInPicture()
        var transitions = 0
        window.stateChanged = { _ in transitions += 1 }
        window.start(cameraSupported: false, copy: HeadUpCopy(language: .traditionalChinese))
        XCTAssertEqual(window.state, .stopped)
        XCTAssertEqual(transitions, 0)
        XCTAssertTrue(window.errorMessage?.contains("不支援") == true)
        window.stop()
        window.stop()
        XCTAssertEqual(transitions, 0)
    }

    func testMissingSourceWindowCannotStartCameraHandoff() {
        let window = PosturePictureInPicture()
        window.start(cameraSupported: true, copy: HeadUpCopy(language: .english))
        XCTAssertEqual(window.state, .stopped)
        XCTAssertNotNil(window.errorMessage)
    }
}
