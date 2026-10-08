import Foundation

/// Local presentation/media choices. Defaults preserve native iOS behavior.
public final class NebulaMessagePreferences {
    public static let shared = NebulaMessagePreferences()
    public static let changed = Notification.Name("NebulaMessagePreferencesChanged")
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public func enabled(_ key: String) -> Bool {
        defaults.object(forKey: "nebula.messages." + key) as? Bool ?? ["direct_share", "voice_autoplay"].contains(key)
    }
    public func set(_ key: String, _ value: Bool) {
        guard ["edited_pencil", "forward_date", "direct_share", "voice_autoplay", "hide_profile_phone", "profile_photo_dc"].contains(key) else { return }
        defaults.set(value, forKey: "nebula.messages." + key)
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
}
