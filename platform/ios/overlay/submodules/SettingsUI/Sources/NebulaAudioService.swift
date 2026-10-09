import Foundation
import NebulaSettingsContract

private final class NebulaAudioRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

/// Connection, key and audio options are captured before any recording download.
struct NebulaAudioService {
    private let base: URL, key: String, model: String, voice: String, style: String, speed: Double
    private let provider: NebulaAiProvider
    let title: String
    init(speech: Bool = false, settings: NebulaAiSettings = .shared, audio: NebulaAudioPreferences = .shared) throws {
        audio.migrate(services: settings.services)
        guard let connection = audio.connection(speech: speech, services: settings.services), let base = connection.url else { throw NebulaAiServiceError.invalidConfiguration }
        guard let key = try NebulaAiSecrets.shared.serviceKey(id: connection.id), !key.isEmpty, !key.contains("\r"), !key.contains("\n") else { throw NebulaAiServiceError.missingKey }
        self.base = base; self.key = key; self.provider = connection.provider; self.title = connection.name
        self.model = try NebulaAudioProtocol.model(audio.model(speech: speech, connection: connection))
        self.voice = audio.voice(provider: connection.provider); self.style = audio.style; self.speed = audio.speed
    }
    @available(iOS 15.0, *)
    func transcribe(file: URL, mime: String) async throws -> String {
        try Task.checkCancellation()
        let media = try NebulaAudioTranscription.read(file, cancelled: { Task.isCancelled })
        let boundary = "nebula-" + UUID().uuidString
        let body: Data
        if provider == .openAI { body = try NebulaAudioProtocol.multipart(data: media, mime: mime, model: model, boundary: boundary) }
        else { body = try NebulaAudioTranscription.payload(data: media, mime: mime, model: model) }
        let response = try await request(path: provider == .openAI ? "audio/transcriptions" : "interactions", body: body, type: provider == .openAI ? "multipart/form-data; boundary=\(boundary)" : "application/json", maximum: 2_000_000)
        return try NebulaAudioProtocol.transcript(provider: provider, data: response)
    }
    @available(iOS 15.0, *)
    func speech(text: String) async throws -> Data {
        let body = try NebulaAudioProtocol.speech(provider: provider, model: model, text: text, voice: voice, style: style, speed: speed)
        let response = try await request(path: provider == .openAI ? "audio/speech" : "interactions", body: body, type: "application/json", maximum: provider == .openAI ? NebulaAudioProtocol.maximumAudio : NebulaAudioProtocol.maximumJSON)
        if provider == .openAI { return try NebulaAudioProtocol.wave(response) }
        return try NebulaAudioProtocol.generatedAudio(response)
    }
    @available(iOS 15.0, *)
    private func request(path: String, body: Data, type: String, maximum: Int) async throws -> Data {
        try Task.checkCancellation()
        let config = URLSessionConfiguration.ephemeral; config.httpCookieStorage = nil; config.httpShouldSetCookies = false; config.urlCache = nil
        config.timeoutIntervalForRequest = 60; config.timeoutIntervalForResource = 180
        let session = URLSession(configuration: config, delegate: NebulaAudioRedirects(), delegateQueue: nil); defer { session.invalidateAndCancel() }
        var request = URLRequest(url: base.appendingPathComponent(path)); request.httpMethod = "POST"; request.httpBody = body
        request.setValue(type, forHTTPHeaderField: "Content-Type"); request.setValue(provider == .openAI ? "Bearer " + key : key, forHTTPHeaderField: provider == .openAI ? "Authorization" : "x-goog-api-key")
        let (bytes, response) = try await session.bytes(for: request)
        guard let response = response as? HTTPURLResponse else { throw NebulaAiServiceError.invalidResponse }
        guard (200..<300).contains(response.statusCode) else { throw NebulaAiServiceError.httpStatus(response.statusCode) }
        guard response.expectedContentLength <= maximum else { throw NebulaAiServiceError.invalidResponse }
        var data = Data()
        for try await byte in bytes { try Task.checkCancellation(); guard data.count < maximum else { throw NebulaAiServiceError.invalidResponse }; data.append(byte) }
        return data
    }
}
