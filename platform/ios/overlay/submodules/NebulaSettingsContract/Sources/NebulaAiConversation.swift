import Foundation

/// Successful turns only; never loads persisted history into a new conversation.
public struct NebulaAiConversation {
    private var turns: [(String, String)] = []
    private var identity = ""
    public init() {}
    public mutating func select(_ value: String) {
        if value != identity { clear(); identity = value }
    }
    public mutating func clear() { turns.removeAll() }
    public mutating func append(input: String, output: String) {
        turns.append((input, output))
        if turns.count > 6 { turns.removeFirst(turns.count - 6) }
    }
    public func request(_ input: String, limit: Int) -> String? {
        guard input.count <= limit else { return nil }
        let suffix = "\n\nUser: " + input
        var prior = ""
        for (question, answer) in turns.reversed() {
            let part = "User: \(question)\nAssistant: \(answer)\n\n"
            guard part.count + prior.count + suffix.count + 40 <= limit else { break }
            prior = part + prior
        }
        return prior.isEmpty ? input : "Previous conversation:\n" + prior + suffix
    }
}

/// Invalidate before cancelling: a late completion must never finish a newer request.
public struct NebulaAiRequestGate {
    public private(set) var current: UUID?
    public init() {}
    public mutating func begin() -> UUID { let id = UUID(); current = id; return id }
    public mutating func cancel() { current = nil }
    public func accepts(_ id: UUID) -> Bool { current == id }
}
