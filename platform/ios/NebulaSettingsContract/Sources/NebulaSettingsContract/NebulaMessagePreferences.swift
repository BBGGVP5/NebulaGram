import Foundation

/// Local presentation/media choices. Defaults preserve native iOS behavior.
public final class NebulaMessagePreferences {
    public static let shared = NebulaMessagePreferences()
    public static let changed = Notification.Name("NebulaMessagePreferencesChanged")
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public func enabled(_ key: String) -> Bool {
        defaults.object(forKey: "nebula.messages." + key) as? Bool ?? ["direct_share", "voice_autoplay", "premium_effects", "reaction_effects", "instant_view", "delete_dust", "profile_registration"].contains(key)
    }
    public func set(_ key: String, _ value: Bool) {
        guard ["edited_pencil", "forward_date", "direct_share", "voice_autoplay", "hide_profile_phone", "profile_photo_dc", "pause_background_video", "disable_message_effects", "premium_effects", "reaction_effects", "instant_view", "delete_for_all", "selection_without_author", "delete_dust", "profile_registration"].contains(key) else { return }
        defaults.set(value, forKey: "nebula.messages." + key)
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
    public var botApiIds: Bool { defaults.bool(forKey: "nebula.messages.bot_api_ids") }
    public func setBotApiIds(_ value: Bool) {
        defaults.set(value, forKey: "nebula.messages.bot_api_ids")
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
    public enum PeerKind { case user, group, channel }
    public static func peerIdentifier(_ id: Int64, kind: PeerKind, botApi: Bool) -> String? {
        guard id > 0 else { return nil }
        guard botApi else { return String(id) }
        switch kind {
        case .user: return String(id)
        case .group: return String(-id)
        case .channel:
            let (value, overflow) = Int64(-1_000_000_000_000).subtractingReportingOverflow(id)
            return overflow ? nil : String(value)
        }
    }
    public var seekInterval: Int {
        let value = defaults.integer(forKey: "nebula.messages.seek_interval")
        return [5, 10, 15, 20, 25, 30].contains(value) ? value : 15
    }
    public func setSeekInterval(_ value: Int) {
        guard [5, 10, 15, 20, 25, 30].contains(value) else { return }
        defaults.set(value, forKey: "nebula.messages.seek_interval")
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
    /// "Blurred messages": bubbles show the blurred wallpaper under a
    /// translucent tint of their own colour. The wallpaper hook reads these
    /// keys straight from `UserDefaults.standard`.
    public static let messageBlurKey = "nebula.messages.message_blur"
    public static let messageBlurTintKey = "nebula.messages.message_blur_tint"
    /// Tint densities offered in settings, in percent of the bubble colour.
    public static let messageBlurTints = [35, 55, 75]
    public static let defaultMessageBlurTint = 55
    public var messageBlur: Bool { defaults.bool(forKey: Self.messageBlurKey) }
    public func setMessageBlur(_ value: Bool) {
        defaults.set(value, forKey: Self.messageBlurKey)
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
    public var messageBlurTint: Int { Self.validTint(defaults.object(forKey: Self.messageBlurTintKey) as? Int) }
    public func setMessageBlurTint(_ value: Int) {
        guard Self.messageBlurTints.contains(value) else { return }
        defaults.set(value, forKey: Self.messageBlurTintKey)
        NotificationCenter.default.post(name: Self.changed, object: nil)
    }
    /// Stored values outside the offered densities fall back to the default.
    public static func validTint(_ value: Int?) -> Int {
        guard let value, messageBlurTints.contains(value) else { return defaultMessageBlurTint }
        return value
    }
    /// Alpha of the bubble tint over the blur: the colour's own alpha scaled by the density.
    public static func tintAlpha(colorAlpha: Double, percent: Int) -> Double {
        max(0.0, min(1.0, colorAlpha * Double(validTint(percent)) / 100.0))
    }
}
