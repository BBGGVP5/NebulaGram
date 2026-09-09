import Foundation
import CryptoKit
import Security

public struct NebulaDeletedEntry: Codable, Equatable {
    public let peer: Int64
    public let id: Int32
    public let timestamp: Int32
    public let deletedAt: TimeInterval
    public let text: String
    public let namespace: Int32?
    public init(peer: Int64, id: Int32, timestamp: Int32, deletedAt: TimeInterval = Date().timeIntervalSince1970, text: String, namespace: Int32? = nil) {
        self.namespace = namespace
        self.peer = peer; self.id = id; self.timestamp = timestamp; self.deletedAt = deletedAt
        self.text = String(text.prefix(4096))
    }
}

/// Separate, opt-in per-account archive. Never puts a deleted message back into Telegram.
public final class NebulaDeletedArchive {
    public static let shared = NebulaDeletedArchive()
    public static let limit = 500
    public static let retention: TimeInterval = 7 * 24 * 60 * 60
    private let queue = DispatchQueue(label: "app.nebulagram.deleted-archive")
    private let defaults: UserDefaults
    private let directory: URL?
    private let testKey: Data?
    public enum Failure: Error { case unavailable, invalidArchive }

    public init(defaults: UserDefaults = .standard, directory: URL? = nil, key: Data? = nil) {
        self.defaults = defaults
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?.appendingPathComponent("NebulaDeletedArchive", isDirectory: true)
        self.testKey = key
    }
    private func setting(_ account: Int64) -> String { "nebula.privacy.deleted.\(account)" }
    public func enabled(account: Int64) -> Bool { account != 0 && defaults.bool(forKey: setting(account)) }
    public func setEnabled(account: Int64, value: Bool) { defaults.set(value, forKey: setting(account)) }
    private func key() throws -> SymmetricKey {
        if let testKey = testKey { return SymmetricKey(data: testKey) }
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "app.nebulagram.deleted-archive", kSecAttrAccount as String: "encryption-v1"]
        var read = query; read[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(read as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data, data.count == 32 { return SymmetricKey(data: data) }
        guard status == errSecItemNotFound else { throw Failure.unavailable }
        let key = SymmetricKey(size: .bits256)
        let data = key.withUnsafeBytes { Data($0) }
        var insert = query; insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw Failure.unavailable }
        return key
    }
    private func file(_ account: Int64) throws -> URL {
        guard account != 0, var directory = directory else { throw Failure.unavailable }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        return directory.appendingPathComponent("\(account).enc")
    }
    private func read(_ account: Int64) throws -> [NebulaDeletedEntry] {
        let file = try file(account)
        guard FileManager.default.fileExists(atPath: file.path) else { return [] }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= 10 * 1024 * 1024 else { throw Failure.invalidArchive }
        let bytes = try Data(contentsOf: file)
        let plain = try AES.GCM.open(AES.GCM.SealedBox(combined: bytes), using: key(), authenticating: Data(String(account).utf8))
        return try JSONDecoder().decode([NebulaDeletedEntry].self, from: plain)
    }
    private func write(_ entries: [NebulaDeletedEntry], account: Int64) throws {
        let plain = try JSONEncoder().encode(entries)
        guard let sealed = try AES.GCM.seal(plain, using: key(), authenticating: Data(String(account).utf8)).combined else { throw Failure.unavailable }
        let file = try file(account)
        try sealed.write(to: file, options: .atomic)
        #if os(iOS)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: file.path)
        #endif
    }
    public var icon: String {
        get { defaults.string(forKey: "nebula.privacy.deletedIcon") ?? "🗑" }
        set {
            let text = String(newValue.trimmingCharacters(in: .whitespacesAndNewlines).prefix(4))
            if !text.isEmpty { defaults.set(text, forKey: "nebula.privacy.deletedIcon") }
        }
    }
    public func saveSecret(account: Int64) -> Bool { defaults.bool(forKey: "nebula.privacy.secret.\(account)") }
    public func setSaveSecret(account: Int64, value: Bool) { defaults.set(value, forKey: "nebula.privacy.secret.\(account)") }
    public func saveExpiring(account: Int64) -> Bool { defaults.bool(forKey: "nebula.privacy.expiring.\(account)") }
    public func setSaveExpiring(account: Int64, value: Bool) { defaults.set(value, forKey: "nebula.privacy.expiring.\(account)") }
    public func replace(_ entries: [NebulaDeletedEntry], account: Int64) throws {
        try queue.sync { try write(entries, account: account) }
    }
    public static func pruned(_ entries: [NebulaDeletedEntry], now: TimeInterval = Date().timeIntervalSince1970) -> [NebulaDeletedEntry] {
        Array(entries.filter { $0.deletedAt >= now - retention && $0.deletedAt <= now + 60 }.sorted { $0.deletedAt > $1.deletedAt }.prefix(limit))
    }
    public func entries(account: Int64) throws -> [NebulaDeletedEntry] {
        try queue.sync { try read(account) }
    }
    public func hasCaptureError(account: Int64) -> Bool { defaults.bool(forKey: "nebula.privacy.archiveError.\(account)") }
    public func clear(account: Int64) throws {
        try queue.sync {
            let file = try file(account)
            if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) }
            defaults.removeObject(forKey: "nebula.privacy.archiveError.\(account)")
        }
    }
}
