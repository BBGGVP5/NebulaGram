import Foundation

/// Local personal templates, independent of Telegram Business.
struct NebulaQuickReply: Codable, Equatable {
    var id: String
    var title: String
    var text: String
}

final class NebulaQuickReplyStore {
    static let maximumCount = 100
    static let maximumText = 4096
    static let maximumTitle = 80
    private let defaults: UserDefaults
    private let key: String
    init(account: String, defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.key = "nebula.quickReplies." + account
    }
    private func valid(_ entry: NebulaQuickReply) -> Bool {
        return !entry.id.isEmpty && entry.id.utf16.count <= 64
            && !entry.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !entry.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && entry.title.utf16.count <= Self.maximumTitle && entry.text.utf16.count <= Self.maximumText
    }
    func list() -> [NebulaQuickReply] {
        guard let data = defaults.data(forKey: key), data.count <= 3_000_000,
              let entries = try? JSONDecoder().decode([NebulaQuickReply].self, from: data) else { return [] }
        var ids = Set<String>()
        return Array(entries.filter { valid($0) && ids.insert($0.id).inserted }.prefix(Self.maximumCount))
    }
    @discardableResult func save(id: String?, title: String, text: String) -> Bool {
        let entry = NebulaQuickReply(id: id ?? UUID().uuidString, title: title.trimmingCharacters(in: .whitespacesAndNewlines), text: text)
        guard valid(entry) else { return false }
        var entries = list()
        if let id = id {
            guard let index = entries.firstIndex(where: { $0.id == id }) else { return false }
            entries[index] = entry
        } else {
            guard entries.count < Self.maximumCount else { return false }
            entries.append(entry)
        }
        return write(entries)
    }
    @discardableResult func delete(id: String) -> Bool { return write(list().filter { $0.id != id }) }
    private func write(_ entries: [NebulaQuickReply]) -> Bool {
        guard let data = try? JSONEncoder().encode(entries) else { return false }
        defaults.set(data, forKey: key); return true
    }
}
