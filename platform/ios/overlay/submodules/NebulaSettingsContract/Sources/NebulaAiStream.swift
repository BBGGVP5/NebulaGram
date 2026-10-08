import Foundation

/// Bounded SSE decoder. Bytes are decoded only after a complete line so split
/// UTF-8 scalars are preserved. Only visible answer deltas enter the result.
public struct NebulaAiStream {
    public enum Failure: Error { case malformed, interrupted, providerError, tooLarge }
    public private(set) var text = ""
    public private(set) var completed = false
    private let provider: NebulaAiProvider
    private var line = Data()
    private var payload = ""
    private var received = 0
    public init(provider: NebulaAiProvider) { self.provider = provider }

    @discardableResult public mutating func append(_ byte: UInt8) throws -> Bool {
        received += 1
        guard received <= 4_000_000, line.count < 512_000 else { throw Failure.tooLarge }
        guard byte == 10 else { line.append(byte); return false }
        guard var value = String(data: line, encoding: .utf8) else { throw Failure.malformed }
        line.removeAll(keepingCapacity: true)
        if value.hasSuffix("\r") { value.removeLast() }
        if value.isEmpty { return try event() }
        if value.hasPrefix("data:") {
            var data = String(value.dropFirst(5)); if data.hasPrefix(" ") { data.removeFirst() }
            payload += (payload.isEmpty ? "" : "\n") + data
            guard payload.utf8.count <= 512_000 else { throw Failure.tooLarge }
        }
        return false
    }
    public mutating func finish() throws -> String {
        if !line.isEmpty { _ = try append(10) }
        _ = try event()
        guard completed, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw Failure.interrupted }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private mutating func event() throws -> Bool {
        guard !payload.isEmpty else { return false }
        let data = payload; payload = ""
        if data == "[DONE]" { completed = true; return false }
        guard let json = try JSONSerialization.jsonObject(with: Data(data.utf8)) as? [String: Any] else { throw Failure.malformed }
        let type = json["type"] as? String ?? ""
        if json["error"] != nil || ["error", "response.failed", "response.incomplete"].contains(type) { throw Failure.providerError }
        var delta = ""
        switch provider {
        case .openAI, .perplexity:
            if type == "response.output_text.delta" || type == "response.refusal.delta" { delta = json["delta"] as? String ?? "" }
            if type == "response.completed" {
                if let response = json["response"] as? [String: Any], let status = response["status"] as? String, status != "completed" { throw Failure.providerError }
                completed = true
            }
        case .claude:
            if type == "content_block_delta", let part = json["delta"] as? [String: Any], part["type"] as? String == "text_delta" { delta = part["text"] as? String ?? "" }
            if type == "message_delta", let part = json["delta"] as? [String: Any], part["stop_reason"] as? String == "max_tokens" { throw Failure.interrupted }
            if type == "message_stop" { completed = true }
        case .gemini:
            if let candidate = (json["candidates"] as? [[String: Any]])?.first {
                let content = candidate["content"] as? [String: Any]
                delta = (content?["parts"] as? [[String: Any]] ?? []).filter { ($0["thought"] as? Bool) != true }.compactMap { $0["text"] as? String }.joined()
                if let reason = candidate["finishReason"] as? String {
                    guard reason == "STOP" else { throw Failure.providerError }; completed = true
                }
            }
        case .custom, .openRouter:
            if let choice = (json["choices"] as? [[String: Any]])?.first {
                let part = choice["delta"] as? [String: Any]
                delta = part?["content"] as? String ?? part?["refusal"] as? String ?? ""
                if let reason = choice["finish_reason"] as? String {
                    guard reason == "stop" else { throw Failure.interrupted }; completed = true
                }
            }
        case .appleIntelligence: throw Failure.malformed
        }
        guard text.utf8.count + delta.utf8.count <= 400_000 else { throw Failure.tooLarge }
        text += delta
        return !delta.isEmpty
    }
}
