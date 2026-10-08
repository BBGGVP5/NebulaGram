import Foundation

public struct NebulaAiConnection: Codable, Equatable {
    public var id: String
    public var name: String
    public var provider: NebulaAiProvider
    public var model: String
    public var endpoint: String
    public init(id: String = UUID().uuidString, name: String, provider: NebulaAiProvider, model: String = "", endpoint: String = "") {
        self.id = id; self.name = name; self.provider = provider; self.model = model; self.endpoint = endpoint
    }
    public var url: URL? {
        let value = provider == .custom ? endpoint : provider.endpoint ?? ""
        guard let url = URL(string: value), url.scheme?.lowercased() == "https", url.host?.isEmpty == false,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { return nil }
        return url
    }
    public var valid: Bool {
        !id.isEmpty && id.count <= 80 && id.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }
        && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && name.count <= 80
        && model.count <= 256 && endpoint.count <= 1000
        && (provider == .appleIntelligence || url != nil && !model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
}

/// Non-secret connection metadata. Credentials never enter this document.
public final class NebulaAiServices {
    public enum Failure: Error { case invalidConnection, damagedStorage, limitReached, missingConnection }
    private let defaults: UserDefaults
    private let key = "nebula.ai.services.v1"
    private let selectionKey = "nebula.ai.selectedService.v1"
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public var connections: [NebulaAiConnection] {
        guard let data = defaults.data(forKey: key), data.count <= 128_000,
              let values = try? JSONDecoder().decode([NebulaAiConnection].self, from: data), values.count <= 32,
              values.allSatisfy({ $0.valid }), Set(values.map(\.id)).count == values.count else { return [] }
        return values
    }
    public var hasLoadError: Bool {
        guard defaults.object(forKey: key) != nil else { return false }
        guard let data = defaults.data(forKey: key), data.count <= 128_000,
              let values = try? JSONDecoder().decode([NebulaAiConnection].self, from: data) else { return true }
        return values.count > 32 || !values.allSatisfy({ $0.valid }) || Set(values.map(\.id)).count != values.count
    }
    public var active: NebulaAiConnection? { connections.first { $0.id == defaults.string(forKey: selectionKey) } }
    public func select(_ id: String) throws {
        guard !hasLoadError else { throw Failure.damagedStorage }
        guard connections.contains(where: { $0.id == id }) else { throw Failure.missingConnection }
        defaults.set(id, forKey: selectionKey)
    }
    public func save(_ connection: NebulaAiConnection, apiKey: String?, secrets: NebulaAiSecrets) throws {
        guard !hasLoadError else { throw Failure.damagedStorage }
        guard connection.valid else { throw Failure.invalidConnection }
        var values = connections
        if let index = values.firstIndex(where: { $0.id == connection.id }) {
            let previous = values[index]
            guard apiKey != nil || (previous.provider == connection.provider && previous.url?.host == connection.url?.host) else { throw Failure.invalidConnection }
            values[index] = connection
        }
        else { guard values.count < 32 else { throw Failure.limitReached }; values.append(connection) }
        let data = try JSONEncoder().encode(values)
        if let apiKey { try secrets.setServiceKey(apiKey, id: connection.id) }
        defaults.set(data, forKey: key)
    }
    public func remove(_ id: String, secrets: NebulaAiSecrets) throws {
        guard !hasLoadError else { throw Failure.damagedStorage }
        let values = connections.filter { $0.id != id }
        let data = try JSONEncoder().encode(values)
        try secrets.removeServiceKey(id: id)
        defaults.set(data, forKey: key)
        // Deletion never silently sends the next request to a different service.
        if defaults.string(forKey: selectionKey) == id { defaults.removeObject(forKey: selectionKey) }
    }
    public var migrated: Bool { defaults.object(forKey: key) != nil }
    public func migrateLegacy(secrets: NebulaAiSecrets) throws {
        guard !migrated else { return }
        var values: [NebulaAiConnection] = []
        let selected = NebulaAiProvider(rawValue: defaults.integer(forKey: "nebula.ai.provider")) ?? .openAI
        for provider in NebulaAiProvider.allCases {
            let model = defaults.string(forKey: "nebula.ai.model_\(provider.rawValue)") ?? ""
            let endpoint = defaults.string(forKey: "nebula.ai.endpoint") ?? ""
            let secret = try secrets.key(for: provider)
            guard !model.isEmpty || secret != nil || provider == .appleIntelligence && selected == provider else { continue }
            let value = NebulaAiConnection(id: "legacy-\(provider.rawValue)", name: provider.title, provider: provider, model: model, endpoint: provider == .custom ? endpoint : "")
            guard value.valid else { continue } // Incomplete legacy fields remain available for recovery.
            if let secret { try secrets.setServiceKey(secret, id: value.id) }
            values.append(value)
        }
        defaults.set(try JSONEncoder().encode(values), forKey: key)
        if let value = values.first(where: { $0.provider == selected }) { defaults.set(value.id, forKey: selectionKey) }
    }
}
