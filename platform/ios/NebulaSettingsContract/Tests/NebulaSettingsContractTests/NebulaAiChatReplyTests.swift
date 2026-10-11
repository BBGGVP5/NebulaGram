import XCTest
@testable import NebulaSettingsContract

final class NebulaAiChatReplyTests: XCTestCase {
    func testRoleOnlyAtStartAndFormattingPreserved() {
        for prefix in ["Assistant:", "assistant:", "ASSISTANT:", "**Assistant:**", "**Assistant**:"] {
            XCTAssertEqual(NebulaAiChatReply.clean(prefix + " 1 + 1 = 2! 😊"), "1 + 1 = 2! 😊")
            XCTAssertEqual(NebulaAiChatReply.clean(" \n" + prefix + "\r\n\r\n    code\n**bold**"), "    code\n**bold**")
            XCTAssertEqual(NebulaAiChatReply.clean(prefix), "")
        }
        for value in ["A", "Привет! 👋", "  **Actual**\n", "`Assistant:` is a label", "> Assistant: quote", "Answer\nAssistant: inside", "Assistant is a word", "🤖 Assistant: text"] {
            XCTAssertEqual(NebulaAiChatReply.clean(value), value)
        }
    }
    func testStreamingLabelDoesNotFlashOrRemoveRealFinalText() {
        for value in ["A", "Ass", "Assistant", "Assistant:", "**Assis", "**Assistant:"] { XCTAssertEqual(NebulaAiChatReply.clean(value, partial: true), "") }
        XCTAssertEqual(NebulaAiChatReply.clean("A"), "A")
        XCTAssertEqual(NebulaAiChatReply.clean("A direct answer", partial: true), "A direct answer")
        XCTAssertEqual(NebulaAiChatReply.clean("Assistant: **Привет** 👋", partial: true), "**Привет** 👋")
    }
}
