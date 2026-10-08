import XCTest
@testable import NebulaSettingsContract

final class NebulaAiGenerationPolicyTests: XCTestCase {
    func testOptionalThinkingFieldsAreModelScoped() {
        XCTAssertNil(NebulaAiGenerationPolicy.claudeThinking("claude-3-5-haiku-latest"))
        XCTAssertNil(NebulaAiGenerationPolicy.claudeThinking("unknown-future-model"))
        XCTAssertEqual(NebulaAiGenerationPolicy.claudeThinking("claude-sonnet-4-5-20250929"), "enabled")
        XCTAssertEqual(NebulaAiGenerationPolicy.claudeThinking("claude-opus-4-8"), "adaptive")
        XCTAssertNil(NebulaAiGenerationPolicy.geminiThinking("gemini-2.0-flash"))
        XCTAssertNil(NebulaAiGenerationPolicy.geminiThinking("gemini-2.5-flash-image"))
        XCTAssertEqual(NebulaAiGenerationPolicy.geminiThinking("models/gemini-3-pro-preview")?["thinkingLevel"] as? String, "HIGH")
        XCTAssertEqual(NebulaAiGenerationPolicy.geminiThinking("gemini-2.5-flash")?["thinkingBudget"] as? Int, 1024)
        XCTAssertFalse(NebulaAiGenerationPolicy.supportsReasoning(provider: .custom, model: "anything"))
        XCTAssertFalse(NebulaAiGenerationPolicy.supportsReasoning(provider: .openAI, model: "gpt-4.1"))
        XCTAssertTrue(NebulaAiGenerationPolicy.supportsReasoning(provider: .openAI, model: "o3"))
    }
}
