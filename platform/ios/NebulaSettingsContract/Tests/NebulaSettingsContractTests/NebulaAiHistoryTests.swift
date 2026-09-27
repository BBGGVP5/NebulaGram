import XCTest
@testable import NebulaSettingsContract

final class NebulaAiHistoryTests: XCTestCase {
    private func makeHistory() -> (NebulaAiHistory, UserDefaults) {
        let suite = "NebulaAiHistoryTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        return (NebulaAiHistory(defaults: defaults), defaults)
    }

    func testHistoryIsOptInAndClearsLocally() {
        let (history, _) = makeHistory()
        XCTAssertFalse(history.isEnabled)
        XCTAssertFalse(history.append(provider: "OpenAI", input: "hello", output: "hi"))
        XCTAssertTrue(history.entries().isEmpty)
        history.isEnabled = true
        XCTAssertTrue(history.append(provider: "OpenAI", input: "hello", output: "hi"))
        XCTAssertEqual(history.entries().first?.input, "hello")
        history.clear()
        XCTAssertTrue(history.entries().isEmpty)
    }

    func testBoundedHistoryAndEntryText() {
        let (history, _) = makeHistory()
        history.isEnabled = true
        for index in 0..<(NebulaAiHistory.maximumEntries + 4) {
            XCTAssertTrue(history.append(provider: "provider", input: "in \(index)", output: String(repeating: "x", count: 9_000)))
        }
        let values = history.entries()
        XCTAssertEqual(values.count, NebulaAiHistory.maximumEntries)
        XCTAssertEqual(values.first?.input, "in \(NebulaAiHistory.maximumEntries + 3)")
        XCTAssertLessThanOrEqual(values.first?.output.count ?? .max, 8_000)
    }
}
