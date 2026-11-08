import Foundation

/// NebulaLink button on the home screen (Android `home_nebulalink`). It uses the
/// header slot of the camera/AI shortcut; the AI shortcut keeps it when both are on.
/// Opening it only presents the NebulaLink screen: no tunnel or probe starts.
public enum NebulaHomeLink {
    public static let key = "nebula.home.nebulalink"
    public static let open = Notification.Name("NebulaHomeLinkOpen")

    public static func enabled(_ defaults: UserDefaults = .standard) -> Bool {
        defaults.bool(forKey: key)
    }

    public static func setEnabled(_ value: Bool, _ defaults: UserDefaults = .standard) {
        defaults.set(value, forKey: key)
        // The home header already re-renders on this notification.
        NotificationCenter.default.post(name: NebulaAiSettings.homeShortcutChanged, object: nil)
    }

    public static func showsLink(aiShortcut: Bool, link: Bool) -> Bool {
        link && !aiShortcut
    }
}
