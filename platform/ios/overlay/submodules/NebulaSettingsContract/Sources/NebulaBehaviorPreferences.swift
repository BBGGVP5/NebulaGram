import Foundation

/// Local account-scoped behavior choices. Shared defaults expose notification policy to the NSE.
public final class NebulaBehaviorPreferences {
    public static let shared = NebulaBehaviorPreferences()
    public static let changed = Notification.Name("NebulaBehaviorPreferencesChanged")
    private let defaults: UserDefaults
    public init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: "group." + (Bundle.main.bundleIdentifier ?? "app.nebulagram")) ?? .standard
    }
    public var smoothFade: Bool { defaults.bool(forKey: "nebula.behavior.smooth_fade") }
    public func setSmoothFade(_ value: Bool) { defaults.set(value, forKey: "nebula.behavior.smooth_fade"); notify() }
    /// Preserve iOS's native title until a user chooses the brand title.
    public var homeChatsTitle: Bool { defaults.object(forKey: "nebula.behavior.home_chats_title") as? Bool ?? true }
    public func setHomeChatsTitle(_ value: Bool) { defaults.set(value, forKey: "nebula.behavior.home_chats_title"); notify() }
    public var folderTabsBottom: Bool { defaults.bool(forKey: "nebula.behavior.folder_tabs_bottom") }
    public func setFolderTabsBottom(_ value: Bool) { defaults.set(value, forKey: "nebula.behavior.folder_tabs_bottom"); notify() }
    private func key(_ account: String, _ name: String) -> String { "nebula.behavior." + account + "." + name }
    public func enabled(_ name: String, account: String) -> Bool {
        defaults.object(forKey: key(account, name)) as? Bool ?? ["custom_chat_wallpaper", "notifications"].contains(name)
    }
    public func set(_ name: String, account: String, value: Bool) {
        defaults.set(value, forKey: key(account, name)); notify()
    }
    public var disabledNotificationAccounts: Set<String> {
        let prefix = "nebula.behavior.", suffix = ".notifications"
        return Set(defaults.dictionaryRepresentation().compactMap { key, value -> String? in
            guard key.hasPrefix(prefix), key.hasSuffix(suffix), value as? Bool == false else { return nil }
            return String(key.dropFirst(prefix.count).dropLast(suffix.count))
        })
    }
    public func savedTarget(account: String) -> Int64? {
        (defaults.object(forKey: key(account, "saved_destination")) as? NSNumber)?.int64Value
    }
    public func setSavedTarget(account: String, peer: Int64?) {
        if let peer { defaults.set(peer, forKey: key(account, "saved_destination")) }
        else { defaults.removeObject(forKey: key(account, "saved_destination")) }
        notify()
    }
    /// nil means all chats; an empty set means no chats.
    public func ignoredMentionPeers(account: String) -> Set<Int64>? {
        guard let values = defaults.stringArray(forKey: key(account, "mention_peers")) else { return nil }
        return Set(values.compactMap(Int64.init))
    }
    public func setIgnoredMentionPeers(account: String, peers: Set<Int64>?) {
        if let peers { defaults.set(peers.sorted().map(String.init), forKey: key(account, "mention_peers")) }
        else { defaults.removeObject(forKey: key(account, "mention_peers")) }
        notify()
    }
    public func ignoreMention(account: String, peer: Int64) -> Bool {
        enabled("ignore_mentions", account: account) && (ignoredMentionPeers(account: account)?.contains(peer) ?? true)
    }
    public static func silenceUnknown(enabled: Bool, isPrivate: Bool, contact: Bool, serviceOrSelf: Bool) -> Bool {
        enabled && isPrivate && !contact && !serviceOrSelf
    }
    public static func fade(_ progress: Double, enabled: Bool) -> Double {
        guard enabled else { return progress }
        let p = min(1, max(0, progress)); return p * p * (3 - 2 * p)
    }
    private func notify() { NotificationCenter.default.post(name: Self.changed, object: nil) }
}
