import Foundation

public enum NebulaAvatarGeometry {
    /// Clockwise from top-left, matching Android's four corner radii.
    public static func radii(side: Double, shape: Int, roundness: Int, uniform: Bool = true, isForum: Bool = false) -> [Double] {
        let half = max(0, side.isFinite ? side : 0) / 2
        if isForum && !uniform { return Array(repeating: half * 0.5, count: 4) }
        let factors: [[Double]] = [[1,1,1,1], [0.78,0.78,0.78,0.78], [0.46,0.46,0.46,0.46], [0,0,0,0], [1,0.22,1,0.22], [1,1,0.22,1]]
        let scale = Double(max(0, min(100, roundness))) / 100
        return factors[max(0, min(5, shape))].map { half * $0 * scale }
    }
}
