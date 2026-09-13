import Foundation
import XCTest
@testable import NebulaSettingsContract

final class ForwardEditingTests: XCTestCase {
    func testOptInPersistsAndDoesNotTouchArchiveOrAI() {
        let name = "NebulaForwardEditingTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let settings = NebulaForwardEditing(defaults: defaults)
        XCTAssertFalse(settings.enabled)
        settings.enabled = true
        XCTAssertTrue(NebulaForwardEditing(defaults: defaults).enabled)
        XCTAssertNil(defaults.object(forKey: "nebula.ai.enabled"))
        settings.enabled = false
        XCTAssertFalse(NebulaForwardEditing(defaults: defaults).enabled)
    }
}
