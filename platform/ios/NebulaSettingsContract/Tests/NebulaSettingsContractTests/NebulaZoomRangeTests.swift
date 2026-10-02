import XCTest
@testable import NebulaSettingsContract

final class NebulaZoomRangeTests: XCTestCase {
    func testVirtualCameraUsesWideAngleAsOneAndKeepsOpticalStops() {
        let range = NebulaZoomRange(neutral: 2, minimumDeviceFactor: 1, maximumDeviceFactor: 60)
        XCTAssertEqual(range.minimum, 0.5)
        XCTAssertEqual(range.maximum, 30)
        XCTAssertEqual(range.presets(deviceStops: [2, 10]), [0.5, 1, 2, 5, 30])
        XCTAssertEqual(range.deviceFactor(1), 2)
        XCTAssertEqual(range.deviceFactor(5), 10)
        XCTAssertEqual(range.displayed(10), 5)
    }
    func testFrontDeviceRangeIsIndependentAndNotArtificiallyCapped() {
        let range = NebulaZoomRange(neutral: 1, minimumDeviceFactor: 1, maximumDeviceFactor: 2.5)
        XCTAssertEqual(range.presets(deviceStops: []), [1, 2, 2.5])
        XCTAssertEqual(range.deviceFactor(30), 2.5)
        XCTAssertEqual(range.deviceFactor(0.5), 1)
        let long = NebulaZoomRange(neutral: 1, minimumDeviceFactor: 1, maximumDeviceFactor: 125)
        XCTAssertEqual(long.maximum, 125)
    }
    func testInvalidValuesAndSingleStopRemainFinite() {
        let range = NebulaZoomRange(neutral: .nan, minimumDeviceFactor: .infinity, maximumDeviceFactor: -.infinity)
        XCTAssertEqual(range.presets(deviceStops: [.nan, .infinity, 1]), [1])
        XCTAssertEqual(range.deviceFactor(.nan), 1)
        XCTAssertEqual(range.displayed(.infinity), 1)
    }
}
