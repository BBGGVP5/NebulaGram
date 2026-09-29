import Foundation

public struct NebulaAiChatTurn: Codable, Equatable {
    public let input: String
    public let output: String
    public init(input: String, output: String) {
        self.input = String(input.prefix(8_000))
        self.output = String(output.prefix(8_000))
    }
}

public struct NebulaAiChatSession: Codable, Equatable, Identifiable {
    public let id: UUID
    public var title: String
    public var identity: String
    public var turns: [NebulaAiChatTurn]
    public init(id: UUID = UUID(), title: String = "", identity: String = "", turns: [NebulaAiChatTurn] = []) {
        self.id = id; self.title = title; self.identity = identity; self.turns = turns
    }
}

/// Chat switching works in memory. Disk persistence uses the existing AI-history opt-in.
public final class NebulaAiChats {
    public static let shared = NebulaAiChats()
    private static let storageKey = "app.nebulagram.ai.chats.v1"
    private static let selectedKey = "app.nebulagram.ai.chats.selected.v1"
    private let defaults: UserDefaults
    private let settings: NebulaAiSettings
    private let lock = NSLock()
    private var chats: [NebulaAiChatSession] = []
    private var selected: UUID?
    private var loaded = false

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults; self.settings = NebulaAiSettings(defaults: defaults)
    }
    private func load() {
        guard !loaded else { return }
        loaded = true
        if settings.historyEnabled, let data = defaults.data(forKey: Self.storageKey), data.count <= 2_000_000,
           let saved = try? JSONDecoder().decode([NebulaAiChatSession].self, from: data) {
            chats = Array(saved.prefix(20))
            selected = defaults.string(forKey: Self.selectedKey).flatMap(UUID.init(uuidString:))
        }
        if selected == nil || !chats.contains(where: { $0.id == selected }) {
            if chats.isEmpty { chats = [NebulaAiChatSession()] }
            selected = chats[0].id
        }
    }
    public func list() -> [NebulaAiChatSession] {
        lock.lock(); defer { lock.unlock() }; load(); return chats
    }
    public func current() -> NebulaAiChatSession {
        lock.lock(); defer { lock.unlock() }; load()
        return chats.first(where: { $0.id == selected }) ?? chats[0]
    }
    @discardableResult public func fresh() -> NebulaAiChatSession {
        lock.lock(); defer { lock.unlock() }; load()
        if let chat = chats.first(where: { $0.id == selected }), chat.turns.isEmpty { return chat }
        let chat = NebulaAiChatSession(); chats.insert(chat, at: 0); selected = chat.id
        persist(); return chat
    }
    @discardableResult public func select(_ id: UUID) -> NebulaAiChatSession? {
        lock.lock(); defer { lock.unlock() }; load()
        guard let chat = chats.first(where: { $0.id == id }) else { return nil }
        selected = id; persist(); return chat
    }
    public func append(id: UUID, identity: String, input: String, output: String) {
        lock.lock(); defer { lock.unlock() }; load()
        guard let index = chats.firstIndex(where: { $0.id == id }) else { return }
        var chat = chats.remove(at: index)
        chat.identity = String(identity.prefix(512))
        if chat.title.isEmpty { chat.title = String(input.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\n", with: " ").prefix(48)) }
        chat.turns.append(NebulaAiChatTurn(input: input, output: output))
        if chat.turns.count > 20 { chat.turns.removeFirst(chat.turns.count - 20) }
        chats.insert(chat, at: 0)
        if chats.count > 20 { chats.removeLast(chats.count - 20) }
        persist()
    }
    public func clear() {
        lock.lock(); defer { lock.unlock() }
        chats = [NebulaAiChatSession()]; selected = chats[0].id; loaded = true
        defaults.removeObject(forKey: Self.storageKey)
        defaults.removeObject(forKey: Self.selectedKey)
    }
    private func persist() {
        guard settings.historyEnabled else { return }
        let saved = chats.filter { !$0.turns.isEmpty }
        guard let data = try? JSONEncoder().encode(saved), data.count <= 2_000_000 else { return }
        defaults.set(data, forKey: Self.storageKey)
        defaults.set(selected?.uuidString, forKey: Self.selectedKey)
    }
}
