import XCTest
@testable import NebulaSettingsContract

final class NebulaAvatarGeometryTests: XCTestCase {
    func testSixShapesAndFractionalBounds() {
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 0, roundness: 100), [50,50,50,50])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 1, roundness: 100), [39,39,39,39])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 2, roundness: 100), [23,23,23,23])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 3, roundness: 100), [0,0,0,0])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 4, roundness: 100), [50,11,50,11])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 5, roundness: 100), [50,50,11,50])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 33.5, shape: 0, roundness: 100), [16.75,16.75,16.75,16.75])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 5, roundness: 0), [0,0,0,0])
    }
    func testForumsAndInvalidSizes() {
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: 100, shape: 5, roundness: 0, uniform: false, isForum: true), [25,25,25,25])
        XCTAssertEqual(NebulaAvatarGeometry.radii(side: .nan, shape: 3, roundness: 100), [0,0,0,0])
        for shape in -1...6 {
            let corners = NebulaAvatarGeometry.radii(side: 50, shape: shape, roundness: 200)
            XCTAssertTrue(corners.allSatisfy { $0 >= 0 && $0 <= 25 })
        }
    }
}
