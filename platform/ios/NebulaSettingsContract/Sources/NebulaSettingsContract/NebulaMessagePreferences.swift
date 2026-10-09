import Foundation

/// Local presentation/media choices. Defaults preserve native iOS behavior.
public final class NebulaMessagePreferences {
    public static let shared = NebulaMessagePreferences()
    public static let changed = Notification.Name("NebulaMessagePreferencesChanged")
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public func enabled(_ key: String) -> Bool {
        defaults.object(forKey: "nebula.messages." + key) as? Bool ?? ["direct_share", "voice_autoplay", "premium_effects", "reaction_effects", "instant_view"].contains(key)
    }
    public func set(_ key: String, _ value: Bool) {
        guard ["edited_pencil", "forward_date", "direct_share", "voice_autoplay", "hide_profile_phone", "profile_photo_dc", "pause_background_video", "disable_message_effects", "premium_effects", "reaction_effects", "instant_view", "delete_for_all", "selection_without_author"].contains(key) else { return }
        defaults.set(value, forKey: "nebula.messages." + key)
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
    public var seekInterval: Int {
        let value = defaults.integer(forKey: "nebula.messages.seek_interval")
        return [5, 10, 15, 20, 30].contains(value) ? value : 15
    }
    public func setSeekInterval(_ value: Int) {
        guard [5, 10, 15, 20, 30].contains(value) else { return }
        defaults.set(value, forKey: "nebula.messages.seek_interval")
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
}
