import Foundation
import Security

/// The AI providers the client can talk to, in the order Android numbers them.
///
/// The raw values are the numbers Android already writes to its own settings,
/// so a provider means the same thing on both platforms and a future transfer
/// of the non-secret part needs no translation table.
public enum NebulaAiProvider: Int, CaseIterable, Codable, Equatable {
    case openAI = 0
    case claude = 1
    case gemini = 2
    case custom = 3

    /// The default API root. Custom has none: the user supplies it.
    public var endpoint: String? {
        switch self {
        case .openAI: return "https://api.openai.com/v1"
        case .claude: return "https://api.anthropic.com/v1"
        case .gemini: return "https://generativelanguage.googleapis.com/v1beta"
        case .custom: return nil
        }
    }

    public var title: String {
        switch self {
        case .openAI: return "OpenAI"
        case .claude: return "Claude"
        case .gemini: return "Gemini"
        case .custom: return "Custom"
        }
    }
}

/// Where a provider's API key is kept. Injectable so tests never need a real
/// keychain, which a command-line test process on a build machine does not have.
public protocol NebulaSecretStorage {
    func secret(for account: String) throws -> String?
    func setSecret(_ value: String, for account: String) throws
    func removeSecret(for account: String) throws
}

/// API keys, one per provider, kept in the keychain and nowhere else.
///
/// Android keeps them in AES-GCM files under the no-backup directory with the
/// key held by the Android keystore. The keychain is the same guarantee stated
/// once: `ThisDeviceOnly` keeps the item out of iCloud and out of encrypted
/// backups, and `WhenUnlocked` keeps it unreadable while the device is locked,
/// which is stricter than the archive's own key needs to be and right for a
/// credential that only ever moves when someone is looking at the screen.
///
/// Keys are deliberately outside `SettingsCatalog`: the settings transfer
/// carries presentation, not credentials, and `NebulaSettingsStore.exportData`
/// can only serialise what the catalog describes. A key therefore cannot leave
/// the device through an export, and no code here writes one to `UserDefaults`.
public final class NebulaAiSecrets {
    public static let shared = NebulaAiSecrets()

    public enum Failure: Error, Equatable {
        /// The keychain refused the operation; `status` is its OSStatus.
        case keychain(OSStatus)
    }

    private let storage: NebulaSecretStorage
    private let lock = NSLock()

    public init(storage: NebulaSecretStorage? = nil) {
        self.storage = storage ?? NebulaKeychainStorage()
    }

    private func account(_ provider: NebulaAiProvider) -> String {
        "provider-\(provider.rawValue)"
    }

    /// The stored key, or nil when the provider has none.
    public func key(for provider: NebulaAiProvider) throws -> String? {
        lock.lock(); defer { lock.unlock() }
        return try storage.secret(for: account(provider))
    }

    /// Whether a key exists, without reading it. The settings screen shows a
    /// state, never the key itself, so it has no reason to hold one in memory.
    public func hasKey(for provider: NebulaAiProvider) -> Bool {
        ((try? key(for: provider)) ?? nil) != nil
    }

    /// Stores a key. Whitespace is trimmed, because a pasted key usually brings
    /// some, and an empty value removes the key rather than storing nothing.
    public func setKey(_ value: String, for provider: NebulaAiProvider) throws {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        lock.lock(); defer { lock.unlock() }
        if trimmed.isEmpty {
            try storage.removeSecret(for: account(provider))
        } else {
            try storage.setSecret(trimmed, for: account(provider))
        }
    }

    public func removeKey(for provider: NebulaAiProvider) throws {
        lock.lock(); defer { lock.unlock() }
        try storage.removeSecret(for: account(provider))
    }

    /// Forgets every provider's key. Used by "sign out" and by the settings
    /// screen's own reset, so a device changing hands leaves nothing behind.
    public func removeAll() throws {
        for provider in NebulaAiProvider.allCases {
            try removeKey(for: provider)
        }
    }
}

/// The keychain itself. Separated from the class above so the policy — one item
/// per provider, this device only — is stated in one place and testable in another.
public struct NebulaKeychainStorage: NebulaSecretStorage {
    public static let service = "app.nebulagram.ai"

    public init() {}

    private func query(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: account,
        ]
    }

    public func secret(for account: String) throws -> String? {
        var request = query(account)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw NebulaAiSecrets.Failure.keychain(status)
        }
        return String(data: data, encoding: .utf8)
    }

    public func setSecret(_ value: String, for account: String) throws {
        let data = Data(value.utf8)
        // Update first: SecItemAdd on an existing account fails with
        // errSecDuplicateItem, and deleting before adding would lose the key
        // if the process died between the two calls.
        let update = SecItemUpdate(query(account) as CFDictionary,
                                   [kSecValueData as String: data] as CFDictionary)
        if update == errSecSuccess { return }
        guard update == errSecItemNotFound else { throw NebulaAiSecrets.Failure.keychain(update) }
        var insert = query(account)
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let add = SecItemAdd(insert as CFDictionary, nil)
        guard add == errSecSuccess else { throw NebulaAiSecrets.Failure.keychain(add) }
    }

    public func removeSecret(for account: String) throws {
        let status = SecItemDelete(query(account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw NebulaAiSecrets.Failure.keychain(status)
        }
    }
}
