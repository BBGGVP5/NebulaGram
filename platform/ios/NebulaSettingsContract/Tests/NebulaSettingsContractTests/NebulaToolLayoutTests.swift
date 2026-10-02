import XCTest
@testable import NebulaSettingsContract

final class NebulaToolLayoutTests: XCTestCase {
    func testThreeColumnsWithCenteredLastTwo() {
        let layout = NebulaToolLayout(width: 358, count: 5, minimumTileWidth: 100, rowHeight: 102)
        XCTAssertEqual(layout.tiles.count, 5)
        XCTAssertEqual(layout.height, 214)
        XCTAssertEqual(layout.tiles[0].y, layout.tiles[2].y)
        XCTAssertEqual(layout.tiles[3].x, 61.333333, accuracy: 0.001)
        XCTAssertEqual(layout.tiles[3].x, 358 - layout.tiles[4].x - layout.tiles[4].width, accuracy: 0.001)
    }
    func testLargeTextWrapsIntoTwoAndOneColumnsWithoutOverflow() {
        for width in [288.0, 358, 744] {
            for minimum in [100.0, 150, 300] {
                let layout = NebulaToolLayout(width: width, count: 5, minimumTileWidth: minimum, rowHeight: 160)
                for tile in layout.tiles {
                    XCTAssertGreaterThanOrEqual(tile.x, 0)
                    XCTAssertLessThanOrEqual(tile.x + tile.width, width + 0.001)
                    XCTAssertEqual(tile.height, 160)
                }
                for row in Set(layout.tiles.map(\.y)) {
                    let tiles = layout.tiles.filter { $0.y == row }
                    XCTAssertEqual(tiles.first!.x, width - tiles.last!.x - tiles.last!.width, accuracy: 0.001)
                }
            }
        }
    }
    func testRightToLeftMirrorsEachPartialRow() {
        let ltr = NebulaToolLayout(width: 358, count: 5, minimumTileWidth: 100, rowHeight: 96)
        let rtl = NebulaToolLayout(width: 358, count: 5, minimumTileWidth: 100, rowHeight: 96, rtl: true)
        for (left, right) in zip(ltr.tiles, rtl.tiles) {
            XCTAssertEqual(left.x, 358 - right.x - right.width, accuracy: 0.001)
            XCTAssertEqual(left.y, right.y)
        }
    }
    func testLanguagesPinShortcutsExactlyOnceAndKeepUnknownCodes() {
        let codes = NebulaLanguageOrder.codes(supported: ["en", "ru", "fr", "de", "fr", "", "zz"], locale: Locale(identifier: "ru"))
        XCTAssertEqual(Array(codes.prefix(2)), ["ru", "en"])
        XCTAssertEqual(codes.count, 5)
        XCTAssertEqual(Set(codes), ["ru", "en", "fr", "de", "zz"])
    }
}
