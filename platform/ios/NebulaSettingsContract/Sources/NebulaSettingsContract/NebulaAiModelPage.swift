import Foundation

public struct NebulaAiModelPage {
    public let models: [String]
    public let cursor: String?
    public enum Failure: Error { case invalid }
    public static func decode(_ data: Data, provider: NebulaAiProvider) throws -> Self {
        guard data.count <= 2_000_000, let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let entries = json[provider == .gemini ? "models" : "data"] as? [[String: Any]], entries.count <= 5000 else { throw Failure.invalid }
        var models: Set<String> = []
        for entry in entries {
            if provider == .gemini && !(entry["supportedGenerationMethods"] as? [String] ?? []).contains("generateContent") { continue }
            guard var id = entry[provider == .gemini ? "name" : "id"] as? String else { continue }
            if provider == .gemini && id.hasPrefix("models/") { id = String(id.dropFirst(7)) }
            guard !id.isEmpty, id.count <= 256, id.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }) else { continue }
            models.insert(id)
        }
        let cursor = provider == .gemini ? json["nextPageToken"] as? String : provider == .claude && json["has_more"] as? Bool == true ? json["last_id"] as? String : nil
        guard cursor == nil || cursor!.count <= 2048 else { throw Failure.invalid }
        return Self(models: models.sorted(), cursor: cursor?.isEmpty == false ? cursor : nil)
    }
}
