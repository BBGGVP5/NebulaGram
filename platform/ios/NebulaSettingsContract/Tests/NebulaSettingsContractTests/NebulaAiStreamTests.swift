import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaAiStreamTests: XCTestCase {
    private func feed(_ value: String, into stream: inout NebulaAiStream) throws {
        for byte in value.utf8 { try stream.append(byte) }
    }
    func testSplitUTF8AndCRLFPreserveEmoji() throws {
        var stream = NebulaAiStream(provider: .openAI)
        try feed("event: response.output_text.delta\r\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"Привет 🧰\"}\r\n\r\n", into: &stream)
        try feed("data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n", into: &stream)
        XCTAssertEqual(try stream.finish(), "Привет 🧰")
    }
    func testInterruptedAndTerminalErrorAreNotSavedAsAnswers() throws {
        var stream = NebulaAiStream(provider: .openAI)
        try feed("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}\n\n", into: &stream)
        XCTAssertThrowsError(try stream.finish())
        XCTAssertThrowsError(try feed("data: {\"type\":\"response.failed\"}\n\n", into: &stream))
    }
    func testClaudeAndGeminiExcludeReasoning() throws {
        var claude = NebulaAiStream(provider: .claude)
        try feed("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"private\"}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Answer\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n", into: &claude)
        XCTAssertEqual(try claude.finish(), "Answer")
        var gemini = NebulaAiStream(provider: .gemini)
        try feed("data: {\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"Visible\"}]},\"finishReason\":\"STOP\"}]}\n\n", into: &gemini)
        XCTAssertEqual(try gemini.finish(), "Visible")
    }
    func testChatCompletionAndLimitFailure() throws {
        var stream = NebulaAiStream(provider: .openRouter)
        try feed("data: {\"choices\":[{\"delta\":{\"content\":\"OK\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n", into: &stream)
        XCTAssertEqual(try stream.finish(), "OK")
        var failed = NebulaAiStream(provider: .custom)
        XCTAssertThrowsError(try feed("data: {\"choices\":[{\"delta\":{\"content\":\"Truncated\"},\"finish_reason\":\"length\"}]}\n\n", into: &failed))
    }
    func testMalformedUTF8AndBoundedLine() throws {
        var invalid = NebulaAiStream(provider: .openAI)
        try invalid.append(255)
        XCTAssertThrowsError(try invalid.append(10))
        var oversized = NebulaAiStream(provider: .openAI)
        XCTAssertThrowsError(try feed(String(repeating: "x", count: 512_001), into: &oversized))
    }
}
