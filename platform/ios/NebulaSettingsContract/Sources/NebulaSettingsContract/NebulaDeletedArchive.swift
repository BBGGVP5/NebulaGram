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

public struct NebulaDeletedKey: Hashable {
    public let peer: Int64
    public let id: Int32
    public let namespace: Int32
    public init(peer: Int64, id: Int32, namespace: Int32 = 0) { self.peer = peer; self.id = id; self.namespace = namespace }
}
public struct NebulaDeletedSnapshot {
    public let entries: [NebulaDeletedEntry]
    public let keys: Set<NebulaDeletedKey>
    public init(_ entries: [NebulaDeletedEntry]) {
        self.entries = entries
        self.keys = Set(entries.map { NebulaDeletedKey(peer: $0.peer, id: $0.id, namespace: $0.namespace ?? 0) })
    }
}
public enum NebulaRetentionScope: String, CaseIterable { case privateChats, groups, channels, bots, saved }

/// Separate, opt-in per-account archive. Never puts a deleted message back into Telegram.
public final class NebulaDeletedArchive {
    public static let shared = NebulaDeletedArchive()
    /// Ноль — без ограничения: архив живёт, пока его не очистит сам пользователь.
    public static let unlimited: TimeInterval = 0
    private let queue = DispatchQueue(label: "app.nebulagram.deleted-archive")
    private let stateLock = NSRecursiveLock()
    private let keyLock = NSLock()
    private var cached: [Int64: NebulaDeletedSnapshot] = [:]
    private var pending: [Int64: NebulaDeletedSnapshot] = [:]
    private var writing = Set<Int64>()
    private var nextPrune: [Int64: TimeInterval] = [:]
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
        keyLock.lock(); defer { keyLock.unlock() }
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
        // Признак испорченного файла, а не квота хранения: архив читается в
        // память целиком, и без потолка испорченная длина стала бы нехваткой
        // памяти. Числом записей и сроком архив не ограничен.
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= 64 * 1024 * 1024 else { throw Failure.invalidArchive }
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
    /// Ноль — хранить бессрочно; это и есть значение по умолчанию.
    public static let retentionChoices = [0, 1, 7, 30]
    public func retentionDays(account: Int64) -> Int {
        let value = defaults.integer(forKey: "nebula.privacy.retentionDays.\(account)")
        return Self.retentionChoices.contains(value) ? value : 0
    }
    public func setRetentionDays(account: Int64, value: Int) {
        guard Self.retentionChoices.contains(value) else { return }
        defaults.set(value, forKey: "nebula.privacy.retentionDays.\(account)")
    }
    public func excluded(account: Int64, peer: Int64) -> Bool {
        (defaults.stringArray(forKey: "nebula.privacy.excluded.\(account)") ?? []).contains(String(peer))
    }
    public func setExcluded(account: Int64, peer: Int64, value: Bool) {
        let key = "nebula.privacy.excluded.\(account)"
        var peers = Set(defaults.stringArray(forKey: key) ?? [])
        if value { peers.insert(String(peer)) } else { peers.remove(String(peer)) }
        defaults.set(peers.sorted(), forKey: key)
    }
    public func prunedForAccount(_ entries: [NebulaDeletedEntry], account: Int64, now: TimeInterval = Date().timeIntervalSince1970) -> [NebulaDeletedEntry] {
        Self.pruned(entries, now: now, retention: TimeInterval(retentionDays(account: account)) * 86400)
    }
    public func saveSecret(account: Int64) -> Bool { defaults.bool(forKey: "nebula.privacy.secret.\(account)") }
    public func setSaveSecret(account: Int64, value: Bool) { defaults.set(value, forKey: "nebula.privacy.secret.\(account)") }
    public func saveExpiring(account: Int64) -> Bool { defaults.bool(forKey: "nebula.privacy.expiring.\(account)") }
    public func setSaveExpiring(account: Int64, value: Bool) { defaults.set(value, forKey: "nebula.privacy.expiring.\(account)") }
    public func scopeEnabled(account: Int64, scope: NebulaRetentionScope) -> Bool {
        defaults.object(forKey: "nebula.privacy.scope.\(account).\(scope.rawValue)") as? Bool ?? true
    }
    public func setScopeEnabled(account: Int64, scope: NebulaRetentionScope, value: Bool) {
        defaults.set(value, forKey: "nebula.privacy.scope.\(account).\(scope.rawValue)")
    }
    public func hasArchive(account: Int64) -> Bool {
        stateLock.lock(); defer { stateLock.unlock() }
        if cached[account] != nil { return true }
        guard account != 0, let directory = directory else { return false }
        return FileManager.default.fileExists(atPath: directory.appendingPathComponent("\(account).enc").path)
    }
    public func snapshot(account: Int64) throws -> NebulaDeletedSnapshot {
        stateLock.lock(); defer { stateLock.unlock() }
        if let value = cached[account] { return value }
        let value = NebulaDeletedSnapshot(try read(account))
        cached[account] = value
        return value
    }
    public func shouldPrune(account: Int64, now: TimeInterval = Date().timeIntervalSince1970) -> Bool {
        guard retentionDays(account: account) > 0 else { return false }
        stateLock.lock(); defer { stateLock.unlock() }
        guard now >= (nextPrune[account] ?? 0) else { return false }
        nextPrune[account] = now + 60
        return true
    }
    /// Publish immediately; encode/encrypt/fsync on the archive worker, not a Postbox transaction.
    public func scheduleReplace(_ entries: [NebulaDeletedEntry], account: Int64) {
        let next = NebulaDeletedSnapshot(entries)
        stateLock.lock()
        cached[account] = next
        pending[account] = next
        let start = writing.insert(account).inserted
        stateLock.unlock()
        if start { queue.async { self.drain(account: account) } }
    }
    private func drain(account: Int64) {
        while true {
            stateLock.lock()
            let next = pending.removeValue(forKey: account)
            if next == nil { writing.remove(account) }
            stateLock.unlock()
            guard let next = next else { return }
            do {
                try write(next.entries, account: account)
                defaults.removeObject(forKey: "nebula.privacy.archiveError.\(account)")
            } catch {
                // Keep the newest in-memory snapshot after failure; a later change retries it.
                defaults.set(true, forKey: "nebula.privacy.archiveError.\(account)")
            }
        }
    }
    public func replace(_ entries: [NebulaDeletedEntry], account: Int64) throws {
        scheduleReplace(entries, account: account)
        queue.sync { }
        if hasCaptureError(account: account) { throw Failure.unavailable }
    }
    /// Ничего не выбрасывает по количеству. По сроку — только если пользователь
    /// сам задал срок; записи из будущего пришли бы из испорченного файла.
    public static func pruned(_ entries: [NebulaDeletedEntry], now: TimeInterval = Date().timeIntervalSince1970, retention: TimeInterval = NebulaDeletedArchive.unlimited) -> [NebulaDeletedEntry] {
        entries.filter { (retention <= 0 || $0.deletedAt >= now - retention) && $0.deletedAt <= now + 60 }
            .sorted { $0.deletedAt > $1.deletedAt }
    }
    public func entries(account: Int64) throws -> [NebulaDeletedEntry] {
        try snapshot(account: account).entries
    }
    public func hasCaptureError(account: Int64) -> Bool { defaults.bool(forKey: "nebula.privacy.archiveError.\(account)") }
    public func clear(account: Int64) throws {
        try replace([], account: account)
    }
}
