import Foundation
import NebulaSettingsContract

private final class NebulaAudioRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

/// Capture connection and key before downloading media; changing settings later
/// cannot reroute an already approved recording to another provider.
struct NebulaAudioService {
    private let url: URL, key: String, model: String
    init(settings: NebulaAiSettings = .shared) throws {
        guard settings.enabled, settings.provider == .gemini, let base = settings.endpoint(for: .gemini) else { throw NebulaAiServiceError.invalidConfiguration }
        guard let key = try settings.apiKey(), !key.isEmpty else { throw NebulaAiServiceError.missingKey }
        model = settings.model(for: .gemini); guard !model.isEmpty else { throw NebulaAiServiceError.invalidConfiguration }
        self.key = key; url = base.appendingPathComponent("interactions")
    }
    @available(iOS 15.0, *)
    func transcribe(file: URL, mime: String) async throws -> String {
        try Task.checkCancellation()
        let body = try NebulaAudioTranscription.payload(data: NebulaAudioTranscription.read(file, cancelled: { Task.isCancelled }), mime: mime, model: model)
        let config = URLSessionConfiguration.ephemeral; config.httpCookieStorage = nil; config.httpShouldSetCookies = false; config.urlCache = nil
        config.timeoutIntervalForRequest = 60; config.timeoutIntervalForResource = 120
        let session = URLSession(configuration: config, delegate: NebulaAudioRedirects(), delegateQueue: nil); defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url); request.httpMethod = "POST"; request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type"); request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
        let (bytes, response) = try await session.bytes(for: request)
        guard let response = response as? HTTPURLResponse else { throw NebulaAiServiceError.invalidResponse }
        guard (200..<300).contains(response.statusCode) else { throw NebulaAiServiceError.httpStatus(response.statusCode) }
        guard response.expectedContentLength <= 2_000_000 else { throw NebulaAiServiceError.invalidResponse }
        var data = Data()
        for try await byte in bytes { try Task.checkCancellation(); guard data.count < 2_000_000 else { throw NebulaAiServiceError.invalidResponse }; data.append(byte) }
        return try NebulaAudioTranscription.transcript(data)
    }
}
