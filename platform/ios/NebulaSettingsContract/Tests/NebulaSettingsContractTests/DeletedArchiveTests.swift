import Foundation
import XCTest
@testable import NebulaSettingsContract

final class DeletedArchiveTests: XCTestCase {
    func testAdaptiveGlassPolicy() throws {
        for mode in 0...2 { for mask in 0..<8 {
            let power = mask & 1 != 0, hot = mask & 2 != 0, accessibility = mask & 4 != 0
            XCTAssertEqual(NebulaGlassPolicy.reduced(mode: mode, lowPower: power, hot: hot, reduceTransparency: accessibility), accessibility || mode == 2 || (mode == 0 && (power || hot)))
        } }
    }
    func testPolicyAllCombinations() {
        for mask in 0..<512 {
            let b = (0..<9).map { mask & (1 << $0) != 0 }
            let expected = b[0] && b[6] && !b[7] && b[8] && !b[5] && (!b[1] || b[3]) && (!b[2] || b[4])
            XCTAssertEqual(NebulaRetentionPolicy.allowed(enabled: b[0], secret: b[1], expiring: b[2], saveSecret: b[3], saveExpiring: b[4], protectedContent: b[5], incoming: b[6], service: b[7], validId: b[8]), expected)
        }
    }
    func testEncryptedStorageScopeDefaultsAndOwnIcon() throws {
        let suite = "NebulaDeleted.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(suite)
        defer { defaults.removePersistentDomain(forName: suite);try? FileManager.default.removeItem(at: folder) }
        let archive = NebulaDeletedArchive(defaults: defaults, directory: folder, key: Data(repeating: 7, count: 32))
        XCTAssertFalse(archive.enabled(account: 1));XCTAssertFalse(archive.saveSecret(account: 1));XCTAssertFalse(archive.saveExpiring(account: 1))
        archive.setEnabled(account: 1, value: true);archive.setSaveSecret(account: 1, value: true)
        XCTAssertFalse(archive.enabled(account: 2));XCTAssertFalse(archive.saveSecret(account: 2))
        archive.setExcluded(account: 1, peer: 42, value: true)
        XCTAssertTrue(archive.excluded(account: 1, peer: 42)); XCTAssertFalse(archive.excluded(account: 2, peer: 42))
        archive.setExcluded(account: 1, peer: 42, value: false); XCTAssertFalse(archive.excluded(account: 1, peer: 42))
        archive.setRetentionDays(account: 1, value: 1); XCTAssertEqual(archive.retentionDays(account: 1), 1)
        archive.setRetentionDays(account: 1, value: -1); XCTAssertEqual(archive.retentionDays(account: 1), 1)
        XCTAssertEqual(archive.retentionDays(account: 2), 7)
        XCTAssertTrue(archive.prunedForAccount([NebulaDeletedEntry(peer: 42, id: 1, timestamp: 1, deletedAt: 1, text: "old")], account: 1, now: 86402).isEmpty)
        archive.icon = " 👨‍👩‍👧‍👦 "
        XCTAssertEqual(archive.icon, "👨‍👩‍👧‍👦")
        let media = NebulaDeletedEntry(peer: 42, id: 5, timestamp: 50, text: "", namespace: 123)
        let text = NebulaDeletedEntry(peer: 43, id: 6, timestamp: 51, text: "private received content")
        try archive.replace([media, text], account: 1)
        try archive.replace([text], account: 2)
        XCTAssertEqual(try archive.entries(account: 1), [media, text])
        let bytes = try Data(contentsOf: folder.appendingPathComponent("1.enc"))
        XCTAssertNil(bytes.range(of: Data("private received content".utf8)))
        archive.setEnabled(account: 1, value: false)
        XCTAssertEqual(try archive.entries(account: 1).count, 2)
        try archive.clear(account: 1)
        XCTAssertTrue(try archive.entries(account: 1).isEmpty)
        XCTAssertEqual(try archive.entries(account: 2), [text])
        // Authentication failure must surface, not silently replace an unreadable archive.
        var corrupt = try Data(contentsOf: folder.appendingPathComponent("2.enc"))
        corrupt[corrupt.count - 1] ^= 1
        try corrupt.write(to: folder.appendingPathComponent("2.enc"))
        XCTAssertThrowsError(try archive.entries(account: 2))
        XCTAssertEqual(try Data(contentsOf: folder.appendingPathComponent("2.enc")), corrupt)
    }
    func testRetentionBounds() {
        let now: TimeInterval = 1_000_000
        var entries = (0..<510).map { NebulaDeletedEntry(peer: 1, id: Int32($0 + 1), timestamp: 1, deletedAt: now - Double($0), text: "") }
        entries.append(NebulaDeletedEntry(peer: 1, id: 700, timestamp: 1, deletedAt: now - NebulaDeletedArchive.retention - 1, text: "old"))
        entries.append(NebulaDeletedEntry(peer: 1, id: 701, timestamp: 1, deletedAt: now + 100, text: "future"))
        let result = NebulaDeletedArchive.pruned(entries, now: now)
        XCTAssertEqual(result.count, 500);XCTAssertEqual(result.first?.id, 1);XCTAssertEqual(result.last?.id, 500)
    }
    func testNewNativeControlsPersist() throws {
        let suite = "NebulaControls.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = NebulaSettingsStore(defaults: defaults)
        XCTAssertTrue(store.showStories);XCTAssertTrue(store.settingsSearchHistory)
        try store.set(.boolean(false), for: "show_stories")
        try store.set(.boolean(false), for: "settings_search_history")
        let loaded = NebulaSettingsStore(defaults: defaults)
        XCTAssertFalse(loaded.showStories);XCTAssertFalse(loaded.settingsSearchHistory)
        try loaded.set(.boolean(false), for: "bottom_bar_contacts"); XCTAssertFalse(loaded.showContactsTab)
        try loaded.set(.string("settings,chats,contacts,profile"), for: "bottom_bar_order")
        XCTAssertEqual(loaded.bottomTabOrder, ["settings", "chats", "contacts", "profile"])
        try loaded.set(.string("chats,chats,settings,profile"), for: "bottom_bar_order")
        XCTAssertEqual(loaded.bottomTabOrder, ["chats", "contacts", "settings", "profile"])
        try loaded.set(.integer(2), for: "glass_quality")
        XCTAssertEqual(loaded.glassQuality, 2)
        XCTAssertThrowsError(try loaded.set(.integer(3), for: "glass_quality"))
        let exported = try JSONDecoder().decode(SettingsDocument.self, from: loaded.exportData())
        XCTAssertNil(exported.settings["show_stories"])
        try SettingsCatalog.bundled().validate(exported)
        try loaded.importData(JSONEncoder().encode(SettingsDocument(settings: ["glass_quality": .integer(1)])))
        XCTAssertFalse(loaded.showStories); XCTAssertEqual(loaded.glassQuality, 1)
    }
}
