import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaMessagePreferencesTests: XCTestCase {
    func testNativeDefaultsAndRoundTrip() {
        let suite = "MessagePreferencesTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = NebulaMessagePreferences(defaults: defaults)
        XCTAssertTrue(prefs.enabled("direct_share")); XCTAssertTrue(prefs.enabled("voice_autoplay"))
        XCTAssertFalse(prefs.enabled("edited_pencil")); XCTAssertFalse(prefs.enabled("forward_date"))
        XCTAssertFalse(prefs.enabled("hide_profile_phone")); XCTAssertFalse(prefs.enabled("profile_photo_dc"))
        prefs.set("voice_autoplay", false); prefs.set("hide_profile_phone", true)
        let restored = NebulaMessagePreferences(defaults: defaults)
        XCTAssertFalse(restored.enabled("voice_autoplay")); XCTAssertTrue(restored.enabled("hide_profile_phone"))
        for key in ["premium_effects", "reaction_effects"] { XCTAssertTrue(prefs.enabled(key)); prefs.set(key, false); XCTAssertFalse(restored.enabled(key)) }
        prefs.set("unknown", true); XCTAssertFalse(prefs.enabled("unknown"))
        XCTAssertEqual(prefs.seekInterval, 15)
        prefs.setSeekInterval(20); XCTAssertEqual(restored.seekInterval, 20)
        prefs.setSeekInterval(-1); XCTAssertEqual(prefs.seekInterval, 20)
        defaults.set(999, forKey: "nebula.messages.seek_interval"); XCTAssertEqual(prefs.seekInterval, 15)
        XCTAssertFalse(prefs.enabled("pause_background_video")); XCTAssertFalse(prefs.enabled("disable_message_effects"))
    }
}
