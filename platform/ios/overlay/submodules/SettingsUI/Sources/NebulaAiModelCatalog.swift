import Foundation
import NebulaSettingsContract

private final class NebulaCatalogRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

enum NebulaAiModelCatalog {
    @available(iOS 15.0, *)
    static func load(connection: NebulaAiConnection, key: String) async throws -> [String] {
        guard connection.provider != .appleIntelligence, let base = connection.url else { throw NebulaAiServiceError.invalidConfiguration }
        guard !key.isEmpty else { throw NebulaAiServiceError.missingKey }
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.urlCache = nil; config.httpShouldSetCookies = false
        config.timeoutIntervalForRequest = 20; config.timeoutIntervalForResource = 60
        let session = URLSession(configuration: config, delegate: NebulaCatalogRedirects(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var result: Set<String> = [], cursors: Set<String> = [], cursor: String?, total = 0
        for _ in 0..<20 {
            try Task.checkCancellation()
            guard var url = URLComponents(url: base.appendingPathComponent("models"), resolvingAgainstBaseURL: false) else { throw NebulaAiServiceError.invalidConfiguration }
            if let cursor { url.queryItems = [URLQueryItem(name: connection.provider == .gemini ? "pageToken" : "after_id", value: cursor)] }
            guard let requestURL = url.url else { throw NebulaAiServiceError.invalidConfiguration }
            var request = URLRequest(url: requestURL); request.setValue("application/json", forHTTPHeaderField: "Accept")
            switch connection.provider {
            case .claude: request.setValue(key, forHTTPHeaderField: "x-api-key"); request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
            case .gemini: request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
            default: request.setValue("Bearer " + key, forHTTPHeaderField: "Authorization")
            }
            let (bytes, response) = try await session.bytes(for: request)
            guard let response = response as? HTTPURLResponse else { throw NebulaAiServiceError.invalidResponse }
            guard (200..<300).contains(response.statusCode) else { throw NebulaAiServiceError.httpStatus(response.statusCode) }
            var data = Data()
            for try await byte in bytes {
                try Task.checkCancellation(); total += 1
                guard data.count < 2_000_000, total <= 8_000_000 else { throw NebulaAiServiceError.invalidResponse }; data.append(byte)
            }
            let page = try NebulaAiModelPage.decode(data, provider: connection.provider)
            result.formUnion(page.models); guard result.count <= 5000 else { throw NebulaAiServiceError.invalidResponse }
            guard let next = page.cursor else {
                guard !result.isEmpty else { throw NebulaAiServiceError.invalidResponse }; return result.sorted()
            }
            guard cursors.insert(next).inserted else { throw NebulaAiServiceError.invalidResponse }; cursor = next
        }
        throw NebulaAiServiceError.invalidResponse
    }
}
