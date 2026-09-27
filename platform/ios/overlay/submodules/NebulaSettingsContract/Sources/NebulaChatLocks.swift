import Foundation
import Security
import CommonCrypto

/// Device-local locks. The verifier and retry state live in a separate,
/// ThisDeviceOnly Keychain service; settings export cannot include them.
public final class NebulaChatLocks {
    public static let shared = NebulaChatLocks()
    public enum Mode: String, Codable { case pin, password }
    public enum Failure: Error { case invalidCredential, incorrectCredential, rateLimited, unavailable }

    private struct Record: Codable {
        let version: Int
        let mode: Mode
        let salt: Data
        let verifier: Data
        var attempts: Int
        var retryAfter: Date?
    }

    private let storage: NebulaSecretStorage
    private let clock: () -> Date
    private let lock = NSLock()

    public init(storage: NebulaSecretStorage? = nil, clock: @escaping () -> Date = Date.init) {
        self.storage = storage ?? NebulaKeychainStorage(service: "app.nebulagram.chatlocks")
        self.clock = clock
    }

    private func key(account: Int64, peer: Int64) throws -> String {
        guard account != 0, peer != 0 else { throw Failure.unavailable }
        return "v1:\(account):\(peer)"
    }

    public func hasLock(account: Int64, peer: Int64) throws -> Bool {
        lock.lock(); defer { lock.unlock() }
        return try storage.secret(for: key(account: account, peer: peer)) != nil
    }

    public func mode(account: Int64, peer: Int64) throws -> Mode? {
        lock.lock(); defer { lock.unlock() }
        return try read(account: account, peer: peer)?.mode
    }

    public func verify(account: Int64, peer: Int64, credential: String) throws -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard var record = try read(account: account, peer: peer) else { return true }
        return try check(credential, record: &record, account: account, peer: peer)
    }

    public func set(account: Int64, peer: Int64, current: String?, new: String, mode: Mode) throws {
        guard Self.valid(new, mode: mode) else { throw Failure.invalidCredential }
        lock.lock(); defer { lock.unlock() }
        if var existing = try read(account: account, peer: peer) {
            guard let current = current, try check(current, record: &existing, account: account, peer: peer) else {
                throw Failure.incorrectCredential
            }
        }
        var salt = Data(count: 16)
        let randomStatus = salt.withUnsafeMutableBytes { bytes in
            SecRandomCopyBytes(kSecRandomDefault, bytes.count, bytes.baseAddress!)
        }
        guard randomStatus == errSecSuccess else { throw Failure.unavailable }
        let record = Record(version: 1, mode: mode, salt: salt,
                            verifier: try Self.derive(new, salt: salt), attempts: 0, retryAfter: nil)
        try write(record, account: account, peer: peer)
    }

    public func remove(account: Int64, peer: Int64, current: String) throws {
        lock.lock(); defer { lock.unlock() }
        guard var record = try read(account: account, peer: peer) else { return }
        guard try check(current, record: &record, account: account, peer: peer) else {
            throw Failure.incorrectCredential
        }
        try storage.removeSecret(for: key(account: account, peer: peer))
    }

    private func read(account: Int64, peer: Int64) throws -> Record? {
        guard let value = try storage.secret(for: key(account: account, peer: peer)) else { return nil }
        guard let record = try? JSONDecoder().decode(Record.self, from: Data(value.utf8)),
              record.version == 1, record.salt.count == 16, record.verifier.count == 32 else {
            throw Failure.unavailable
        }
        return record
    }

    private func write(_ record: Record, account: Int64, peer: Int64) throws {
        guard let value = String(data: try JSONEncoder().encode(record), encoding: .utf8) else { throw Failure.unavailable }
        try storage.setSecret(value, for: key(account: account, peer: peer))
    }

    private func check(_ input: String, record: inout Record, account: Int64, peer: Int64) throws -> Bool {
        if let until = record.retryAfter, until > clock() { throw Failure.rateLimited }
        let candidate = try Self.derive(input, salt: record.salt)
        let valid = Self.equal(candidate, record.verifier)
        if valid { record.attempts = 0; record.retryAfter = nil }
        else {
            record.attempts += 1
            if record.attempts >= 5 { record.attempts = 0; record.retryAfter = clock().addingTimeInterval(30) }
        }
        try write(record, account: account, peer: peer)
        return valid
    }

    private static func valid(_ value: String, mode: Mode) -> Bool {
        switch mode {
        case .pin: return (4...12).contains(value.count) && value.utf8.allSatisfy { (48...57).contains($0) }
        case .password: return (6...128).contains(value.count)
        }
    }

    private static func derive(_ input: String, salt: Data) throws -> Data {
        var output = Data(count: 32)
        let status = input.utf8CString.withUnsafeBufferPointer { password in
            salt.withUnsafeBytes { saltBytes in
                output.withUnsafeMutableBytes { result in
                    CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), password.baseAddress,
                        password.count - 1, saltBytes.bindMemory(to: UInt8.self).baseAddress,
                        salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 120_000,
                        result.bindMemory(to: UInt8.self).baseAddress, 32)
                }
            }
        }
        guard status == kCCSuccess else { throw Failure.unavailable }
        return output
    }

    private static func equal(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var difference: UInt8 = 0
        for (left, right) in zip(a, b) { difference |= left ^ right }
        return difference == 0
    }
}
