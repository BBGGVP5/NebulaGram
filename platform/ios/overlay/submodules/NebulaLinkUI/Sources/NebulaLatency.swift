import Foundation

// Presentation only: raw GET timing and cache values remain unchanged.
enum NebulaLatency {
    static func isMeasured(_ rawMs: Int, checkedAt: Int64) -> Bool {
        rawMs > 0 || (rawMs == 0 && checkedAt > 0)
    }

    static func format(_ rawMs: Int, method: String, checkedAt: Int64,
                       unit: String, unknown: String, failed: String) -> String {
        if rawMs < 0 { return failed }
        if !isMeasured(rawMs, checkedAt: checkedAt) { return unknown }
        if method == "nimbo" { return "≈\(Int((Double(rawMs) / 3.3).rounded())) \(unit)" }
        return "\(rawMs) \(unit)"
    }
}
