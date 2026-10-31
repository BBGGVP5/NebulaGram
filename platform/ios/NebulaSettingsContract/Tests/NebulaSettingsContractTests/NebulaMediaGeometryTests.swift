import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaMediaGeometryTests: XCTestCase {
    func testIndependentValuesPersistAndTransfer() throws {
        let name = "NebulaMediaGeometryTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = NebulaSettingsStore(defaults: defaults)
        XCTAssertEqual(store.stickerScale, 1)
        XCTAssertEqual(store.emojiScale, 1)
        XCTAssertEqual(store.gifScale, 1)
        try store.set(.integer(150), for: "sticker_percent")
        try store.set(.integer(50), for: "emoji_percent")
        try store.set(.integer(80), for: "gif_percent")
        let restored = NebulaSettingsStore(defaults: defaults)
        XCTAssertEqual(restored.stickerScale, 1.5)
        XCTAssertEqual(restored.emojiScale, 0.5)
        XCTAssertEqual(restored.gifScale, 0.8)
        let data = try restored.exportData()
        try restored.set(.integer(100), for: "gif_percent")
        _ = try restored.importData(data)
        XCTAssertEqual(restored.gifPercent, 80)
        for key in ["sticker_percent", "emoji_percent", "gif_percent"] {
            XCTAssertThrowsError(try restored.set(.integer(49), for: key))
            XCTAssertThrowsError(try restored.set(.integer(151), for: key))
            XCTAssertThrowsError(try restored.set(.boolean(true), for: key))
        }
        XCTAssertEqual(restored.stickerPercent, 150)
    }

    func testNarrowLayoutCapsWidthAndRepeatedLayoutDoesNotAccumulateScale() {
        XCTAssertEqual(NebulaMediaGeometry.boundedScale(percent: 150, naturalWidth: 180, availableWidth: 200), 200.0 / 180)
        for _ in 0..<10 {
            XCTAssertEqual(NebulaMediaGeometry.boundedScale(percent: 50, naturalWidth: 180, availableWidth: 320), 0.5)
        }
        XCTAssertEqual(NebulaMediaGeometry.scale(-100), 0.5)
        XCTAssertEqual(NebulaMediaGeometry.scale(1000), 1.5)
        XCTAssertEqual(NebulaMediaGeometry.boundedScale(percent: 100, naturalWidth: .nan, availableWidth: 320), 1)
    }
}
