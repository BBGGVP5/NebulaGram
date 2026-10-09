import Foundation

public enum NebulaAudioProtocol {
    public static let maximumInput = 14_000_000, maximumAudio = 20_000_000, maximumJSON = 30_000_000, maximumText = 4_000
    public static let openAIVoices = ["marin", "cedar", "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer", "verse"]
    public static let geminiVoices = ["Kore", "Puck", "Charon", "Aoede", "Fenrir", "Leda", "Orus", "Zephyr"]
    public enum Failure: Error { case invalidInput, invalidResponse, unsupportedFormat }
    public static func model(_ value: String) throws -> String {
        let value = value.hasPrefix("models/") ? String(value.dropFirst(7)) : value
        guard value.range(of: #"^[A-Za-z0-9._/-]{1,256}$"#, options: .regularExpression) == (value.startIndex..<value.endIndex) else { throw Failure.invalidInput }
        return value
    }
    public static func fileExtension(_ mime: String) throws -> String {
        switch mime {
        case "audio/ogg": return "ogg"
        case "audio/mp4": return "m4a"
        case "video/mp4": return "mp4"
        case "audio/mpeg", "audio/mp3": return "mp3"
        case "audio/wav", "audio/x-wav": return "wav"
        case "audio/webm", "video/webm": return "webm"
        default: throw Failure.unsupportedFormat
        }
    }
    public static func multipart(data: Data, mime: String, model: String, boundary: String) throws -> Data {
        guard !data.isEmpty, data.count <= maximumInput, boundary.range(of: #"^[A-Za-z0-9-]{16,80}$"#, options: .regularExpression) == (boundary.startIndex..<boundary.endIndex) else { throw Failure.invalidInput }
        let ext = try fileExtension(mime), model = try self.model(model)
        var body = Data()
        for (name, value) in [("model", model), ("response_format", "json")] {
            body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n".utf8))
        }
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"recording.\(ext)\"\r\nContent-Type: \(mime)\r\n\r\n".utf8))
        body.append(data); body.append(Data("\r\n--\(boundary)--\r\n".utf8)); return body
    }
    public static func speech(provider: NebulaAiProvider, model: String, text: String, voice: String, style: String, speed: Double) throws -> Data {
        let voices = provider == .openAI ? openAIVoices : geminiVoices
        guard [.openAI, .gemini].contains(provider), !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              text.utf16.count <= maximumText, speed.isFinite, (0.8...1.2).contains(speed), voices.contains(voice) else { throw Failure.invalidInput }
        let tones = ["neutral": "Natural and clear", "warm": "Warm and friendly", "calm": "Calm and gentle", "lively": "Lively and expressive"]
        guard let tone = tones[style] else { throw Failure.invalidInput }
        let direction = tone + ". Read the exact supplied text in its original language without adding words. " + (speed < 1 ? "Speak slowly." : speed > 1 ? "Speak briskly." : "Use a normal speaking pace.")
        let model = try self.model(model)
        var body: [String: Any]
        if provider == .openAI {
            body = ["model": model, "input": text, "voice": voice, "response_format": "wav", "speed": speed]
            if model.hasPrefix("gpt-") { body["instructions"] = direction }
        } else {
            body = ["model": model, "store": false, "input": [["type": "user_input", "content": [["type": "text", "text": text, "annotations": [["type": "speech_metadata", "style": direction]]]]]],
                "response_format": ["type": "audio", "mime_type": "audio/wav"], "generation_config": ["speech_config": [["voice": voice]]]]
        }
        return try JSONSerialization.data(withJSONObject: body)
    }
    private static func object(_ data: Data, limit: Int) throws -> [String: Any] {
        guard data.count <= limit, let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure.invalidResponse }; return object
    }
    private static func output(_ object: [String: Any]) throws -> [[String: Any]] {
        guard object["status"] as? String == "completed", object["error"] == nil || object["error"] is NSNull else { throw Failure.invalidResponse }
        if let values = object["outputs"] as? [[String: Any]] { return values }
        return (object["steps"] as? [[String: Any]] ?? []).filter { $0["type"] as? String == "model_output" }.flatMap { $0["content"] as? [[String: Any]] ?? [] }
    }
    public static func transcript(provider: NebulaAiProvider, data: Data) throws -> String {
        let object = try self.object(data, limit: 2_000_000)
        let value: String
        if provider == .openAI {
            guard object["error"] == nil, let text = object["text"] as? String else { throw Failure.invalidResponse }; value = text
        } else {
            value = try output(object).filter { $0["type"] as? String == "text" && $0["thought"] as? Bool != true }.compactMap { $0["text"] as? String }.joined(separator: "\n")
        }
        let result = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !result.isEmpty, result.utf16.count <= 100_000 else { throw Failure.invalidResponse }; return result
    }
    public static func generatedAudio(_ data: Data) throws -> Data {
        let object = try self.object(data, limit: maximumJSON)
        let outputs = try output(object).filter { $0["type"] as? String == "audio" }
        let items = outputs + ((object["output_audio"] as? [String: Any]).map { [$0] } ?? [])
        guard items.count == 1, let encoded = items[0]["data"] as? String, !encoded.isEmpty,
              encoded.utf8.count <= (maximumAudio + 2) / 3 * 4, let bytes = Data(base64Encoded: encoded) else { throw Failure.invalidResponse }
        switch items[0]["mime_type"] as? String ?? "audio/wav" {
        case "audio/wav", "audio/x-wav": return try wave(bytes)
        case "audio/l16", "audio/pcm", "audio/L16;codec=pcm;rate=24000": return try pcmWave(bytes)
        default: throw Failure.unsupportedFormat
        }
    }
    public static func wave(_ data: Data) throws -> Data {
        guard data.count >= 44, data.count <= maximumAudio, data.prefix(4) == Data("RIFF".utf8), data.dropFirst(8).prefix(4) == Data("WAVE".utf8) else { throw Failure.invalidResponse }; return data
    }
    public static func pcmWave(_ data: Data) throws -> Data {
        guard !data.isEmpty, data.count % 2 == 0, data.count <= maximumAudio - 44 else { throw Failure.invalidResponse }
        var output = Data()
        func u32(_ value: UInt32) { var value = value.littleEndian; withUnsafeBytes(of: &value) { output.append(contentsOf: $0) } }
        func u16(_ value: UInt16) { var value = value.littleEndian; withUnsafeBytes(of: &value) { output.append(contentsOf: $0) } }
        output.append(Data("RIFF".utf8)); u32(UInt32(data.count + 36)); output.append(Data("WAVEfmt ".utf8)); u32(16); u16(1); u16(1); u32(24000); u32(48000); u16(2); u16(16); output.append(Data("data".utf8)); u32(UInt32(data.count)); output.append(data); return output
    }
}
