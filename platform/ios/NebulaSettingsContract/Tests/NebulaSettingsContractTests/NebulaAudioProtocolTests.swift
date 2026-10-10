import XCTest
@testable import NebulaSettingsContract

final class NebulaAudioProtocolTests: XCTestCase {
    func testMultipartPreservesRecordingAndFormatMetadata() throws {
        let recording = Data([0, 1, 255, 13, 10]), boundary = "nebula-test-boundary"
        let body = try NebulaAudioProtocol.multipart(data: recording, mime: "audio/ogg", model: "gpt-4o-mini-transcribe", boundary: boundary)
        XCTAssertNotNil(body.range(of: recording))
        XCTAssertNotNil(body.range(of: Data("filename=\"recording.ogg\"\r\nContent-Type: audio/ogg".utf8)))
        XCTAssertNotNil(body.range(of: Data("name=\"response_format\"\r\n\r\njson".utf8)))
        let video = try NebulaAudioProtocol.multipart(data: recording, mime: "video/mp4", model: "whisper-1", boundary: boundary)
        XCTAssertNotNil(video.range(of: Data("recording.mp4".utf8)))
        XCTAssertThrowsError(try NebulaAudioProtocol.multipart(data: Data(), mime: "audio/ogg", model: "m", boundary: boundary))
        XCTAssertThrowsError(try NebulaAudioProtocol.multipart(data: recording, mime: "text/html", model: "m", boundary: boundary))
        XCTAssertThrowsError(try NebulaAudioProtocol.model("model\n"))
        XCTAssertThrowsError(try NebulaAudioProtocol.multipart(data: recording, mime: "audio/ogg", model: "m", boundary: "short"))
    }
    func testTranscriptOutputTypesAndIncompleteResponses() throws {
        XCTAssertEqual(try NebulaAudioProtocol.transcript(provider: .openAI, data: Data(#"{"text":" Привет "}"#.utf8)), "Привет")
        let gemini = Data(#"{"status":"completed","steps":[{"type":"user_input","content":[{"type":"text","text":"input"}]},{"type":"model_output","content":[{"type":"thought","text":"hidden"},{"type":"text","thought":true,"text":"hidden"},{"type":"text","text":"answer"}]}]}"#.utf8)
        XCTAssertEqual(try NebulaAudioProtocol.transcript(provider: .gemini, data: gemini), "answer")
        for json in [#"{"status":"in_progress","outputs":[{"type":"text","text":"partial"}]}"#, #"{"status":"completed","error":{},"outputs":[]}"#, #"{"status":"completed","outputs":[{"type":"tool_call","text":"not transcript"}]}"#, #"{"text":""}"#] {
            XCTAssertThrowsError(try NebulaAudioProtocol.transcript(provider: .gemini, data: Data(json.utf8)))
        }
    }
    func testSpeechExactTextVoiceAndLegacyOptions() throws {
        func body(_ provider: NebulaAiProvider, _ model: String) throws -> [String: Any] { try JSONSerialization.jsonObject(with: NebulaAudioProtocol.speech(provider: provider, model: model, text: "Привет", voice: provider == .openAI ? "marin" : "Kore", style: "warm", speed: 1)) as! [String: Any] }
        let openai = try body(.openAI, "gpt-4o-mini-tts")
        XCTAssertEqual(openai["input"] as? String, "Привет"); XCTAssertEqual(openai["response_format"] as? String, "wav"); XCTAssertNotNil(openai["instructions"])
        XCTAssertNil(try body(.openAI, "tts-1-hd")["instructions"])
        let gemini = try body(.gemini, "gemini-3.8-flash-tts")
        XCTAssertEqual((gemini["response_format"] as? [String: String])?["type"], "audio")
        let content = ((gemini["input"] as? [[String: Any]])?.first?["content"] as? [[String: Any]])?.first
        XCTAssertEqual(content?["text"] as? String, "Привет")
        XCTAssertThrowsError(try NebulaAudioProtocol.speech(provider: .openAI, model: "m", text: String(repeating: "x", count: 4001), voice: "marin", style: "neutral", speed: 1))
        XCTAssertThrowsError(try NebulaAudioProtocol.speech(provider: .openAI, model: "m", text: "hi", voice: "fake", style: "neutral", speed: 1))
        XCTAssertThrowsError(try NebulaAudioProtocol.speech(provider: .openAI, model: "m", text: "hi", voice: "marin", style: "neutral", speed: .nan))
    }
    func testGeneratedWaveAndPCMValidation() throws {
        let pcm = Data([0, 0, 1, 0]), wav = try NebulaAudioProtocol.pcmWave(Data([0, 0, 1, 0]))
        XCTAssertEqual(wav.count, 48); XCTAssertEqual(wav.suffix(4), pcm); XCTAssertEqual(Array(wav[24..<28]), [192, 93, 0, 0]); XCTAssertEqual(try NebulaAudioProtocol.wave(wav), wav)
        func result(_ bytes: Data, _ mime: String) throws -> Data { try JSONSerialization.data(withJSONObject: ["status": "completed", "outputs": [["type": "audio", "mime_type": mime, "data": bytes.base64EncodedString()]]]) }
        XCTAssertEqual(try NebulaAudioProtocol.generatedAudio(result(wav, "audio/wav")), wav)
        XCTAssertEqual(try NebulaAudioProtocol.generatedAudio(result(pcm, "audio/l16")), wav)
        XCTAssertThrowsError(try NebulaAudioProtocol.generatedAudio(result(pcm, "text/html")))
        XCTAssertThrowsError(try NebulaAudioProtocol.generatedAudio(Data(#"{"status":"completed","outputs":[{"type":"audio","data":"%%%bad"}]}"#.utf8)))
        XCTAssertThrowsError(try NebulaAudioProtocol.pcmWave(Data([0])))
        XCTAssertThrowsError(try NebulaAudioProtocol.wave(Data("not audio".utf8)))
    }
    func testAudioPreferencesPersistIndependentlyAndNeverFallback() throws {
        let name = "audio-test-\(UUID().uuidString)", defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let audio = NebulaAudioPreferences(defaults: defaults), services = NebulaAiServices(defaults: defaults)
        audio.migrate(services: services); XCTAssertEqual(audio.speechService, "device"); XCTAssertNil(audio.connection(speech: false, services: services))
        audio.transcriptionService = "deleted"; audio.speechService = "selected"; audio.style = "calm"; audio.speed = 1.2
        XCTAssertNil(audio.connection(speech: false, services: services)); audio.speed = .nan; XCTAssertEqual(audio.speed, 1.2)
        audio.setVoice("cedar", provider: .openAI); audio.setVoice("fake", provider: .openAI); XCTAssertEqual(audio.voice(provider: .openAI), "cedar")
        let connection = NebulaAiConnection(name: "OpenAI", provider: .openAI, model: "text-model")
        try audio.setModel("tts-1-hd", speech: true, provider: .openAI)
        XCTAssertEqual(audio.model(speech: true, connection: connection), "tts-1-hd"); XCTAssertEqual(connection.model, "text-model")
        let reopened = NebulaAudioPreferences(defaults: defaults); XCTAssertEqual(reopened.style, "calm"); XCTAssertEqual(reopened.speechService, "selected")
    }
    func testExplicitTranscriptionSwitchPreservesLegacyAndOverridesTextAI() throws {
        let suite = "audio-switch-\(UUID().uuidString)", defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let audio = NebulaAudioPreferences(defaults: defaults)
        XCTAssertFalse(audio.transcriptionEnabled)
        defaults.set(true, forKey: "nebula.ai.enabled"); XCTAssertTrue(audio.transcriptionEnabled)
        audio.transcriptionEnabled = false; XCTAssertFalse(audio.transcriptionEnabled)
        defaults.set(false, forKey: "nebula.ai.enabled"); audio.transcriptionEnabled = true
        XCTAssertTrue(NebulaAudioPreferences(defaults: defaults).transcriptionEnabled)
        XCTAssertFalse(defaults.bool(forKey: "nebula.ai.enabled"))
    }

}
