import Foundation

public struct NebulaTranslationOptions: Equatable {
    public var incoming: Bool
    public var outgoing: Bool
    public var draft: Bool
    public var incomingLanguage: String
    public var draftLanguage: String
    public var delay: Double
    public func translates(incoming: Bool) -> Bool { incoming ? self.incoming : outgoing }
}

/// Account/chat opt-ins stay local and are deliberately outside settings transfer.
public final class NebulaTranslationPreferences {
    public static let shared = NebulaTranslationPreferences()
    public static let changed = Notification.Name("NebulaTranslationPreferencesChanged")
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public var composerShortcut: Bool {
        get { defaults.bool(forKey: "nebula.ai.composer_shortcut") }
        set { defaults.set(newValue, forKey: "nebula.ai.composer_shortcut"); notify() }
    }
    private func key(_ account: String, _ peer: String) -> String { "nebula.translation.\(account).\(peer)." }
    public func options(account: String, peer: String) -> NebulaTranslationOptions {
        let prefix = key(account, peer)
        let delay = defaults.object(forKey: prefix + "delay") as? Double ?? 0.3
        return NebulaTranslationOptions(incoming: defaults.bool(forKey: prefix + "incoming"),
            outgoing: defaults.bool(forKey: prefix + "outgoing"),
            draft: defaults.bool(forKey: prefix + "draft"),
            incomingLanguage: defaults.string(forKey: prefix + "incomingLanguage") ?? "ru",
            draftLanguage: defaults.string(forKey: prefix + "draftLanguage") ?? "en",
            delay: delay.isFinite ? min(2, max(0.15, delay)) : 0.3)
    }
    public func update(account: String, peer: String, _ change: (inout NebulaTranslationOptions) -> Void) {
        var options = self.options(account: account, peer: peer); change(&options)
        let prefix = key(account, peer)
        defaults.set(options.incoming, forKey: prefix + "incoming")
        defaults.set(options.outgoing, forKey: prefix + "outgoing")
        defaults.set(options.draft, forKey: prefix + "draft")
        defaults.set(options.incomingLanguage, forKey: prefix + "incomingLanguage")
        defaults.set(options.draftLanguage, forKey: prefix + "draftLanguage")
        defaults.set(options.delay.isFinite ? min(2, max(0.15, options.delay)) : 0.3, forKey: prefix + "delay")
        notify()
    }
    private func notify() { NotificationCenter.default.post(name: Self.changed, object: nil) }
}

/// Same-draft deduplication and late-response protection, including A → B → A edits.
public struct NebulaDraftRevision {
    public private(set) var revision = 0
    private var identity: String?
    private var suppressedIdentity: String?
    public init() { }
    public mutating func begin(_ identity: String) -> Int? {
        guard self.identity != identity, suppressedIdentity != identity else { return nil }
        suppressedIdentity = nil; revision += 1; self.identity = identity; return revision
    }
    public mutating func suppress(_ identity: String) { cancel(); suppressedIdentity = identity }
    public mutating func cancel() { revision += 1; identity = nil }
    public func accepts(_ revision: Int) -> Bool { self.revision == revision && identity != nil }
}
