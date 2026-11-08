import Foundation

public enum NebulaMessageMenuAction: String, CaseIterable {
    case reply, forward, copy, saveGallery, saveFiles, copyLink, report, translate, transcribe, sound, tools, checklist
    public var identifier: String { "nebula.message." + rawValue }
}

/// Local menu choices affect only actions already allowed by Telegram.
public final class NebulaMessageMenuPreferences {
    public static let shared = NebulaMessageMenuPreferences()
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public var compact: Bool { defaults.bool(forKey: "nebula.messageMenu.compact") }
    public func setCompact(_ value: Bool) { defaults.set(value, forKey: "nebula.messageMenu.compact") }
    /// Frequent actions move into an icon row below the list (Android `split`).
    public var split: Bool { defaults.object(forKey: "nebula.messageMenu.split") as? Bool ?? true }
    public func setSplit(_ value: Bool) { defaults.set(value, forKey: "nebula.messageMenu.split") }
    /// The menu grows out of the message with a light spring (Android `cherry_motion`).
    /// The animation hook reads this key directly, so keep the name in sync.
    public static let cherryMotionKey = "nebula.messageMenu.cherryMotion"
    public var cherryMotion: Bool { defaults.object(forKey: Self.cherryMotionKey) as? Bool ?? true }
    public func setCherryMotion(_ value: Bool) { defaults.set(value, forKey: Self.cherryMotionKey) }
    public func visible(_ action: NebulaMessageMenuAction) -> Bool { defaults.object(forKey: action.identifier) as? Bool ?? true }
    public func setVisible(_ action: NebulaMessageMenuAction, _ value: Bool) { defaults.set(value, forKey: action.identifier) }
    public func visible(identifier: String?) -> Bool {
        guard let action = NebulaMessageMenuAction.allCases.first(where: { $0.identifier == identifier }) else { return true }
        return visible(action)
    }
}

/// Split message menu: Reply, Copy, Edit, Forward and Copy link leave the list
/// and become one row of icons below it, the way Android lays the menu out.
public enum NebulaMessageMenuSplit {
    /// Edit stays a native action without a visibility switch; it only gets an
    /// identifier so the row can find it.
    public static let editIdentifier = "nebula.message.edit"
    public static let order: [String] = [NebulaMessageMenuAction.reply.identifier, NebulaMessageMenuAction.copy.identifier, editIdentifier, NebulaMessageMenuAction.forward.identifier, NebulaMessageMenuAction.copyLink.identifier]
    public static let maximum = 5

    /// Indices of `identifiers` that move into the row, in row order. Empty
    /// unless at least two actions move and at least one stays in the list:
    /// a lone icon or an empty list reads worse than the plain menu.
    public static func row(_ identifiers: [String?], enabled: Bool) -> [Int] {
        guard enabled else { return [] }
        var picked: [Int] = []
        for id in order {
            if let index = identifiers.firstIndex(where: { $0 == id }), !picked.contains(index), picked.count < maximum { picked.append(index) }
        }
        if picked.count < 2 || identifiers.count - picked.count < 1 { return [] }
        return picked
    }
}
