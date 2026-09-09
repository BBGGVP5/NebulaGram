import Foundation
import XCTest
@testable import NebulaSettingsContract

final class SettingsStoreTests: XCTestCase {
    private func withDefaults(_ body: (UserDefaults) throws -> Void) rethrows {
        let name = "NebulaSettingsTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        try body(defaults)
    }

    func testDefaultDoesNotWriteAndTogglePersists() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.hideTabCounters)
            XCTAssertNil(defaults.object(forKey: NebulaSettingsStore.storageKey))
            try store.set(.boolean(true), for: "hide_tab_counters")
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).hideTabCounters)
            try store.set(.boolean(false), for: "hide_tab_counters")
            XCTAssertFalse(NebulaSettingsStore(defaults: defaults).hideTabCounters)
        }
    }

    func testObservationAndCancellation() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            var calls = 0
            var observation: SettingsObservation? = store.observe(queue: nil) { calls += 1 }
            try store.set(.boolean(true), for: "hide_tab_counters")
            try store.set(.boolean(true), for: "hide_tab_counters")
            XCTAssertEqual(calls, 1)
            observation?.cancel()
            observation?.cancel()
            observation = nil
            try store.set(.boolean(false), for: "hide_tab_counters")
            XCTAssertEqual(calls, 1)
            var automatic: SettingsObservation? = store.observe(queue: nil) { calls += 1 }
            XCTAssertNotNil(automatic)
            automatic = nil
            try store.set(.boolean(true), for: "hide_tab_counters")
            XCTAssertEqual(calls, 1)
        }
    }

    func testImportRetainsPendingKeysAndRejectsInvalidAtomically() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            let valid = try JSONEncoder().encode(SettingsDocument(settings: [
                "hide_tab_counters": .boolean(true), "centered_chat_header": .boolean(false)
            ]))
            XCTAssertEqual(try store.importData(valid), ["centered_chat_header"])
            let before = defaults.data(forKey: NebulaSettingsStore.storageKey)
            let invalid = try JSONEncoder().encode(SettingsDocument(settings: [
                "hide_tab_counters": .boolean(false), "avatar_round": .integer(9999)
            ]))
            XCTAssertThrowsError(try store.importData(invalid))
            XCTAssertThrowsError(try store.importData(Data("{\"format\":\"NebulaGram-settings\",\"version\":2,\"settings\":{}}".utf8)))
            XCTAssertThrowsError(try store.set(.integer(1), for: "hide_tab_counters"))
            XCTAssertThrowsError(try store.set(.boolean(true), for: "centered_chat_header"))
            XCTAssertThrowsError(try store.set(.boolean(true), for: "unknown"))
            XCTAssertEqual(before, defaults.data(forKey: NebulaSettingsStore.storageKey))
            XCTAssertTrue(store.hideTabCounters)
            let exported = try JSONDecoder().decode(SettingsDocument.self, from: store.exportData())
            XCTAssertEqual(exported.settings.count, 2)
            XCTAssertEqual(exported.settings["centered_chat_header"], .boolean(false))
        }
    }

    func testCorruptionIsNotOverwrittenAndExplicitImportRecovers() throws {
        try withDefaults { defaults in
            let corrupt = Data("not settings".utf8)
            defaults.set(corrupt, forKey: NebulaSettingsStore.storageKey)
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.hasLoadError)
            XCTAssertFalse(store.hideTabCounters)
            XCTAssertThrowsError(try store.set(.boolean(true), for: "hide_tab_counters"))
            XCTAssertThrowsError(try store.exportData())
            XCTAssertEqual(defaults.data(forKey: NebulaSettingsStore.storageKey), corrupt)
            try store.importData(JSONEncoder().encode(SettingsDocument(settings: ["hide_tab_counters": .boolean(true)])))
            XCTAssertFalse(store.hasLoadError)
            XCTAssertTrue(store.hideTabCounters)
        }
    }

    func testMissingCatalogDoesNotAllowWrites() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults, catalog: nil)
            XCTAssertTrue(store.hasLoadError)
            XCTAssertThrowsError(try store.set(.boolean(true), for: "hide_tab_counters"))
            XCTAssertThrowsError(try store.importData(JSONEncoder().encode(SettingsDocument(settings: [:]))))
            XCTAssertNil(defaults.object(forKey: NebulaSettingsStore.storageKey))
        }
    }
}
