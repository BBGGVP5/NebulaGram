import Foundation
import NebulaSettingsContract
#if canImport(FoundationModels)
import FoundationModels
#endif

enum NebulaAiServiceError: LocalizedError {
    case missingKey
    case invalidConfiguration
    case emptyInput
    case inputTooLong
    case localModelUnavailable
    case invalidResponse
    case httpStatus(Int)

    var errorDescription: String? {
        switch self {
        case .missingKey: return "Add an API key for the selected provider."
        case .invalidConfiguration: return "Check the model and HTTPS API address."
        case .emptyInput: return "Enter text to send."
        case .inputTooLong: return "The selected text is too long."
        case .localModelUnavailable: return "Apple's on-device model is unavailable on this device or is still preparing."
        case .invalidResponse: return "The provider returned an unsupported response."
        case let .httpStatus(code): return "The provider returned HTTP \(code). Check its settings and try again."
        }
    }
}

/// Makes a single explicit request. The local Apple model never enters the
/// URLSession path, so selected text cannot accidentally fall back to cloud.
final class NebulaAiService {
    private let settings: NebulaAiSettings
    private let secrets: NebulaAiSecrets
    private let session: URLSession
    private let overrideInstructions: String?
    private var instructions: String { overrideInstructions ?? settings.instructions }

