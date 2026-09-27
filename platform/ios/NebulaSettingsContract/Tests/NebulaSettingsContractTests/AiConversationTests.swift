import Foundation
import XCTest
@testable import NebulaSettingsContract

final class AiConversationTests: XCTestCase {
    func testContextBoundsAndIsolation() {
        var chat = NebulaAiConversation()
        chat.select("local")
        XCTAssertEqual(chat.request("hello", limit: 10), "hello")
        for i in 0..<20 { chat.append(input: "q\(i)", output: "a\(i)") }
        let request = chat.request("next", limit: 2_000)!
        XCTAssertFalse(request.contains("q13\n")); XCTAssertTrue(request.contains("q14\n"))
        for limit in 10..<500 { XCTAssertLessThanOrEqual(chat.request("next", limit: limit)!.count, limit) }
        XCTAssertNil(chat.request("too long", limit: 2))
        chat.select("remote"); XCTAssertEqual(chat.request("next", limit: 500), "next")
        chat.append(input: "secret", output: "answer"); chat.clear()
        XCTAssertEqual(chat.request("fresh", limit: 500), "fresh")
    }
    func testLateCompletionCannotFinishNewRequest() {
        var gate = NebulaAiRequestGate()
        let first = gate.begin(); gate.cancel(); let second = gate.begin()
        XCTAssertFalse(gate.accepts(first)); XCTAssertTrue(gate.accepts(second))
        gate.cancel(); XCTAssertFalse(gate.accepts(second))
    }
    func testMarkdownKeepsCodeLiteralAndUsesUTF16Ranges() {
        let result = NebulaAiMarkdown.parse("## Привет 👋\n**Жирный** и *курсив*\n- Пункт\n```\n**код**\n```\n`x < y`")
        XCTAssertEqual(result.text, "Привет 👋\nЖирный и курсив\n• Пункт\n**код**\nx < y\n")
        XCTAssertEqual(result.marks.count, 5)
        for mark in result.marks { XCTAssertLessThanOrEqual(NSMaxRange(mark.range), result.text.utf16.count) }
        XCTAssertEqual(NebulaAiMarkdown.parse("\\* текст").text, "* текст\n")
        XCTAssertTrue(NebulaAiMarkdown.parse("<script>bad()</script>").text.contains("<script>"))
        XCTAssertTrue(NebulaAiMarkdown.parse("**unclosed").text.contains("**unclosed"))
        XCTAssertLessThan(NebulaAiMarkdown.parse(String(repeating: "a", count: 200_000)).text.count, 60_010)
    }
    func testShortcutIsOptInAndPersists() {
        let suite = "ai-shortcut-\(UUID())"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = NebulaAiSettings(defaults: defaults)
        XCTAssertFalse(settings.homeShortcut)
        settings.homeShortcut = true
        XCTAssertTrue(NebulaAiSettings(defaults: defaults).homeShortcut)
        XCTAssertFalse(settings.enabled)
    }
}
