import Foundation

public enum SettingsStoreError: Error {
    case unavailable
    case unsupportedControl(String)
}

/// Owns its subscription, not the store or its UI consumer.
public final class SettingsObservation {
    private let center: NotificationCenter
    private var token: NSObjectProtocol?
    private let lock = NSLock()

    fileprivate init(center: NotificationCenter, token: NSObjectProtocol) {
        self.center = center
        self.token = token
    }

    public func cancel() {
        lock.lock()
        let previous = token
        token = nil
        lock.unlock()
        if let previous { center.removeObserver(previous) }
    }

    deinit { cancel() }
}

/// Global presentation preferences only: never Telegram sessions or AI/VPN secrets.
/// The native iOS adapter is experimental; other imported v1 keys are retained,
/// not applied. Catalog defaults are not materialized into exports.
public final class NebulaSettingsStore {
    public static let shared = NebulaSettingsStore(defaults: .standard)
    public static let storageKey = "app.nebulagram.presentation.settings.v1"
    public static let editableKeys: Set<String> = ["folder_panel_style", "sticker_time_style", "channel_forward_count", "inline_math", "folder_unmuted_only", "swipe_actions", "icon_pack", "switch_style", "login_style", "adaptive_chat_header", "floating_chat_header_v2", "header_unread", "glass_custom", "glass_haptics", "glass_haptic_strength", "own_double_tap", "message_menu_blur", "bottom_bar", "hide_all_chats", "center_home", "avatar_round", "custom_avatar_corners", "uniform_avatars", "hide_tab_counters", "show_stories", "settings_search_history", "glass_quality", "glass_highlights", "glass_depth", "glass_depth_enabled", "glass_opacity", "glass_blur", "liquid_animations", "ios_glass_style", "ios_glass_tint", "bottom_bar_contacts", "bottom_bar_order", "bottom_bar_profile", "bottom_bar_settings", "tab_labels", "compact_bottom_bar", "hide_home_camera", "hide_home_compose", "hide_send_as", "hide_attach_camera", "hide_dividers", "hide_search_field", "hide_premium_status", "menu_search", "menu_mute", "menu_call", "menu_video", "folder_style", "folder_title", "folder_outline", "centered_chat_header", "disable_next_channel", "seconds_in_time", "wide_posts", "fragment_transition_style", "profile_style", "profile_channel", "profile_birthday", "profile_business", "profile_background", "profile_emoji", "profile_photo_banner", "reply_background", "reply_colors", "reply_emoji"]
    public static let maximumTransferBytes = 1024 * 1024

    private let defaults: UserDefaults
    private let catalog: SettingsCatalog?
    private let lock = NSLock()
    private let center = NotificationCenter()
    private let changed = Notification.Name("NebulaSettingsChanged")
    private var values: [String: SettingValue] = [:]
    private var failedToLoad = false

    public init(defaults: UserDefaults, catalog: SettingsCatalog? = try? SettingsCatalog.bundled()) {
        self.defaults = defaults
        self.catalog = catalog
        guard let catalog else { failedToLoad = true; return }
        guard let stored = defaults.object(forKey: Self.storageKey) else { return }
        do {
            guard let data = stored as? Data else { throw SettingsStoreError.unavailable }
            let document = try JSONDecoder().decode(SettingsDocument.self, from: data)
            try catalog.validate(document, localKeys: Self.editableKeys)
            values = document.settings
        } catch {
            // Keep corrupt/unsupported bytes intact. Only an explicit valid import
            // may replace them; opening the screen must never reset preferences.
            failedToLoad = true
        }
    }

    public var hasLoadError: Bool {
        lock.lock(); defer { lock.unlock() }
        return failedToLoad
    }

    public var hideTabCounters: Bool {
        lock.lock(); defer { lock.unlock() }
        if case let .boolean(value) = values["hide_tab_counters"] { return value }
        return false
    }

