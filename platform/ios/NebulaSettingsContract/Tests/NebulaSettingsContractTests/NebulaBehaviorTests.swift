import XCTest
@testable import NebulaSettingsContract

final class NebulaBehaviorTests: XCTestCase {
    func testAccountIsolationPersistenceAndDefaults() {
        let suite = "NebulaBehaviorTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = NebulaBehaviorPreferences(defaults: defaults)
        XCTAssertTrue(prefs.enabled("custom_chat_wallpaper", account: "a"))
        for key in ["mute_non_contacts", "ignore_mentions", "quote_full_reply", "biometric_delete"] {
            XCTAssertFalse(prefs.enabled(key, account: "a"))
            prefs.set(key, account: "a", value: true)
            XCTAssertFalse(prefs.enabled(key, account: "b"))
        }
        prefs.setSavedTarget(account: "a", peer: 42)
        prefs.setIgnoredMentionPeers(account: "a", peers: [42, 43])
        let restored = NebulaBehaviorPreferences(defaults: defaults)
        XCTAssertEqual(restored.savedTarget(account: "a"), 42)
        XCTAssertNil(restored.savedTarget(account: "b"))
        XCTAssertTrue(restored.ignoreMention(account: "a", peer: 42))
        XCTAssertFalse(restored.ignoreMention(account: "a", peer: 44))
        XCTAssertFalse(restored.ignoreMention(account: "b", peer: 42))
        restored.setIgnoredMentionPeers(account: "a", peers: [])
        XCTAssertFalse(restored.ignoreMention(account: "a", peer: 42))
        restored.setIgnoredMentionPeers(account: "a", peers: nil)
        XCTAssertTrue(restored.ignoreMention(account: "a", peer: 44))
        restored.setSavedTarget(account: "a", peer: nil)
        XCTAssertNil(restored.savedTarget(account: "a"))
        restored.set("ignore_mentions", account: "a", value: false)
        XCTAssertFalse(restored.ignoreMention(account: "a", peer: 44))
    }
    func testOnlyUnknownPrivatePeersAreSilenced() {
        for enabled in [false, true] {
            for isPrivate in [false, true] {
                for contact in [false, true] {
                    for service in [false, true] {
                        XCTAssertEqual(NebulaBehaviorPreferences.silenceUnknown(enabled: enabled, isPrivate: isPrivate, contact: contact, serviceOrSelf: service), enabled && isPrivate && !contact && !service)
                    }
                }
            }
        }
    }
    func testSmoothFadeIsGlobalPersistentAndBounded() {
        let suite = "NebulaBehaviorTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = NebulaBehaviorPreferences(defaults: defaults)
        XCTAssertFalse(prefs.smoothFade)
        prefs.setSmoothFade(true)
        XCTAssertTrue(NebulaBehaviorPreferences(defaults: defaults).smoothFade)
        XCTAssertEqual(NebulaBehaviorPreferences.fade(-1, enabled: true), 0)
        XCTAssertEqual(NebulaBehaviorPreferences.fade(2, enabled: true), 1)
        XCTAssertEqual(NebulaBehaviorPreferences.fade(0.5, enabled: true), 0.5)
        XCTAssertEqual(NebulaBehaviorPreferences.fade(0.2, enabled: false), 0.2)
        var previous = 0.0
        for step in 0...100 {
            let next = NebulaBehaviorPreferences.fade(Double(step) / 100, enabled: true)
            XCTAssertGreaterThanOrEqual(next, previous); previous = next
        }
    }
}
