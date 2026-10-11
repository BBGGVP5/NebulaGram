import XCTest
@testable import NebulaSettingsContract

final class NebulaProfileBannerTests: XCTestCase {
    func testFadeEndsAtFractionalHeaderEdgeWithOnePhysicalPixel() {
        for width in [CGFloat(320), 393, 430, 768] {
            for bottom in [CGFloat(449.5), 474.5, 704.25] {
                for pixel in [CGFloat(1), 0.5, 1.0 / 3.0] {
                    let frame = NebulaProfileBannerPolicy.fadeFrame(width: width, bottom: bottom, protectedBottom: 186, pixel: pixel)
                    XCTAssertEqual(frame.width, width)
                    XCTAssertEqual(frame.maxY, bottom + pixel, accuracy: 0.00001)
                    XCTAssertEqual(frame.height - pixel, 148, accuracy: 0.00001)
                }
            }
        }
    }

    func testSmallAvatarIsProtectedAndCompactBoundsStayFinite() {
        let frame = NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: 250.25, protectedBottom: 186.5, pixel: 0.5)
        XCTAssertEqual(frame.minY, 186.5)
        XCTAssertEqual(frame.maxY, 250.75)
        XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: 88, protectedBottom: 186.5, pixel: 0.5), .zero)
        for invalid in [CGFloat.nan, CGFloat.infinity, -CGFloat.infinity] {
            XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: invalid, bottom: 500, protectedBottom: 0, pixel: 0.5), .zero)
            XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: invalid, protectedBottom: 0, pixel: 0.5), .zero)
            XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: 500, protectedBottom: invalid, pixel: 0.5), .zero)
            XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: 500, protectedBottom: 0, pixel: invalid), .zero)
        }
        XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: 0, bottom: 500, protectedBottom: 0, pixel: 0.5), .zero)
        XCTAssertEqual(NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: 0, protectedBottom: 0, pixel: 0.5), .zero)
    }

    func testOpeningAndClosingHaveNoMidpointSwitch() {
        for direction in [1, -1] {
            var previous: CGRect?
            for step in 0...100 {
                let phase = CGFloat(direction == 1 ? step : 100 - step) / 100.0
                let bottom = 430.5 + (704.25 - 430.5) * phase
                let frame = NebulaProfileBannerPolicy.fadeFrame(width: 393, bottom: bottom, protectedBottom: 186, pixel: 1.0 / 3.0)
                XCTAssertEqual(frame.maxY, bottom + 1.0 / 3.0, accuracy: 0.00001)
                if let previous {
                    XCTAssertLessThan(abs(frame.minY - previous.minY), 2.74)
                    XCTAssertEqual(frame.height, previous.height, accuracy: 0.00001)
                }
                previous = frame
            }
        }
    }

    func testStylePhotoAndEditingGates() {
        XCTAssertEqual(NebulaProfileBannerPolicy.opacity(style: true, photoBanner: true, hasPhoto: true, editing: false), 1)
        XCTAssertEqual(NebulaProfileBannerPolicy.opacity(style: false, photoBanner: true, hasPhoto: true, editing: false), 0)
        XCTAssertEqual(NebulaProfileBannerPolicy.opacity(style: true, photoBanner: false, hasPhoto: true, editing: false), 0)
        XCTAssertEqual(NebulaProfileBannerPolicy.opacity(style: true, photoBanner: true, hasPhoto: false, editing: false), 0)
        XCTAssertEqual(NebulaProfileBannerPolicy.opacity(style: true, photoBanner: true, hasPhoto: true, editing: true), 0)
    }
}
