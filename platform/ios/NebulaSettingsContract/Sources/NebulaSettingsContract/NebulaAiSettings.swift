import Foundation
import CryptoKit

/// Everything about an AI connection except the key.
///
/// Split from `NebulaAiSecrets` on purpose: this half is ordinary preferences —
/// readable, exportable in principle, safe in a crash log — and the other half
/// is a credential that never leaves the keychain. Keeping them in one type
/// would make it far too easy for a key to follow the rest of the settings
/// somewhere it should not go.
///
/// Keys mirror the names Android writes into its own `nebula_ai_settings`, so
/// the two platforms describe the same connection the same way.
public final class NebulaAiSettings {
    public static let shared = NebulaAiSettings()
    public static let homeShortcutChanged = Notification.Name("NebulaAiHomeShortcutChanged")
    public static let openHomeChat = Notification.Name("NebulaAiOpenHomeChat")

    public var homeShortcut: Bool {
        get { defaults.bool(forKey: name("home_shortcut")) }
        set {
            defaults.set(newValue, forKey: name("home_shortcut"))
            NotificationCenter.default.post(name: Self.homeShortcutChanged, object: nil)
        }
    }

    private let defaults: UserDefaults
    private let prefix = "nebula.ai."
    public var services: NebulaAiServices { NebulaAiServices(defaults: defaults) }
    public var roles: NebulaAiRoles { NebulaAiRoles(defaults: defaults) }
    public var temperature: Double {
        get { let value = defaults.object(forKey: name("temperature")) as? Double ?? 1; return value.isFinite ? min(2, max(0, value)) : 1 }
        set { defaults.set(newValue.isFinite ? min(2, max(0, newValue)) : 1, forKey: name("temperature")) }
    }
    public var streaming: Bool {
        get { defaults.object(forKey: name("streaming")) as? Bool ?? true }
        set { defaults.set(newValue, forKey: name("streaming")) }
    }
    public var reasoning: Bool {
        get { defaults.bool(forKey: name("reasoning")) }
        set { defaults.set(newValue, forKey: name("reasoning")) }
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private func name(_ key: String) -> String { prefix + key }

    /// Whether the AI entry appears in message menus at all.
    ///
    /// Off until someone turns it on: an assistant that reaches a third party
    /// with the text of a message is not something to enable on a user's behalf.
    public var enabled: Bool {
        get { defaults.bool(forKey: name("enabled")) }
        set { defaults.set(newValue, forKey: name("enabled")) }
    }

    /// Conversation history is local to this installation and stays opt-in.
    public var historyEnabled: Bool {
        get { defaults.bool(forKey: name("history_enabled")) }
        set { defaults.set(newValue, forKey: name("history_enabled")) }
    }

    public var provider: NebulaAiProvider {
        get { services.active?.provider ?? NebulaAiProvider(rawValue: defaults.integer(forKey: name("provider"))) ?? .openAI }
        set { defaults.set(newValue.rawValue, forKey: name("provider")) }
    }

    /// The model id, remembered per provider: a model name means nothing to a
    /// provider that does not have it, and switching back should not lose it.
    public func model(for provider: NebulaAiProvider) -> String {
        if let selected = services.active, selected.provider == provider { return selected.model }
        return defaults.string(forKey: name("model_\(provider.rawValue)")) ?? ""
    }

    public func setModel(_ value: String, for provider: NebulaAiProvider) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        defaults.set(String(trimmed.prefix(256)), forKey: name("model_\(provider.rawValue)"))
    }

    /// The API root for `.custom`. The named providers carry their own and
    /// ignore this, so switching to one of them cannot send a request to an
    /// address left over from a custom connection.
    public var customEndpoint: String {
        get { defaults.string(forKey: name("endpoint")) ?? "" }
        set { defaults.set(String(newValue.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1000)), forKey: name("endpoint")) }
    }

    /// The system prompt sent with every request.
    public var instructions: String {
        get { defaults.string(forKey: name("prompt")) ?? "" }
        set { defaults.set(String(newValue.prefix(8192)), forKey: name("prompt")) }
    }

    /// The address requests go to, or nil when a custom connection has no
    /// usable one yet. Only https is accepted: a key in an Authorization
    /// header over plain http is a key handed to the network.
    public func endpoint(for provider: NebulaAiProvider) -> URL? {
        if let selected = services.active, selected.provider == provider { return selected.url }
        let raw = provider == .custom ? customEndpoint : (provider.endpoint ?? "")
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let url = URL(string: trimmed),
              url.scheme?.lowercased() == "https", url.host?.isEmpty == false,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { return nil }
        return url
    }

    /// Whether a request could be made right now: somewhere to send it, a model
    /// to name, and a key to sign it with.
    public func isConfigured(secrets: NebulaAiSecrets = .shared) -> Bool {
        if services.migrated && services.active == nil { return false }
        if provider == .appleIntelligence { return true }
        return endpoint(for: provider) != nil
            && !model(for: provider).isEmpty
            && ((try? apiKey(secrets: secrets)) ?? nil) != nil
    }
    public func conversationIdentity(action: String, language: String, instructions: String) -> String {
        let parts = [services.active?.id ?? "legacy", String(provider.rawValue), model(for: provider), endpoint(for: provider)?.absoluteString ?? "", roles.selectedId, instructions, action, language]
        let data = (try? JSONEncoder().encode(parts)) ?? Data()
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
    public func apiKey(secrets: NebulaAiSecrets = .shared) throws -> String? {
        if let selected = services.active { return try secrets.serviceKey(id: selected.id) }
        if services.migrated { return nil }
        return try secrets.key(for: provider)
    }
}
