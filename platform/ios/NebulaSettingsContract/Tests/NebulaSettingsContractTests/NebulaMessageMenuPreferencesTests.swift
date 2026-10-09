import XCTest
@testable import NebulaSettingsContract

final class NebulaMessageMenuPreferencesTests: XCTestCase {
    func testVisibilityDoesNotHideRequiredOrUnknownNativeActions() {
        let suite = "MenuTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = NebulaMessageMenuPreferences(defaults: defaults)
        for action in NebulaMessageMenuAction.allCases {
            XCTAssertTrue(prefs.visible(action)); prefs.setVisible(action, false)
            XCTAssertFalse(prefs.visible(identifier: action.identifier))
        }
        for id: String? in [nil, "edit", "delete", "select", "unrecognized", "nebula.message.delete"] { XCTAssertTrue(prefs.visible(identifier: id)) }
        let restored = NebulaMessageMenuPreferences(defaults: defaults)
        XCTAssertFalse(restored.visible(.reply)); XCTAssertFalse(restored.compact)
        prefs.setCompact(true); XCTAssertTrue(restored.compact)
    }
}
