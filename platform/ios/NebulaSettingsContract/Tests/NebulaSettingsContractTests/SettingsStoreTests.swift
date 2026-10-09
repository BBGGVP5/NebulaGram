import Foundation
import XCTest
@testable import NebulaSettingsContract

final class SettingsStoreTests: XCTestCase {
    func testGlassMasterAndStyleRemainConsistentAcrossRestart() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            try store.set(.integer(2), for: "ios_glass_style")
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).iosGlassStyle, 2)
            try store.set(.boolean(false), for: "glass_custom")
            XCTAssertEqual(store.iosGlassStyle, 0)
            try store.set(.integer(1), for: "ios_glass_style")
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).iosGlassStyle, 1)
            try store.set(.boolean(true), for: "glass_haptics")
            try store.set(.integer(60), for: "glass_haptic_strength")
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).glassHaptics)
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).glassHapticStrength, 60)
            let export = try store.previewImport(store.exportData())
            XCTAssertFalse(export.document.settings.keys.contains("glass_custom"))
            XCTAssertFalse(export.document.settings.keys.contains("glass_haptic_strength"))
        }
    }

    func testAppearanceAndNativeDefaultsRemainEditableAfterTransfer() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.messageMenuBlur)
            XCTAssertTrue(store.floatingChatHeader)
            XCTAssertEqual(store.iconPack, 1)
            try store.set(.integer(2), for: "icon_pack")
            try store.set(.integer(3), for: "switch_style")
            try store.set(.boolean(false), for: "login_style")
            try store.set(.boolean(false), for: "bottom_bar")
            let exported = try store.exportData()
            try store.importData(exported)
            let restored = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(restored.iconPack, 2)
            XCTAssertEqual(restored.switchStyle, 3)
            XCTAssertFalse(restored.loginStyle)
            XCTAssertFalse(restored.showBottomBar)
            XCTAssertThrowsError(try store.set(.integer(4), for: "switch_style"))
            XCTAssertThrowsError(try store.set(.integer(4), for: "icon_pack"))
            try store.set(.integer(3), for: "icon_pack")
            XCTAssertEqual(store.iconPack, 0)
            let importedPack = try JSONDecoder().decode(SettingsDocument.self, from: store.exportData())
            XCTAssertEqual(importedPack.settings["icon_pack"], .integer(3))
        }
    }

    func testFolderStylePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(store.folderStyle, 0)
            try store.set(.integer(2), for: "folder_style")
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).folderStyle, 2)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("folder_style"))
            XCTAssertThrowsError(try store.set(.integer(3), for: "folder_style"))
        }
    }

    func testProfilePremiumStatusPreferencePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.hidePremiumStatus)
            try store.set(.boolean(true), for: "hide_premium_status")
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).hidePremiumStatus)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("hide_premium_status"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "hide_premium_status"))
        }
    }

    func testGlassDepthPersistsLocallyWithoutTransfer() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(store.glassDepth, 35)
            XCTAssertTrue(store.glassDepthEnabled)
            try store.set(.integer(72), for: "glass_depth")
            try store.set(.boolean(false), for: "glass_depth_enabled")
            let restored = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(restored.glassDepth, 72)
            XCTAssertFalse(restored.glassDepthEnabled)
            let preview = try store.previewImport(store.exportData())
            XCTAssertFalse(preview.activeKeys.contains("glass_depth"))
            XCTAssertFalse(preview.activeKeys.contains("glass_depth_enabled"))
            XCTAssertThrowsError(try store.set(.integer(101), for: "glass_depth"))
        }
    }

    func testGlassOpacityPersistsLocallyWithoutTransfer() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(store.glassOpacity, 63)
            try store.set(.integer(82), for: "glass_opacity")
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).glassOpacity, 82)
            XCTAssertFalse(try store.previewImport(store.exportData()).activeKeys.contains("glass_opacity"))
            XCTAssertThrowsError(try store.set(.integer(101), for: "glass_opacity"))
        }
    }

    func testGlassBlurPersistsLocallyWithoutTransfer() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(store.glassBlur, 40)
            try store.set(.integer(75), for: "glass_blur")
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).glassBlur, 75)
            XCTAssertFalse(try store.previewImport(store.exportData()).activeKeys.contains("glass_blur"))
            XCTAssertThrowsError(try store.set(.integer(-1), for: "glass_blur"))
        }
    }

    func testSearchAndGlassHighlightsPersistInTransfer() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.hideSearchField)
            XCTAssertTrue(store.glassHighlights)
            try store.set(.boolean(true), for: "hide_search_field")
            try store.set(.boolean(false), for: "glass_highlights")
            let restored = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(restored.hideSearchField)
            XCTAssertFalse(restored.glassHighlights)
            let preview = try store.previewImport(store.exportData())
            XCTAssertTrue(preview.activeKeys.contains("hide_search_field"))
            XCTAssertTrue(preview.activeKeys.contains("glass_highlights"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "glass_highlights"))
        }
    }

    func testChatDividerPreferencePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.hideDividers)
            try store.set(.boolean(true), for: "hide_dividers")
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).hideDividers)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("hide_dividers"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "hide_dividers"))
        }
    }

    func testCallMenuPreferencesPersistAndTransfer() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.menuCall)
            XCTAssertTrue(store.menuVideo)
            try store.set(.boolean(false), for: "menu_call")
            try store.set(.boolean(false), for: "menu_video")
            let restored = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(restored.menuCall)
            XCTAssertFalse(restored.menuVideo)
            let preview = try store.previewImport(store.exportData())
            XCTAssertTrue(preview.activeKeys.contains("menu_call"))
            XCTAssertTrue(preview.activeKeys.contains("menu_video"))
            XCTAssertThrowsError(try store.set(.integer(0), for: "menu_call"))
        }
    }

    func testTopicMuteMenuPreferencePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.menuMute)
            try store.set(.boolean(false), for: "menu_mute")
            XCTAssertFalse(store.menuMute)
            XCTAssertFalse(NebulaSettingsStore(defaults: defaults).menuMute)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("menu_mute"))
            XCTAssertThrowsError(try store.set(.integer(0), for: "menu_mute"))
        }
    }

    func testChatMenuSearchPreferencePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.menuSearch)
            try store.set(.boolean(false), for: "menu_search")
            XCTAssertFalse(store.menuSearch)
            XCTAssertFalse(NebulaSettingsStore(defaults: defaults).menuSearch)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("menu_search"))
            XCTAssertThrowsError(try store.set(.integer(0), for: "menu_search"))
        }
    }

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

    func testFolderTitlePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.folderTitle)
            try store.set(.boolean(false), for: "folder_title")
            XCTAssertFalse(store.folderTitle)
            XCTAssertFalse(NebulaSettingsStore(defaults: defaults).folderTitle)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("folder_title"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "folder_title"))
        }
    }

    func testFolderOutlinePersistsAndTransfers() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(store.folderOutline)
            try store.set(.boolean(true), for: "folder_outline")
            XCTAssertTrue(NebulaSettingsStore(defaults: defaults).folderOutline)
            XCTAssertTrue(try store.previewImport(store.exportData()).activeKeys.contains("folder_outline"))
            XCTAssertThrowsError(try store.set(.integer(1), for: "folder_outline"))
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

    func testProfileAndReplyPreferencesPersistAndImportAsActive() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertTrue(store.profileChannel && store.profileBirthday && store.profileBusiness)
            XCTAssertTrue(store.profileBackground && store.profileEmoji && store.profilePhotoBanner)
            XCTAssertTrue(store.replyBackground && store.replyColors && store.replyEmoji)
            let keys = ["profile_channel", "profile_birthday", "profile_business", "profile_background", "profile_emoji", "profile_photo_banner", "reply_background", "reply_colors", "reply_emoji"]
            let data = try JSONEncoder().encode(SettingsDocument(settings: Dictionary(uniqueKeysWithValues: keys.map { ($0, SettingValue.boolean(false)) })))
            XCTAssertEqual(try store.previewImport(data).pendingKeys, [])
            try store.importData(data)
            let loaded = NebulaSettingsStore(defaults: defaults)
            XCTAssertFalse(loaded.profileChannel || loaded.profileBirthday || loaded.profileBusiness)
            XCTAssertFalse(loaded.profileBackground || loaded.profileEmoji || loaded.profilePhotoBanner)
            XCTAssertFalse(loaded.replyBackground || loaded.replyColors || loaded.replyEmoji)
            XCTAssertThrowsError(try store.set(.integer(1), for: "profile_channel"))
        }
    }

    func testImportRetainsPendingKeysAndRejectsInvalidAtomically() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            let valid = try JSONEncoder().encode(SettingsDocument(settings: [
                "hide_tab_counters": .boolean(true), "material_you": .boolean(false)
            ]))
            XCTAssertEqual(try store.importData(valid), ["material_you"])
            let before = defaults.data(forKey: NebulaSettingsStore.storageKey)
            let invalid = try JSONEncoder().encode(SettingsDocument(settings: [
                "hide_tab_counters": .boolean(false), "avatar_round": .integer(9999)
            ]))
            XCTAssertThrowsError(try store.importData(invalid))
            XCTAssertThrowsError(try store.importData(Data("{\"format\":\"NebulaGram-settings\",\"version\":2,\"settings\":{}}".utf8)))
            XCTAssertThrowsError(try store.set(.integer(1), for: "hide_tab_counters"))
            XCTAssertThrowsError(try store.set(.boolean(true), for: "material_you"))
            XCTAssertThrowsError(try store.set(.boolean(true), for: "unknown"))
            XCTAssertEqual(before, defaults.data(forKey: NebulaSettingsStore.storageKey))
            XCTAssertTrue(store.hideTabCounters)
            let exported = try JSONDecoder().decode(SettingsDocument.self, from: store.exportData())
            XCTAssertEqual(exported.settings.count, 2)
            XCTAssertEqual(exported.settings["material_you"], .boolean(false))
        }
    }

    func testPortableChatPreferencesPersistAndActivateAfterImport() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            let values: [String: SettingValue] = [
                "sticker_time_style": .integer(2), "channel_forward_count": .boolean(true),
                "inline_math": .boolean(true), "folder_unmuted_only": .boolean(true),
                "swipe_actions": .string("3,0,1")
            ]
            let data = try JSONEncoder().encode(SettingsDocument(settings: values))
            XCTAssertTrue(try store.importData(data).isEmpty)
            let restored = NebulaSettingsStore(defaults: defaults)
            let exported = try JSONDecoder().decode(SettingsDocument.self, from: restored.exportData())
            XCTAssertEqual(exported.settings, values)
            for (key, value) in values { try restored.set(value, for: key) }
            XCTAssertEqual(restored.stickerTimeStyle, 2)
            XCTAssertTrue(restored.channelForwardCount)
            XCTAssertTrue(restored.inlineArithmetic)
            XCTAssertTrue(restored.folderUnmutedOnly)
            XCTAssertEqual(restored.swipeActions, [.translate, .reply, .copy])
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
    func testFolderPanelStyleHasAValidatedNativeConsumerValue() throws {
        try withDefaults { defaults in
            let store = NebulaSettingsStore(defaults: defaults)
            XCTAssertEqual(store.folderPanelStyle, 0)
            try store.set(.integer(2), for: "folder_panel_style")
            XCTAssertEqual(NebulaSettingsStore(defaults: defaults).folderPanelStyle, 2)
            XCTAssertThrowsError(try store.set(.integer(3), for: "folder_panel_style"))
            XCTAssertEqual(store.folderPanelStyle, 2)
        }
    }
}