    init(settings: NebulaAiSettings = .shared, secrets: NebulaAiSecrets = .shared, instructions: String? = nil) {
        self.overrideInstructions = instructions
        self.settings = settings
        self.secrets = secrets
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: configuration)
    }

    static var localModelAvailable: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) { return SystemLanguageModel.default.isAvailable }
        #endif
        return false
    }

    static var localModelPreparing: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *), case .unavailable(.modelNotReady) = SystemLanguageModel.default.availability { return true }
        #endif
        return false
    }

    static func message(for error: Error, russian: Bool) -> String {
        guard let error = error as? NebulaAiServiceError else {
            return russian ? "Не удалось получить ответ. Проверьте подключение и настройки модели." : "Could not get a response. Check the connection and model settings."
        }
        guard russian else { return error.localizedDescription }
        switch error {
        case .missingKey: return "Добавьте API-ключ в настройках подключения."
        case .invalidConfiguration: return "Проверьте модель и HTTPS-адрес сервиса."
        case .emptyInput: return "Введите сообщение."
        case .inputTooLong: return "Сократите сообщение или инструкции для выбранной модели."
        case .localModelUnavailable: return localModelStatus(russian: true)
        case .invalidResponse: return "Модель не вернула текст. Попробуйте изменить запрос."
        case let .httpStatus(code): return "Сервис вернул HTTP \(code). Проверьте ключ, модель и лимиты провайдера."
        }
    }

    static func localModelStatus(russian: Bool) -> String {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available: return russian ? "Модель готова. Текст обрабатывается на устройстве." : "Ready. Text is processed on this device."
            case .unavailable(.deviceNotEligible): return russian ? "Это устройство не поддерживает системную модель Apple Intelligence." : "This device does not support the Apple Intelligence system model."
            case .unavailable(.appleIntelligenceNotEnabled): return russian ? "Включите Apple Intelligence в настройках iOS." : "Enable Apple Intelligence in iOS Settings."
            case .unavailable(.modelNotReady): return russian ? "iOS подготавливает модель. Статус обновится автоматически; загрузкой управляет система." : "iOS is preparing the model. Status updates automatically; the system manages the download."
            case .unavailable: return russian ? "Системная модель сейчас недоступна." : "The system model is currently unavailable."
            @unknown default: return russian ? "Не удалось определить доступность модели." : "Model availability could not be determined."
            }
        }
        #endif
        return russian ? "Нужны iOS 26 и устройство с поддержкой Apple Intelligence." : "Requires iOS 26 and an Apple Intelligence capable device."
    }

    func generate(input: String) async throws -> String {
        let input = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { throw NebulaAiServiceError.emptyInput }
        guard input.count <= 50_000, instructions.count <= 20_000 else {
            throw NebulaAiServiceError.inputTooLong
        }
        if settings.provider == .appleIntelligence {
            return try await generateOnDevice(input: input)
        }
        return try await generateRemote(input: input)
    }

    private func generateOnDevice(input: String) async throws -> String {
        #if canImport(FoundationModels)
        guard #available(iOS 26.0, *), SystemLanguageModel.default.isAvailable else {
            throw NebulaAiServiceError.localModelUnavailable
        }
        let session = LanguageModelSession(instructions: instructions)
        let response = try await session.respond(to: input)
        let result = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !result.isEmpty else { throw NebulaAiServiceError.invalidResponse }
        return result
        #else
        throw NebulaAiServiceError.localModelUnavailable
        #endif
    }

    private func generateRemote(input: String) async throws -> String {
        let provider = settings.provider
        guard let key = try secrets.key(for: provider), !key.isEmpty else {
            throw NebulaAiServiceError.missingKey
        }
        guard let base = settings.endpoint(for: provider),
              let modelValue = settings.model(for: provider).trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty else {
            throw NebulaAiServiceError.invalidConfiguration
        }
        let model = modelValue.hasPrefix("models/") ? String(modelValue.dropFirst(7)) : modelValue
        let encodedModel = model.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlPathAllowed.subtracting(CharacterSet(charactersIn: "?#"))) ?? ""
        guard !encodedModel.isEmpty else { throw NebulaAiServiceError.invalidConfiguration }
        let request: URLRequest
        switch provider {
        case .openAI:
            request = try makeRequest(base: base, path: "responses", key: key, provider: provider,
                body: ["model": model, "instructions": instructions, "input": input,
                       "max_output_tokens": 8192, "store": false])
        case .claude:
            var body: [String: Any] = ["model": model, "messages": [["role": "user", "content": input]], "max_tokens": 4096]
            if !instructions.isEmpty { body["system"] = instructions }
            request = try makeRequest(base: base, path: "messages", key: key, provider: provider, body: body)
        case .gemini:
            var body: [String: Any] = ["contents": [["role": "user", "parts": [["text": input]]]]]
            if !instructions.isEmpty { body["systemInstruction"] = ["parts": [["text": instructions]]] }
            request = try makeRequest(base: base, path: "models/\(encodedModel):generateContent", key: key, provider: provider, body: body)
        case .custom:
            var messages: [[String: String]] = []
            if !instructions.isEmpty { messages.append(["role": "system", "content": instructions]) }
            messages.append(["role": "user", "content": input])
            request = try makeRequest(base: base, path: "chat/completions", key: key, provider: provider,
                body: ["model": model, "messages": messages])
        case .appleIntelligence:
            throw NebulaAiServiceError.invalidConfiguration
        }
        let (data, response) = try await data(for: request)
        guard let response = response as? HTTPURLResponse else { throw NebulaAiServiceError.invalidResponse }
        guard (200..<300).contains(response.statusCode) else { throw NebulaAiServiceError.httpStatus(response.statusCode) }
        guard data.count <= 4_000_000,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let output = Self.extractOutput(provider: provider, json: json), !output.isEmpty else {
            throw NebulaAiServiceError.invalidResponse
        }
        return String(output.prefix(100_000))
    }

    private func makeRequest(base: URL, path: String, key: String, provider: NebulaAiProvider,
                             body: [String: Any]) throws -> URLRequest {
        let baseString = base.absoluteString.hasSuffix("/") ? String(base.absoluteString.dropLast()) : base.absoluteString
        guard let url = URL(string: "\(baseString)/\(path)"),
              url.scheme?.lowercased() == "https", url.host == base.host else {
            throw NebulaAiServiceError.invalidConfiguration
        }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 120)
        request.httpMethod = "POST"
        request.httpShouldHandleCookies = false
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        switch provider {
        case .claude:
            request.setValue(key, forHTTPHeaderField: "x-api-key")
            request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        case .gemini:
            request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
        case .openAI, .custom:
            request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        case .appleIntelligence:
            throw NebulaAiServiceError.invalidConfiguration
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [.withoutEscapingSlashes])
        return request
    }

    private func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        if #available(iOS 15.0, *) { return try await session.data(for: request) }
        let cancellation = NebulaAiTransferCancellation()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                let task = session.dataTask(with: request) { data, response, error in
                    if let error = error { continuation.resume(throwing: error); return }
                    guard let data = data, let response = response else {
                        continuation.resume(throwing: NebulaAiServiceError.invalidResponse); return
                    }
                    continuation.resume(returning: (data, response))
                }
                cancellation.start(task)
            }
        }, onCancel: { cancellation.cancel() })
    }

    private static func extractOutput(provider: NebulaAiProvider, json: [String: Any]) -> String? {
        var values: [String] = []
        switch provider {
        case .openAI:
            if let outputText = json["output_text"] as? String { values.append(outputText) }
            if values.isEmpty, let output = json["output"] as? [[String: Any]] {
                for item in output {
                    guard let content = item["content"] as? [[String: Any]] else { continue }
                    values += content.compactMap { part in
                        if part["type"] as? String == "refusal" { return part["refusal"] as? String }
                        return part["text"] as? String
                    }
                }
            }
        case .claude:
            values = (json["content"] as? [[String: Any]] ?? []).compactMap { $0["text"] as? String }
        case .gemini:
            let candidates = json["candidates"] as? [[String: Any]] ?? []
            let parts = candidates.first?["content"] as? [String: Any]
            values = (parts?["parts"] as? [[String: Any]] ?? []).compactMap { $0["text"] as? String }
        case .custom:
            let choices = json["choices"] as? [[String: Any]] ?? []
            let message = choices.first?["message"] as? [String: Any]
            if let content = message?["content"] as? String { values.append(content) }
            if values.isEmpty, let refusal = message?["refusal"] as? String { values.append(refusal) }
        case .appleIntelligence: break
        }
        let result = values.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        return result.isEmpty ? nil : result
    }
}

private final class NebulaAiTransferCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var task: URLSessionDataTask?
    private var cancelled = false
    func start(_ value: URLSessionDataTask) {
        lock.lock(); task = value; let shouldCancel = cancelled; lock.unlock()
        if shouldCancel { value.cancel() }
        value.resume()
    }
    func cancel() {
        lock.lock(); cancelled = true; let value = task; lock.unlock()
        value?.cancel()
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
