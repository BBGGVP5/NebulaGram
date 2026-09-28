import Foundation
import XCTest
@testable import NebulaSettingsContract

final class SettingsStoreTests: XCTestCase {
    func testAttachmentCameraPreferencePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.hideAttachCamera)
            try store.set(.boolean(true), for: "hide_attach_camera")
            XCTAssertTrue(store.hideAttachCamera)
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).hideAttachCamera)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("hide_attach_camera"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "hide_attach_camera"))
        }
    }

    func testNativeNavigationControlsPersistAndKeepSettingsReachable() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.showProfileTab)
            XCTAssertTrue(store.showSettingsTab)
            XCTAssertTrue(store.showTabLabels)
              XCTAssertFalse(store.compactBottomBar)
              XCTAssertFalse(store.hideSendAs)
              XCTAssertTrue(store.centeredChatHeader)
              XCTAssertFalse(store.disableNextChannel)
              XCTAssertFalse(store.secondsInTime)
            try store.set(.boolean(false), for: "bottom_bar_settings")
            XCTAssertFalse(store.showSettingsTab)
            try store.set(.boolean(false), for: "bottom_bar_profile")
            XCTAssertTrue(store.showSettingsTab)
            try store.set(.boolean(false), for: "tab_labels")
            try store.set(.boolean(true), for: "compact_bottom_bar")
            try store.set(.boolean(true), for: "hide_home_camera")
              try store.set(.boolean(true), for: "hide_home_compose")
              try store.set(.boolean(true), for: "hide_send_as")
              try store.set(.boolean(false), for: "centered_chat_header")
              try store.set(.boolean(true), for: "disable_next_channel")
              try store.set(.boolean(true), for: "seconds_in_time")
            let restored = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(restored.showTabLabels)
            XCTAssertTrue(restored.compactBottomBar)
            XCTAssertTrue(restored.hideHomeCamera)
              XCTAssertTrue(restored.hideHomeCompose)
              XCTAssertTrue(restored.hideSendAs)
              XCTAssertFalse(restored.centeredChatHeader)
              XCTAssertTrue(restored.disableNextChannel)
              XCTAssertTrue(restored.secondsInTime)
            let preview = try store.previewImport(store.exportData())
            XCTAssertTrue(preview.activeKeys.contains("bottom_bar_profile"))
              XCTAssertTrue(preview.activeKeys.contains("hide_home_camera"))
              XCTAssertTrue(preview.activeKeys.contains("hide_send_as"))
              XCTAssertTrue(preview.activeKeys.contains("centered_chat_header"))
              XCTAssertTrue(preview.activeKeys.contains("disable_next_channel"))
              XCTAssertTrue(preview.activeKeys.contains("seconds_in_time"))
        }
    }
    private func withDefaults(_ body: (UserDefaults) throws -> Void) rethrows {
        let name = "NebulaSettingsTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        try body(defaults)
    }

    func testNativeTransitionChoicePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(store.transitionStyle, 0)
            try store.set(.integer(2), for: "fragment_transition_style")
            XCTAssertEqual(store.transitionStyle, 2)
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).transitionStyle, 2)
            let exported = try store.exportData()
            XCTAssertTrue(try store.previewImport(exported).activeKeys.contains("fragment_transition_style"))
            XCTAssertThrowsError(try store.set(.integer(3), for: "fragment_transition_style"))
            XCTAssertEqual(store.transitionStyle, 2)
        }
    }

    func testWidePostsToggleImportAndReset() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.widePosts)
            var notifications = 0
            let observation = store.observe(queue: nil) { notifications += 1 }
            defer { observation.cancel() }
            try store.set(.boolean(true), for: "wide_posts")
            XCTAssertTrue(store.widePosts)
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).widePosts)
            XCTAssertEqual(notifications, 1)
            try store.set(.boolean(true), for: "wide_posts")
            XCTAssertEqual(notifications, 1)
            let exported = try store.exportData()
            XCTAssertTrue(try store.previewImport(exported).activeKeys.contains("wide_posts"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "wide_posts"))
            XCTAssertTrue(store.widePosts)
            try store.importData(JSONEncoder().encode(SettingsDocument(settings: [:])))
            XCTAssertFalse(store.widePosts)
            try store.importData(exported)
            XCTAssertTrue(store.widePosts)
            XCTAssertEqual(notifications, 3)
        }
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
                "hide_tab_counters": .boolean(true), "adaptive_chat_header": .boolean(false)
            ]))
            XCTAssertEqual(try store.importData(valid), ["adaptive_chat_header"])
            let before = defaults.data(forKey: NebulaSettingsStore.storageKey)
            let invalid = try JSONEncoder().encode(SettingsDocument(settings: [
                "hide_tab_counters": .boolean(false), "avatar_round": .integer(9999)
            ]))
            XCTAssertThrowsError(try store.importData(invalid))
            XCTAssertThrowsError(try store.importData(Data("{\"format\":\"NebulaGram-settings\",\"version\":2,\"settings\":{}}".utf8)))
            XCTAssertThrowsError(try store.set(.integer(1), for: "hide_tab_counters"))
            XCTAssertThrowsError(try store.set(.boolean(true), for: "adaptive_chat_header"))
            XCTAssertThrowsError(try store.set(.boolean(true), for: "unknown"))
            XCTAssertEqual(before, defaults.data(forKey: NebulaSettingsStore.storageKey))
            XCTAssertTrue(store.hideTabCounters)
            let exported = try JSONDecoder().decode(SettingsDocument.self, from: store.exportData())
            XCTAssertEqual(exported.settings.count, 2)
            XCTAssertEqual(exported.settings["adaptive_chat_header"], .boolean(false))
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
