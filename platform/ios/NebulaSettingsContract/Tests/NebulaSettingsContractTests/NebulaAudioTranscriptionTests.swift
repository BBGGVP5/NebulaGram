import XCTest
@testable import NebulaSettingsContract

final class NebulaAudioTranscriptionTests: XCTestCase {
    func testInlineAudioIsExplicitAndNotStored() throws {
        let bytes = Data([1, 2, 3])
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: NebulaAudioTranscription.payload(data: bytes, mime: "audio/ogg", model: "models/gemini-test")) as? [String: Any])
        XCTAssertEqual(body["model"] as? String, "gemini-test"); XCTAssertEqual(body["store"] as? Bool, false)
        let input = try XCTUnwrap(body["input"] as? [[String: Any]])
        XCTAssertEqual(input.last?["type"] as? String, "audio"); XCTAssertEqual(input.last?["data"] as? String, bytes.base64EncodedString())
        XCTAssertThrowsError(try NebulaAudioTranscription.payload(data: bytes, mime: "application/octet-stream", model: "m"))
        XCTAssertThrowsError(try NebulaAudioTranscription.payload(data: Data(), mime: "audio/ogg", model: "m"))
    }
    func testTranscriptIgnoresThoughtsAndRejectsPartialFailures() throws {
        let beta = Data(#"{"status":"completed","outputs":[{"type":"thought","text":"private"},{"type":"text","text":"Привет"}]}"#.utf8)
        XCTAssertEqual(try NebulaAudioTranscription.transcript(beta), "Привет")
        let v1 = Data(#"{"status":"completed","steps":[{"type":"user_input","content":[{"type":"text","text":"echo"}]},{"type":"model_output","content":[{"type":"text","text":"Hello"}]}]}"#.utf8)
        XCTAssertEqual(try NebulaAudioTranscription.transcript(v1), "Hello")
        for status in ["failed", "in_progress", "requires_action"] {
            XCTAssertThrowsError(try NebulaAudioTranscription.transcript(Data(String(decoding: beta, as: UTF8.self).replacingOccurrences(of: "completed", with: status).utf8)))
        }
        XCTAssertThrowsError(try NebulaAudioTranscription.transcript(Data(#"{"status":"completed","outputs":[]}"#.utf8)))
    }
    func testBoundedFileReadAndCancellation() throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        try Data([5, 7]).write(to: file)
        XCTAssertEqual(try NebulaAudioTranscription.read(file), Data([5, 7]))
        XCTAssertThrowsError(try NebulaAudioTranscription.read(file, cancelled: { true }))
        try Data(repeating: 0, count: NebulaAudioTranscription.maximumBytes + 1).write(to: file)
        XCTAssertThrowsError(try NebulaAudioTranscription.read(file))
    }
}
