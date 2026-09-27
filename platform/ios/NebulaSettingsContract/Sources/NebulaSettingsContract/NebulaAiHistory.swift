import Foundation

/// One explicitly requested AI exchange. It contains no Telegram peer or message IDs.
public struct NebulaAiHistoryEntry: Codable, Equatable, Identifiable {
    public let id: UUID
    public let provider: String
    public let input: String
    public let output: String
    public let timestamp: Date

    public init(id: UUID = UUID(), provider: String, input: String, output: String, timestamp: Date = Date()) {
        self.id = id
        self.provider = String(provider.prefix(80))
        self.input = String(input.prefix(8_000))
        self.output = String(output.prefix(8_000))
        self.timestamp = timestamp
    }
}

/// Bounded, opt-in local history. It is stored separately from transferable
/// presentation settings and never synchronizes Telegram messages.
public final class NebulaAiHistory {
    public static let shared = NebulaAiHistory()
    public static let maximumEntries = 40
    public static let maximumStorageBytes = 512 * 1024
    private static let storageKey = "app.nebulagram.ai.history.v1"

    private let defaults: UserDefaults
    private let settings: NebulaAiSettings
    private let lock = NSLock()

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.settings = NebulaAiSettings(defaults: defaults)
    }

    public var isEnabled: Bool {
        get { settings.historyEnabled }
        set { settings.historyEnabled = newValue }
    }

    public func entries() -> [NebulaAiHistoryEntry] {
        lock.lock(); defer { lock.unlock() }
        guard let data = defaults.data(forKey: Self.storageKey),
              data.count <= Self.maximumStorageBytes,
              let values = try? JSONDecoder().decode([NebulaAiHistoryEntry].self, from: data) else { return [] }
        return values.sorted { $0.timestamp > $1.timestamp }
    }

    @discardableResult
    public func append(provider: String, input: String, output: String, timestamp: Date = Date()) -> Bool {
        let input = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let output = output.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isEnabled, !input.isEmpty, !output.isEmpty else { return false }
        lock.lock(); defer { lock.unlock() }
        var values = readUnlocked()
        values.insert(NebulaAiHistoryEntry(provider: provider, input: input, output: output, timestamp: timestamp), at: 0)
        if values.count > Self.maximumEntries { values = Array(values.prefix(Self.maximumEntries)) }
        while let data = try? JSONEncoder().encode(values), data.count > Self.maximumStorageBytes, !values.isEmpty {
            values.removeLast()
        }
        guard let data = try? JSONEncoder().encode(values), data.count <= Self.maximumStorageBytes else { return false }
        defaults.set(data, forKey: Self.storageKey)
        return true
    }

    public func clear() {
        lock.lock(); defer { lock.unlock() }
        defaults.removeObject(forKey: Self.storageKey)
    }

    private func readUnlocked() -> [NebulaAiHistoryEntry] {
        guard let data = defaults.data(forKey: Self.storageKey),
              data.count <= Self.maximumStorageBytes,
              let values = try? JSONDecoder().decode([NebulaAiHistoryEntry].self, from: data) else { return [] }
        return values.sorted { $0.timestamp > $1.timestamp }
    }
}
