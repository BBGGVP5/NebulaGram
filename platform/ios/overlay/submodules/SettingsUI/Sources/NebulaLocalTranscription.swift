import Foundation
import AVFoundation
import Speech
import NebulaSettingsContract

@available(iOS 15.0, *)
@MainActor
enum NebulaLocalTranscription {
    enum Failure: Error { case denied, unavailable, timeout, recognition }

    static func availability(locale: String, russian: Bool) -> String {
        switch SFSpeechRecognizer.authorizationStatus() {
        case .notDetermined: return russian ? "Разрешение будет запрошено при расшифровке" : "Permission is requested when you transcribe"
        case .denied, .restricted: return russian ? "Нет разрешения на распознавание речи" : "Speech recognition permission is unavailable"
        default: break
        }
        let ready = SFSpeechRecognizer(locale: Locale(identifier: locale)).map { $0.supportsOnDeviceRecognition && $0.isAvailable } ?? false
        return ready ? (russian ? "Локальное распознавание доступно" : "On-device recognition is available") : (russian ? "Локальная модель этого языка недоступна" : "This language's on-device model is unavailable")
    }

    static func authorize() async throws {
        try Task.checkCancellation()
        let status = SFSpeechRecognizer.authorizationStatus()
        let result: SFSpeechRecognizerAuthorizationStatus
        if status == .notDetermined {
            result = await withCheckedContinuation { continuation in SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) } }
        } else { result = status }
        try Task.checkCancellation()
        guard result == .authorized else { throw Failure.denied }
    }

    static func requireAvailable(locale: String) throws {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: locale)), recognizer.supportsOnDeviceRecognition, recognizer.isAvailable else { throw Failure.unavailable }
    }

    static func transcribe(pcm: URL, directory: URL, locale: String, partial: @escaping (String) -> Void) async throws -> String {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: locale)), recognizer.supportsOnDeviceRecognition, recognizer.isAvailable else { throw Failure.unavailable }
        let input = try AVAudioFile(forReading: pcm, commonFormat: .pcmFormatInt16, interleaved: true)
        let ranges = try NebulaLocalAudioPolicy.ranges(frameCount: Int(input.length))
        var finals: [String] = []
        for range in ranges {
            try Task.checkCancellation()
            guard recognizer.supportsOnDeviceRecognition, recognizer.isAvailable,
                  let buffer = AVAudioPCMBuffer(pcmFormat: input.processingFormat, frameCapacity: AVAudioFrameCount(range.count)) else { throw Failure.unavailable }
            input.framePosition = AVAudioFramePosition(range.lowerBound)
            try input.read(into: buffer, frameCount: AVAudioFrameCount(range.count))
            guard buffer.frameLength == AVAudioFrameCount(range.count) else { throw NebulaLocalAudioPolicy.Failure.incomplete }
            let chunk = directory.appendingPathComponent("segment.caf")
            do {
                let output = try AVAudioFile(forWriting: chunk, settings: buffer.format.settings, commonFormat: .pcmFormatInt16, interleaved: true)
                try output.write(from: buffer)
            }
            defer { try? FileManager.default.removeItem(at: chunk) }
            let request = NebulaLocalSpeechRequest(recognizer: recognizer)
            let completed = finals.filter { !$0.isEmpty }
            let final = try await request.run(url: chunk) { value in partial((completed + [value]).joined(separator: "\n")) }
            finals.append(final)
        }
        return try NebulaLocalAudioPolicy.joinedFinals(finals, expectedCount: ranges.count)
    }

    static func message(for error: Error, russian: Bool) -> String {
        switch error {
        case Failure.denied: return russian ? "Разрешите распознавание речи для NebulaGram в настройках iOS." : "Allow speech recognition for NebulaGram in iOS Settings."
        case Failure.unavailable: return russian ? "Локальное распознавание этого языка недоступно. Выберите другой язык или облачный сервис в «Аудио и голоса»." : "On-device recognition is unavailable for this language. Choose another language or a cloud service in Audio & voices."
        case Failure.timeout: return russian ? "Локальное распознавание не завершилось вовремя. Попробуйте ещё раз." : "On-device recognition timed out. Please try again."
        case NebulaLocalAudioPolicy.Failure.noSpeech: return russian ? "В записи не удалось распознать речь." : "No speech could be recognized in this recording."
        case NebulaLocalAudioPolicy.Failure.invalidRecording: return russian ? "Не удалось прочитать запись. Локальный режим поддерживает записи до 14 МБ и 10 минут." : "The recording could not be read. Local mode supports recordings up to 14 MB and 10 minutes."
        default: return russian ? "Расшифровка не завершена. Неполный результат не сохранён; попробуйте ещё раз." : "Transcription did not finish. The incomplete result was not saved; please try again."
        }
    }
}

// All terminal callbacks and cancellation are serialized on the main actor.
// Only isFinal closes a successful request; a partial result is never promoted.
@available(iOS 15.0, *)
@MainActor
private final class NebulaLocalSpeechRequest {
    private let recognizer: SFSpeechRecognizer
    private let timeout: TimeInterval
    private var task: SFSpeechRecognitionTask?
    private var deadline: Task<Void, Never>?
    private var continuation: CheckedContinuation<String, Error>?
    init(recognizer: SFSpeechRecognizer, timeout: TimeInterval = 90) { self.recognizer = recognizer; self.timeout = timeout }
    func run(url: URL, partial: @escaping (String) -> Void) async throws -> String {
        try Task.checkCancellation()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                guard !Task.isCancelled, recognizer.supportsOnDeviceRecognition, recognizer.isAvailable else { finish(.failure(NebulaLocalTranscription.Failure.unavailable)); return }
                let request = SFSpeechURLRecognitionRequest(url: url)
                request.requiresOnDeviceRecognition = true; request.shouldReportPartialResults = true; request.taskHint = .dictation
                deadline = Task { @MainActor [weak self, timeout] in
                    do { try await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000)) }
                    catch { return }
                    self?.finish(.failure(NebulaLocalTranscription.Failure.timeout))
                }
                task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                    Task { @MainActor in
                        guard let self, self.continuation != nil else { return }
                        if error != nil { self.finish(.failure(NebulaLocalTranscription.Failure.recognition)); return }
                        if let result {
                            let text = result.bestTranscription.formattedString
                            guard text.utf16.count <= 100_000 else { self.finish(.failure(NebulaLocalAudioPolicy.Failure.incomplete)); return }
                            if result.isFinal { self.finish(.success(text)) } else { partial(String(text.suffix(2000))) }
                        }
                    }
                }
            }
        }, onCancel: { Task { @MainActor [weak self] in self?.finish(.failure(CancellationError())) } })
    }
    private func finish(_ result: Result<String, Error>) {
        guard let callback = continuation else { return }; continuation = nil
        deadline?.cancel(); deadline = nil; task?.cancel(); task = nil
        callback.resume(with: result)
    }
}