    public var glassQuality: Int {
        lock.lock(); defer { lock.unlock() }
        if case let .integer(value) = values["glass_quality"] { return max(0, min(2, value)) }
        return 0
    }
    public var iosGlassStyle: Int {
        let style = integer("ios_glass_style", fallback: 0)
        return boolean("glass_custom", fallback: style != 0) ? max(1, style) : 0
    }
    public var glassHaptics: Bool { boolean("glass_haptics", fallback: false) }
    public var glassHapticStrength: Int { max(1, min(100, integer("glass_haptic_strength", fallback: 35))) }
    public var showBottomBar: Bool { boolean("bottom_bar", fallback: true) }
    public var hideAllChats: Bool { boolean("hide_all_chats", fallback: false) }
    public var centerHome: Bool { boolean("center_home", fallback: false) }
    public var customAvatarCorners: Bool { boolean("custom_avatar_corners", fallback: contains("avatar_round")) }
    public var avatarRound: Int { customAvatarCorners ? max(0, min(100, integer("avatar_round", fallback: 100))) : 100 }
    public var uniformAvatars: Bool { boolean("uniform_avatars", fallback: true) }
    public var iconPack: Int {
        let value = integer("icon_pack", fallback: boolean("ios_icons", fallback: true) ? 1 : 0)
        // Android's imported pack is preserved for transfer; its files are local to Android.
        return (0...2).contains(value) ? value : 0
    }
    public var switchStyle: Int { max(0, min(3, integer("switch_style", fallback: 0))) }
    public var loginStyle: Bool { boolean("login_style", fallback: true) }
    public var adaptiveChatHeader: Bool { boolean("adaptive_chat_header", fallback: true) }
    public var floatingChatHeader: Bool { boolean("floating_chat_header_v2", fallback: true) }
    public var headerUnread: Bool { boolean("header_unread", fallback: false) }
    public var ownDoubleTap: Int { max(0, min(4, integer("own_double_tap", fallback: 0))) }
    public var messageMenuBlur: Bool { boolean("message_menu_blur", fallback: true) }
    public var stickerTimeStyle: Int { max(0, min(2, integer("sticker_time_style", fallback: 0))) }
    public var channelForwardCount: Bool { boolean("channel_forward_count", fallback: false) }
    public var inlineArithmetic: Bool { boolean("inline_math", fallback: false) }
    public var folderUnmutedOnly: Bool { boolean("folder_unmuted_only", fallback: false) }
    public var swipeActions: [NebulaSwipeAction] {
        lock.lock(); defer { lock.unlock() }
        guard case let .string(value) = values["swipe_actions"] else { return [.reply] }
        return NebulaSwipeActions.parse(value)
    }
    private func contains(_ key: String) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return values[key] != nil
    }
    public var iosGlassTint: Int { integer("ios_glass_tint", fallback: 30) }
    public var liquidAnimations: Bool { boolean("liquid_animations", fallback: true) }
    public var glassHighlights: Bool { boolean("glass_highlights", fallback: true) }
    public var glassDepth: Int { integer("glass_depth", fallback: 35) }
    public var glassDepthEnabled: Bool { boolean("glass_depth_enabled", fallback: true) }
    public var glassOpacity: Int { integer("glass_opacity", fallback: 63) }
    public var glassBlur: Int { integer("glass_blur", fallback: 40) }
    private func integer(_ key: String, fallback: Int) -> Int {
        lock.lock(); defer { lock.unlock() }
        if case let .integer(value) = values[key] { return value }
        return fallback
    }
    public var transitionStyle: Int {
        lock.lock(); defer { lock.unlock() }
        if case let .integer(value) = values["fragment_transition_style"] { return max(0, min(2, value)) }
        return 0
    }
    public var showContactsTab: Bool { boolean("bottom_bar_contacts", fallback: true) }
    public var showProfileTab: Bool { boolean("bottom_bar_profile", fallback: true) }
    /// The Settings tab is the recovery route when other tabs are hidden.
    public var showSettingsTab: Bool { boolean("bottom_bar_settings", fallback: true) || !showProfileTab }
    public var showTabLabels: Bool { boolean("tab_labels", fallback: true) }
    public var compactBottomBar: Bool { boolean("compact_bottom_bar", fallback: false) }
    public var hideHomeCamera: Bool { boolean("hide_home_camera", fallback: false) }
    public var hideHomeCompose: Bool { boolean("hide_home_compose", fallback: false) }
    public var hideSendAs: Bool { boolean("hide_send_as", fallback: false) }
    public var hideAttachCamera: Bool { boolean("hide_attach_camera", fallback: false) }
    public var hideDividers: Bool { boolean("hide_dividers", fallback: false) }
    public var hideSearchField: Bool { boolean("hide_search_field", fallback: false) }
    public var hidePremiumStatus: Bool { boolean("hide_premium_status", fallback: false) }
    public var menuSearch: Bool { boolean("menu_search", fallback: true) }
    public var menuMute: Bool { boolean("menu_mute", fallback: true) }
    public var menuCall: Bool { boolean("menu_call", fallback: true) }
    public var menuVideo: Bool { boolean("menu_video", fallback: true) }
    public var folderTitle: Bool { boolean("folder_title", fallback: true) }
    public var folderOutline: Bool { boolean("folder_outline", fallback: false) }
    public var folderPanelStyle: Int { integer("folder_panel_style", fallback: 0) }
    public var folderStyle: Int { integer("folder_style", fallback: 0) }
    public var centeredChatHeader: Bool { boolean("centered_chat_header", fallback: true) }
    public var disableNextChannel: Bool { boolean("disable_next_channel", fallback: false) }
    public var secondsInTime: Bool { boolean("seconds_in_time", fallback: false) }
    public var bottomTabOrder: [String] {
        lock.lock(); defer { lock.unlock() }
        let standard = ["chats", "contacts", "settings", "profile"]
        guard case let .string(value) = values["bottom_bar_order"] else { return standard }
        let order = value.split(separator: ",").map(String.init)
        return order.count == 4 && Set(order) == Set(standard) ? order : standard
    }
    public var profileStyle: Bool { boolean("profile_style", fallback: true) }
    public var profileChannel: Bool { boolean("profile_channel", fallback: true) }
    public var profileBirthday: Bool { boolean("profile_birthday", fallback: true) }
    public var profileBusiness: Bool { boolean("profile_business", fallback: true) }
    public var profileBackground: Bool { boolean("profile_background", fallback: true) }
    public var profileEmoji: Bool { boolean("profile_emoji", fallback: true) }
    public var profilePhotoBanner: Bool { boolean("profile_photo_banner", fallback: true) }
    public var replyBackground: Bool { boolean("reply_background", fallback: true) }
    public var replyColors: Bool { boolean("reply_colors", fallback: true) }
    public var replyEmoji: Bool { boolean("reply_emoji", fallback: true) }
    public var widePosts: Bool { boolean("wide_posts", fallback: false) }
    public var showStories: Bool { boolean("show_stories", fallback: true) }
    public var settingsSearchHistory: Bool { boolean("settings_search_history", fallback: true) }
    private func boolean(_ key: String, fallback: Bool) -> Bool {
        lock.lock(); defer { lock.unlock() }
        if case let .boolean(value) = values[key] { return value }
        return fallback
    }

    public func exportData() throws -> Data {
        lock.lock(); defer { lock.unlock() }
        guard !failedToLoad else { throw SettingsStoreError.unavailable }
        guard let catalog else { throw SettingsStoreError.unavailable }
        let transferKeys = Set(catalog.settings.filter(\.transferV1).map(\.key))
        return try JSONEncoder().encode(SettingsDocument(settings: values.filter { transferKeys.contains($0.key) }))
    }

    public func set(_ value: SettingValue, for key: String) throws {
        guard Self.editableKeys.contains(key) else { throw SettingsStoreError.unsupportedControl(key) }
        try mutate(recover: false) { current in
            var next = current
            next[key] = value
            if key == "ios_glass_style", case let .integer(style) = value {
                next["glass_custom"] = .boolean(style != 0)
            }
            return SettingsDocument(settings: next)
        }
    }

    /// Explicit replacement, validated in full before storage or UI is changed.
    /// Returns keys retained for future ports, not currently activated on iOS.
    @discardableResult
    public func importData(_ data: Data) throws -> Set<String> {
        let preview = try previewImport(data)
        try mutate(recover: true) { current in
            guard let catalog = self.catalog else { throw SettingsStoreError.unavailable }
            let localKeys = Set(catalog.settings.filter { !$0.transferV1 }.map(\.key))
            var next = current.filter { localKeys.contains($0.key) }
            next.merge(preview.document.settings) { _, imported in imported }
            return SettingsDocument(settings: next)
        }
        return preview.pendingKeys
    }

    /// Read-only validation for confirmation UI; even a recovery import is not
    /// committed until the user confirms. The supplied bytes are bounded first.
    public func previewImport(_ data: Data) throws -> SettingsTransferPreview {
        guard data.count <= Self.maximumTransferBytes else { throw ContractError.invalidDocument }
        guard let catalog else { throw SettingsStoreError.unavailable }
        let document = try JSONDecoder().decode(SettingsDocument.self, from: data)
        try catalog.validate(document)
        return SettingsTransferPreview(document: document,
            activeKeys: Set(document.settings.keys).intersection(Self.editableKeys),
            pendingKeys: Set(document.settings.keys).subtracting(Self.editableKeys))
    }

    private func mutate(recover: Bool, document: ([String: SettingValue]) throws -> SettingsDocument) throws {
        lock.lock()
        do {
            guard let catalog, recover || !failedToLoad else { throw SettingsStoreError.unavailable }
            let next = try document(values)
            try catalog.validate(next, localKeys: Self.editableKeys)
            let data = try JSONEncoder().encode(next)
            let didChange = failedToLoad || values != next.settings
            if didChange {
                defaults.set(data, forKey: Self.storageKey)
                values = next.settings
                failedToLoad = false
            }
            lock.unlock()
            if didChange { center.post(name: changed, object: nil) }
        } catch {
            lock.unlock()
            throw error
        }
    }

    /// UI subscribers use the default main queue and read the latest snapshot.
    /// Pass nil only for synchronous, non-UI consumers/tests.
    public func observe(queue: OperationQueue? = .main, _ callback: @escaping () -> Void) -> SettingsObservation {
        let token = center.addObserver(forName: changed, object: nil, queue: queue) { _ in callback() }
        return SettingsObservation(center: center, token: token)
    }
}
