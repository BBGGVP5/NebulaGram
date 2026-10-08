import Foundation

/// Send optional generation fields only to model families that support them.
public enum NebulaAiGenerationPolicy {
    private static func name(_ model: String) -> String { model.lowercased().replacingOccurrences(of: "models/", with: "") }
    public static func openAIReasoning(_ model: String) -> Bool { ["o1", "o3", "o4", "gpt-5", "gpt-6"].contains { name(model).hasPrefix($0) } }
    public static func claudeThinking(_ model: String) -> String? {
        let value = name(model)
        if ["claude-opus-4-6", "claude-opus-4-7", "claude-opus-4-8", "claude-sonnet-4-6"].contains(where: { value.hasPrefix($0) }) { return "adaptive" }
        if ["claude-3-7-sonnet", "claude-sonnet-4-5", "claude-opus-4-5", "claude-haiku-4-5", "claude-sonnet-4-2025", "claude-opus-4-2025", "claude-opus-4-1"].contains(where: { value.hasPrefix($0) }) { return "enabled" }
        return nil
    }
    public static func geminiThinking(_ model: String) -> [String: Any]? {
        let value = name(model)
        if value.hasPrefix("gemini-3") && !value.contains("image") { return ["thinkingLevel": "HIGH"] }
        if value.hasPrefix("gemini-2.5") && !value.contains("image") && !value.contains("tts") { return ["thinkingBudget": 1024] }
        return nil
    }
    public static func supportsReasoning(provider: NebulaAiProvider, model: String) -> Bool {
        switch provider {
        case .openAI: return openAIReasoning(model)
        case .claude: return claudeThinking(model) != nil
        case .gemini: return geminiThinking(model) != nil
        case .openRouter, .perplexity: return true
        case .custom, .appleIntelligence: return false
        }
    }
}
