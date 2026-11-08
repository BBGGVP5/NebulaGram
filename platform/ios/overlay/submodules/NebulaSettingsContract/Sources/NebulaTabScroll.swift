import Foundation

/// Hides the bottom bar while the chat list is scrolled down, with thresholds
/// (Android `NebulaTabScroll`): it hides only after a steady downward drag and
/// comes back after a steady pull back or at the top of the list. A change of
/// direction resets the distance, so finger jitter changes nothing.
public struct NebulaTabScroll {
    public static let key = "nebula.tabs.hide_on_scroll"
    public static let hideDistance: Double = 72
    public static let showDistance: Double = 32

    public static func enabled(_ defaults: UserDefaults = .standard) -> Bool { defaults.bool(forKey: key) }
    public static func setEnabled(_ value: Bool, _ defaults: UserDefaults = .standard) { defaults.set(value, forKey: key) }

    public private(set) var hidden = false
    private var travelled: Double = 0

    public init() { }

    /// `dy` > 0 when content moves up. Returns true when visibility changed.
    public mutating func scrolled(dy: Double, atTop: Bool, manual: Bool, hideDistance: Double = NebulaTabScroll.hideDistance, showDistance: Double = NebulaTabScroll.showDistance) -> Bool {
        let was = hidden
        if atTop {
            travelled = 0
            hidden = false
            return was != hidden
        }
        if !manual || dy == 0 { return false }
        if travelled != 0 && (travelled > 0) != (dy > 0) { travelled = 0 }
        travelled += dy
        if !hidden && travelled >= hideDistance {
            hidden = true; travelled = 0
        } else if hidden && -travelled >= showDistance {
            hidden = false; travelled = 0
        }
        return was != hidden
    }

    public mutating func reset() { travelled = 0; hidden = false }
}
