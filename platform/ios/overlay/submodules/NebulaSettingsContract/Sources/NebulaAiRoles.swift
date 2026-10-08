import Foundation

public struct NebulaAiRole: Codable, Equatable {
    public var id: String
    public var name: String
    public var emoji: String
    public var instruction: String
    public init(id: String = UUID().uuidString, name: String, emoji: String, instruction: String) {
        self.id = id; self.name = name; self.emoji = emoji; self.instruction = instruction
    }
    public static func builtins(russian: Bool) -> [NebulaAiRole] {
        [NebulaAiRole(id: "assistant", name: russian ? "Помощник" : "Assistant", emoji: "🤖", instruction: russian ? "Ты персональный помощник. Отвечай понятно на языке пользователя." : "You are a personal assistant. Reply clearly in the user's language."),
         NebulaAiRole(id: "summary", name: russian ? "Краткие сводки" : "Summarizer", emoji: "🔎", instruction: russian ? "Кратко изложи текст на языке пользователя. Сохрани ключевые факты, даты и решения." : "Summarize in the user's language. Preserve key facts, dates and decisions."),
         NebulaAiRole(id: "proofreader", name: russian ? "Корректор" : "Proofreader", emoji: "📝", instruction: russian ? "Исправь ошибки, сохранив смысл, тон и язык автора. Верни исправленный текст." : "Proofread while preserving the author's meaning, tone and language. Return the corrected text.")]
    }
    public var valid: Bool { !id.isEmpty && id.count <= 80 && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && name.count <= 80 && emoji.count <= 8 && !instruction.isEmpty && instruction.count <= 8192 }
}

public final class NebulaAiRoles {
    private let defaults: UserDefaults
    private let key = "nebula.ai.roles.v1"
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public var custom: [NebulaAiRole] {
        guard let data = defaults.data(forKey: key), data.count <= 1_000_000,
              let roles = try? JSONDecoder().decode([NebulaAiRole].self, from: data) else { return [] }
        guard roles.count <= 32, roles.allSatisfy({ $0.valid }), Set(roles.map(\.id)).count == roles.count else { return [] }
        return roles
    }
    public var hasLoadError: Bool {
        guard defaults.object(forKey: key) != nil else { return false }
        guard let data = defaults.data(forKey: key), data.count <= 1_000_000,
              let roles = try? JSONDecoder().decode([NebulaAiRole].self, from: data) else { return true }
        return roles.count > 32 || !roles.allSatisfy({ $0.valid }) || Set(roles.map(\.id)).count != roles.count
    }
    public var selectedId: String {
        get { defaults.string(forKey: "nebula.ai.role.v1") ?? "" }
        set { defaults.set(newValue, forKey: "nebula.ai.role.v1") }
    }
    public func selected(russian: Bool) -> NebulaAiRole? {
        (NebulaAiRole.builtins(russian: russian) + custom).first { $0.id == selectedId }
    }
    public func save(_ role: NebulaAiRole) throws {
        guard !hasLoadError else { throw NebulaAiServices.Failure.damagedStorage }
        guard role.valid && !NebulaAiRole.builtins(russian: false).contains(where: { $0.id == role.id }) else { throw NebulaAiServices.Failure.invalidConnection }
        var values = custom.filter { $0.id != role.id }
        guard values.count < 32 else { throw NebulaAiServices.Failure.limitReached }
        values.append(role); defaults.set(try JSONEncoder().encode(values), forKey: key)
    }
    public func remove(_ id: String) throws {
        guard !hasLoadError else { throw NebulaAiServices.Failure.damagedStorage }
        defaults.set(try JSONEncoder().encode(custom.filter { $0.id != id }), forKey: key)
        if selectedId == id { selectedId = "" }
    }
}
