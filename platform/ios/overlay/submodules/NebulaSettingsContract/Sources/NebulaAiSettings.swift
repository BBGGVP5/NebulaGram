import Foundation

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

    private let defaults: UserDefaults
    private let prefix = "nebula.ai."

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

    public var provider: NebulaAiProvider {
        get { NebulaAiProvider(rawValue: defaults.integer(forKey: name("provider"))) ?? .openAI }
        set { defaults.set(newValue.rawValue, forKey: name("provider")) }
    }

    /// The model id, remembered per provider: a model name means nothing to a
    /// provider that does not have it, and switching back should not lose it.
    public func model(for provider: NebulaAiProvider) -> String {
        defaults.string(forKey: name("model_\(provider.rawValue)")) ?? ""
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
        let raw = provider == .custom ? customEndpoint : (provider.endpoint ?? "")
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let url = URL(string: trimmed),
              url.scheme?.lowercased() == "https", url.host?.isEmpty == false else { return nil }
        return url
    }

    /// Whether a request could be made right now: somewhere to send it, a model
    /// to name, and a key to sign it with.
    public func isConfigured(secrets: NebulaAiSecrets = .shared) -> Bool {
        endpoint(for: provider) != nil
            && !model(for: provider).isEmpty
            && secrets.hasKey(for: provider)
    }
}
