import Foundation

public enum NebulaGlassPolicy {
    public static func reduced(mode: Int, lowPower: Bool, hot: Bool, reduceTransparency: Bool) -> Bool {
        // Accessibility always takes precedence, including in Full mode.
        reduceTransparency || mode == 2 || (mode != 1 && (lowPower || hot))
    }
}
