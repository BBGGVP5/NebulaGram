import Foundation

public struct NebulaFilterRules: Codable, Equatable {
    public var enabled = true
    public var words = ""
    public var transliterate = false
    public var wholeWords = false
    public var blockedAuthors = false
    public var excludedPeers: Set<Int64> = []
    public init() {}
    public func matches(_ text: String) -> Bool {
        guard enabled, !words.isEmpty else { return false }
        func normalize(_ value: String) -> String {
            let lower = value.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            return transliterate ? (lower.applyingTransform(.toLatin, reverse: false) ?? lower).folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "en_US_POSIX")) : lower
        }
        let input = normalize(String(text.prefix(50000)))
        for line in words.split(separator: "\n").prefix(200) {
            let term = normalize(String(line).trimmingCharacters(in: .whitespacesAndNewlines))
            guard !term.isEmpty else { continue }
            if !wholeWords { if input.contains(term) { return true } }
            else {
                let pattern = "(?<![\\p{L}\\p{N}_])" + NSRegularExpression.escapedPattern(for: term) + "(?![\\p{L}\\p{N}_])"
                if input.range(of: pattern, options: .regularExpression) != nil { return true }
            }
        }
        return false
    }
}

/// Rules are account-scoped. Reveals last for the process and never mutate history.
public final class NebulaMessageFilter {
    public static let shared = NebulaMessageFilter()
    public static let changed = Notification.Name("NebulaMessageFilterChanged")
    private let defaults: UserDefaults
    private let lock = NSLock()
    private var reveals: Set<String> = []
    private var blocked: [Int64: Set<Int64>] = [:]
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public func rules(account: Int64) -> NebulaFilterRules {
        guard let data = defaults.data(forKey: "nebula.filter.\(account)"), let rules = try? JSONDecoder().decode(NebulaFilterRules.self, from: data) else { return NebulaFilterRules() }
        return rules
    }
    public func save(_ rules: NebulaFilterRules, account: Int64) throws {
        var value = rules; value.words = String(value.words.prefix(4000)); value.excludedPeers = Set(value.excludedPeers.sorted().prefix(256))
        defaults.set(try JSONEncoder().encode(value), forKey: "nebula.filter.\(account)")
        lock.lock(); reveals = reveals.filter { !$0.hasPrefix("\(account):") }; lock.unlock()
        NotificationCenter.default.post(name: Self.changed, object: account)
    }
    public func hidden(account: Int64, peer: Int64, namespace: Int32, id: Int32, author: Int64?, text: String, incoming: Bool, sponsored: Bool) -> Bool {
        guard incoming, !sponsored else { return false }
        let rules = rules(account: account)
        guard rules.enabled, !rules.excludedPeers.contains(peer) else { return false }
        lock.lock()
        let revealed = reveals.contains("\(account):\(peer):\(namespace):\(id)")
        let authorBlocked = author.map { blocked[account]?.contains($0) == true } ?? false
        lock.unlock()
        return !revealed && (rules.blockedAuthors && authorBlocked || rules.matches(text))
    }
    public func reveal(account: Int64, peer: Int64, namespace: Int32, id: Int32) {
        lock.lock(); if reveals.count >= 4096 { reveals.removeAll() }
        reveals.insert("\(account):\(peer):\(namespace):\(id)"); lock.unlock()
        NotificationCenter.default.post(name: Self.changed, object: account)
    }
    public func updateBlocked(_ peers: Set<Int64>, account: Int64) {
        lock.lock(); let differs = blocked[account] != peers; blocked[account] = peers; lock.unlock()
        if differs { NotificationCenter.default.post(name: Self.changed, object: account) }
    }
}
