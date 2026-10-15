import Foundation

/// Device-local chat-row presentation. Open conversations and notifications are outside its scope.
public enum NebulaPresentationPrivacy {
    private static let key = "nebula.presentation.maskChatRows"
    public static var enabled: Bool { UserDefaults.standard.bool(forKey: key) }
    public static func setEnabled(_ enabled: Bool) {
        guard enabled != self.enabled else { return }
        UserDefaults.standard.set(enabled, forKey: key)
        NebulaSettingsStore.shared.refreshPresentation()
    }
    public static func showStories(preference: Bool) -> Bool { preference && !enabled }
    public static func accountLabel(position: Int, russian: Bool) -> String { (russian ? "Аккаунт " : "Account ") + String(max(1, position + 1)) }
}
