import Foundation

/// Shared arithmetic for native message layout and the settings preview.
public enum NebulaMediaGeometry {
    public static func scale(_ percent: Int) -> Double {
        Double(max(50, min(150, percent))) / 100.0
    }

    public static func boundedScale(percent: Int, naturalWidth: Double, availableWidth: Double) -> Double {
        guard naturalWidth.isFinite, availableWidth.isFinite, naturalWidth > 0, availableWidth > 0 else { return 1 }
        return min(scale(percent), availableWidth / naturalWidth)
    }
}
