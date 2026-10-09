import XCTest
@testable import NebulaSettingsContract

final class NebulaSelectionPolicyTests: XCTestCase {
    func testLoadedSelectionCapAndIdempotence() {
        let existing: Set<Int> = [300, 301]
        let loaded = Array(0..<200) + Array(0..<20)
        let selected = NebulaSelectionPolicy.addingLoaded(loaded, to: existing)
        XCTAssertEqual(selected.count, 100)
        XCTAssertTrue(selected.isSuperset(of: existing))
        XCTAssertEqual(NebulaSelectionPolicy.addingLoaded(loaded, to: selected), selected)
        XCTAssertEqual(NebulaSelectionPolicy.addingLoaded([1, 1, 2], to: [1]), [1, 2])
        XCTAssertEqual(NebulaSelectionPolicy.addingLoaded([], to: existing), existing)
        let large = Set(0..<120)
        XCTAssertEqual(NebulaSelectionPolicy.addingLoaded([500], to: large), large)
    }
}
