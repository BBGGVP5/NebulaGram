import Foundation

/// Local Saved Messages index: label names and message IDs only, scoped to the real user ID.
public final class NebulaSavedTags {
    public struct Label: Codable, Equatable { public var id: String; public var name: String }
    private struct State: Codable { var labels: [Label] = []; var messages: [String: [String]] = [:] }
    public enum Failure: Error { case invalidAccount, invalidName, duplicate, damagedStorage, limit, missingLabel, invalidMessage }
    private let defaults: UserDefaults, key: String
    public init(accountId: String, defaults: UserDefaults = .standard) throws {
        guard let number = Int64(accountId), number > 0, String(number) == accountId else { throw Failure.invalidAccount }
        self.defaults = defaults; key = "nebula.savedLabels.v1." + accountId
    }
    public static func name(_ value: String) throws -> String {
        let name = value.trimmingCharacters(in: .whitespacesAndNewlines).precomposedStringWithCanonicalMapping
        guard !name.isEmpty, name.unicodeScalars.count <= 32, !name.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { throw Failure.invalidName }; return name
    }
    private func load() throws -> State {
        guard let raw = defaults.object(forKey: key) else { return State() }
        guard let data = raw as? Data, data.count <= 256000, let state = try? JSONDecoder().decode(State.self, from: data), state.labels.count <= 64, state.messages.count <= 3000 else { throw Failure.damagedStorage }
        let ids = Set(state.labels.map(\.id)); var names = Set<String>()
        guard ids.count == state.labels.count else { throw Failure.damagedStorage }
        for label in state.labels {
            guard label.id.range(of: #"^[A-Za-z0-9-]{1,64}$"#, options: .regularExpression) == (label.id.startIndex..<label.id.endIndex), (try? Self.name(label.name)) == label.name, names.insert(label.name.lowercased()).inserted else { throw Failure.damagedStorage }
        }
        for (key, values) in state.messages {
            guard let id = Int32(key), id > 0, String(id) == key, !values.isEmpty, values.count <= 8, Set(values).count == values.count, values.allSatisfy(ids.contains) else { throw Failure.damagedStorage }
        }
        return state
    }
    private func save(_ state: State) throws { let data = try JSONEncoder().encode(state); guard data.count <= 256000 else { throw Failure.limit }; defaults.set(data, forKey: key) }
    public var labels: [Label] { get throws { try load().labels } }
    public func assigned(message: Int32, label: String) throws -> Bool { try load().messages[String(message)]?.contains(label) ?? false }
    public func messages(label: String) throws -> [Int32] { try load().messages.filter { $0.value.contains(label) }.compactMap { Int32($0.key) }.sorted(by: >) }
    @discardableResult public func create(_ value: String) throws -> String {
        var state = try load(); let name = try Self.name(value)
        guard !state.labels.contains(where: { $0.name.lowercased() == name.lowercased() }) else { throw Failure.duplicate }
        guard state.labels.count < 64 else { throw Failure.limit }; let id = UUID().uuidString.lowercased(); state.labels.append(Label(id: id, name: name)); try save(state); return id
    }
    public func rename(id: String, name: String) throws {
        var state = try load(); let name = try Self.name(name)
        guard let index = state.labels.firstIndex(where: { $0.id == id }) else { throw Failure.missingLabel }
        guard !state.labels.contains(where: { $0.id != id && $0.name.lowercased() == name.lowercased() }) else { throw Failure.duplicate }; state.labels[index].name = name; try save(state)
    }
    public func remove(id: String) throws {
        var state = try load(); state.labels.removeAll { $0.id == id }
        for key in Array(state.messages.keys) { state.messages[key]?.removeAll { $0 == id }; if state.messages[key]?.isEmpty == true { state.messages.removeValue(forKey: key) } }; try save(state)
    }
    public func toggle(message: Int32, label: String) throws {
        var state = try load(); guard message > 0 else { throw Failure.invalidMessage }; guard state.labels.contains(where: { $0.id == label }) else { throw Failure.missingLabel }
        let key = String(message); var values = state.messages[key] ?? []
        if let index = values.firstIndex(of: label) { values.remove(at: index) } else { guard values.count < 8, state.messages[key] != nil || state.messages.count < 3000 else { throw Failure.limit }; values.append(label) }
        if values.isEmpty { state.messages.removeValue(forKey: key) } else { state.messages[key] = values }; try save(state)
    }
}
