import Foundation

/// The "Shape" page (Android `NebulaShape`): avatar shape presets and message tails.
public enum NebulaShapePreferences {
    /// Read straight from `UserDefaults.standard` by the bubble image renderer.
    public static let messageTailsKey = "nebula.shape.message_tails"

    public static func messageTails(_ defaults: UserDefaults = .standard) -> Bool {
        defaults.object(forKey: messageTailsKey) as? Bool ?? true
    }

    public static func setMessageTails(_ value: Bool, _ defaults: UserDefaults = .standard) {
        defaults.set(value, forKey: messageTailsKey)
    }

    /// Avatar shapes that corner radii can express, with the roundness they map to
    /// (`avatar_round`, percent of a circle). Android's leaf and drop need
    /// per-corner radii, which the iOS avatar clip does not take.
    public enum AvatarShape: Int, CaseIterable {
        case circle, squircle, rounded, square

        public var roundness: Int {
            switch self {
            case .circle: return 100
            case .squircle: return 78
            case .rounded: return 46
            case .square: return 0
            }
        }

        public func title(russian: Bool) -> String {
            switch self {
            case .circle: return russian ? "Круг" : "Circle"
            case .squircle: return russian ? "Сквиркл" : "Squircle"
            case .rounded: return russian ? "Скруглённый" : "Rounded"
            case .square: return russian ? "Квадрат" : "Square"
            }
        }

        /// Preset matching a stored roundness, or nil for a custom slider value.
        public static func matching(roundness: Int) -> AvatarShape? {
            allCases.first { $0.roundness == roundness }
        }
    }
}
