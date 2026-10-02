import Foundation

/// Public camera zoom factors are relative to the virtual device's widest lens.
/// UI factors are relative to its wide-angle constituent (the displayed 1×).
public struct NebulaZoomRange {
    public let neutral: Double
    public let minimum: Double
    public let maximum: Double
    public init(neutral: Double, minimumDeviceFactor: Double, maximumDeviceFactor: Double) {
        self.neutral = neutral.isFinite && neutral > 0 ? neutral : 1
        let low = minimumDeviceFactor.isFinite && minimumDeviceFactor > 0 ? minimumDeviceFactor : 1
        self.minimum = low / self.neutral
        self.maximum = max(low, maximumDeviceFactor.isFinite ? maximumDeviceFactor : low) / self.neutral
    }
    public func displayed(_ deviceFactor: Double) -> Double {
        guard deviceFactor.isFinite else { return min(maximum, max(minimum, 1)) }
        return min(maximum, max(minimum, deviceFactor / neutral))
    }
    public func deviceFactor(_ displayed: Double) -> Double {
        let value = displayed.isFinite ? displayed : 1
        return min(maximum, max(minimum, value)) * neutral
    }
    public func presets(deviceStops: [Double]) -> [Double] {
        var result: [Double] = []
        for value in ([minimum, 1, 2, maximum] + deviceStops.filter(\.isFinite).map { $0 / neutral }).sorted() where value >= minimum && value <= maximum {
            if result.last.map({ abs($0 - value) > 0.04 }) ?? true { result.append(value) }
        }
        return result
    }
}
