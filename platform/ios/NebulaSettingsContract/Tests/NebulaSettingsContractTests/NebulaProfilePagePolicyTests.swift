import XCTest
@testable import NebulaSettingsContract

final class NebulaProfilePagePolicyTests: XCTestCase {
    typealias P = NebulaProfilePagePolicy

    func testSource() {
        XCTAssertEqual(P.source(style: true, background: true, photoBanner: true, hasPeerColor: true, hasPhoto: true, editing: false), .peerColor)
        XCTAssertEqual(P.source(style: true, background: false, photoBanner: true, hasPeerColor: true, hasPhoto: true, editing: false), .none)
        XCTAssertEqual(P.source(style: true, background: true, photoBanner: true, hasPeerColor: false, hasPhoto: true, editing: false), .photo)
        XCTAssertEqual(P.source(style: true, background: true, photoBanner: false, hasPeerColor: false, hasPhoto: true, editing: false), .none)
        XCTAssertEqual(P.source(style: false, background: true, photoBanner: true, hasPeerColor: true, hasPhoto: true, editing: false), .none)
        XCTAssertEqual(P.source(style: true, background: true, photoBanner: true, hasPeerColor: true, hasPhoto: true, editing: true), .none)
    }

    func testPhotoToneClampsLightnessAndSaturation() {
        let red = P.RGB(red: 1, green: 0, blue: 0)
        let (_, nightS, nightL) = P.hsl(P.photoTone(red, dark: true))
        XCTAssertEqual(nightS, 0.42, accuracy: 0.001)
        XCTAssertEqual(nightL, 0.22, accuracy: 0.001)
        let (_, dayS, dayL) = P.hsl(P.photoTone(red, dark: false))
        XCTAssertEqual(dayS, 0.38, accuracy: 0.001)
        XCTAssertEqual(dayL, 0.86, accuracy: 0.001)
        let black = P.photoTone(P.RGB(red: 0, green: 0, blue: 0), dark: true)
        XCTAssertEqual(black.red, 0.12, accuracy: 0.001)
    }

    func testHslRoundTrip() {
        let color = P.RGB(red: 0.2, green: 0.5, blue: 0.8)
        let (h, s, l) = P.hsl(color)
        let back = P.rgb(h, s, l)
        XCTAssertEqual(back.red, color.red, accuracy: 0.0001)
        XCTAssertEqual(back.green, color.green, accuracy: 0.0001)
        XCTAssertEqual(back.blue, color.blue, accuracy: 0.0001)
    }

    func testBottomBlendsTowardBase() {
        let page = P.RGB(red: 1, green: 0, blue: 0), base = P.RGB(red: 0, green: 0, blue: 0)
        XCTAssertEqual(P.bottom(page, base: base, dark: true).red, 0.7, accuracy: 0.0001)
        XCTAssertEqual(P.bottom(page, base: base, dark: false).red, 0.55, accuracy: 0.0001)
    }
}
