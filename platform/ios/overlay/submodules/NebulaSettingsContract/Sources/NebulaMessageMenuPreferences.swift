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
    public func visible(_ action: NebulaMessageMenuAction) -> Bool { defaults.object(forKey: action.identifier) as? Bool ?? true }
    public func setVisible(_ action: NebulaMessageMenuAction, _ value: Bool) { defaults.set(value, forKey: action.identifier) }
    public func visible(identifier: String?) -> Bool {
        guard let action = NebulaMessageMenuAction.allCases.first(where: { $0.identifier == identifier }) else { return true }
        return visible(action)
    }
}
