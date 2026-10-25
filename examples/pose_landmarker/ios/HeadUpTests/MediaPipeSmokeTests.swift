import XCTest
import UIKit
@testable import HeadUp

final class MediaPipeSmokeTests: XCTestCase {
    func testBundledFullModelDetectsPoseInReferenceImage() throws {
        let imageURL = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "test_image", withExtension: "jpg"))
        let image = try XCTUnwrap(UIImage(contentsOfFile: imageURL.path))
        let result = try CameraPipeline.detectImage(image)
        XCTAssertEqual(result.count, 33)
        XCTAssertTrue(result.allSatisfy { $0.x.isFinite && $0.y.isFinite && $0.z.isFinite })
    }
}
