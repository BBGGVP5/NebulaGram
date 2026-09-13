import Foundation

/// Local opt-in only; payloads and credentials are never stored here.
public final class NebulaForwardEditing {
    public static let shared = NebulaForwardEditing()
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public var enabled: Bool {
        get { defaults.bool(forKey: "nebula.privacy.edit_forward_draft") }
        set { defaults.set(newValue, forKey: "nebula.privacy.edit_forward_draft") }
    }
}
