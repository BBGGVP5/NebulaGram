import Foundation

public struct NebulaCloudSettingsDocument: Codable, Equatable {
    public static let marker = "#NebulaGramSettingsV1\n"
    public let version: Int
    public let device: String
    public let clock: [String: Int]
    public let settings: [String: SettingValue]
    public init(device: String, clock: [String: Int], settings: [String: SettingValue]) throws {
        version = 1; self.device = device; self.clock = clock; self.settings = settings
        try validate()
    }
    private func validate() throws {
        func validId(_ id: String) -> Bool { id.count == 36 && id == id.lowercased() && UUID(uuidString: id) != nil }
        guard version == 1, validId(device), clock.count <= 24, clock[device] != nil,
              clock.allSatisfy({ validId($0.key) && (1...1_000_000_000).contains($0.value) }) else { throw ContractError.invalidDocument }
        try SettingsCatalog.bundled().validate(SettingsDocument(settings: settings))
    }
    public static func parse(_ text: String) throws -> Self {
        guard text.utf16.count <= 4000, text.hasPrefix(marker) else { throw ContractError.invalidDocument }
        let value = try JSONDecoder().decode(Self.self, from: Data(text.dropFirst(marker.count).utf8))
        try value.validate(); return value
    }
    public func encode() throws -> String {
        try validate()
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let result = Self.marker + String(decoding: try encoder.encode(self), as: UTF8.self)
        guard result.utf16.count <= 4000 else { throw ContractError.invalidDocument }; return result
    }
    public func dominates(_ other: Self) -> Bool {
        let keys = Set(clock.keys).union(other.clock.keys)
        return keys.allSatisfy { clock[$0, default: 0] >= other.clock[$0, default: 0] }
            && keys.contains { clock[$0, default: 0] > other.clock[$0, default: 0] }
    }
    public static func heads(_ documents: [Self]) -> [Self] {
        documents.filter { candidate in !documents.contains { $0.dominates(candidate) } }
    }
    public static func next(device: String, documents: [Self], settings: [String: SettingValue]) throws -> Self {
        var clock: [String: Int] = [:]
        for doc in documents { for (id, count) in doc.clock { clock[id] = max(clock[id, default: 0], count) } }
        clock[device, default: 0] += 1
        return try Self(device: device, clock: clock, settings: settings)
    }
    public enum Decision: Equatable { case unchanged, publish, apply(NebulaCloudSettingsDocument), conflict, deleted }
    public static func reconcile(local: [String: SettingValue], base: [String: SettingValue]?, documents: [Self]) -> Decision {
        let heads = heads(documents)
        guard let remote = heads.first else { return base == nil ? .publish : .deleted }
        guard heads.allSatisfy({ $0.settings == remote.settings }) else { return .conflict }
        if local == remote.settings { return .unchanged }
        if base == local { return .apply(remote) }
        if base == remote.settings { return .publish }
        return .conflict
    }
}
