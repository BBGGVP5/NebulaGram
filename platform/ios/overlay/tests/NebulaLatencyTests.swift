import Foundation

// On Mac: swiftc ../submodules/NebulaLinkUI/Sources/NebulaLatency.swift NebulaLatencyTests.swift -o /tmp/nebulalink-latency && /tmp/nebulalink-latency
@main enum NebulaLatencyTests {
    static func main() {
        func expect(_ expected: String, _ ms: Int, _ method: String, _ checked: Int64) {
            precondition(NebulaLatency.format(ms, method: method, checkedAt: checked,
                         unit: "ms", unknown: "unknown", failed: "failed") == expected)
        }
        expect("≈100 ms", 330, "nimbo", 1)
        expect("≈1 ms", 4, "nimbo", 1)
        expect("≈2 ms", 5, "nimbo", 1)
        expect("≈0 ms", 1, "nimbo", 1)
        expect("≈0 ms", 0, "nimbo", 1)
        expect("unknown", 0, "nimbo", 0)
        expect("failed", -1, "nimbo", 1)
        expect("≈650752620 ms", 2147483647, "nimbo", 1)
        for method in ["tcp", "http", "url", "", "future", "NIMBO"] {
            expect("330 ms", 330, method, 1)
            expect("0 ms", 0, method, 1)
            expect("unknown", 0, method, 0)
            expect("failed", -1, method, 1)
        }
        expect("330 ms", 330, "", 0)
        print("PASS: Swift latency provenance, rounding, measured zero and legacy values")
    }
}
