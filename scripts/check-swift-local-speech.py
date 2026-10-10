"""Run the actual local Speech request lifetime with a deterministic fake SDK.

This proves request/callback/cancellation ownership, not on-device speech quality.
The production adapter is also typechecked against Apple's real SDK in bootstrap.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
swift = shutil.which('swiftc')
if not swift:
    raise SystemExit('swiftc is required (run this check on the macOS iOS CI host)')
source = (root / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaLocalTranscription.swift').read_text(encoding='utf-8')
driver = source[source.index('@available(iOS 15.0, *)\n@MainActor\nprivate final class NebulaLocalSpeechRequest'):]
policy = (root / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaLocalAudioPolicy.swift').read_text(encoding='utf-8')
fixture = r'''
import Foundation
enum NebulaLocalTranscription { enum Failure: Error { case unavailable, timeout, recognition } }
enum Hint { case dictation }
final class SFSpeechURLRecognitionRequest {
    let url: URL; var requiresOnDeviceRecognition = false, shouldReportPartialResults = false; var taskHint = Hint.dictation
    init(url: URL) { self.url = url }
}
final class SFSpeechRecognitionTask { var cancelled = false; func cancel() { cancelled = true } }
struct Transcription { let formattedString: String }
struct SpeechResult { let bestTranscription: Transcription; let isFinal: Bool }
final class SFSpeechRecognizer {
    var supportsOnDeviceRecognition = true, isAvailable = true
    var request: SFSpeechURLRecognitionRequest?, work: SFSpeechRecognitionTask?
    var callback: ((SpeechResult?, Error?) -> Void)?
    func recognitionTask(with request: SFSpeechURLRecognitionRequest, resultHandler: @escaping (SpeechResult?, Error?) -> Void) -> SFSpeechRecognitionTask {
        precondition(request.requiresOnDeviceRecognition && request.shouldReportPartialResults, "must force local recognition")
        self.request = request; callback = resultHandler
        let task = SFSpeechRecognitionTask(); work = task; return task
    }
    func emit(_ text: String, final: Bool = false, error: Error? = nil) { callback?(SpeechResult(bestTranscription: Transcription(formattedString: text), isFinal: final), error) }
}
@main struct Check {
    @MainActor static func waitFor(_ label: String, _ condition: () -> Bool) async throws {
        for _ in 0..<500 { if condition() { return }; try await Task.sleep(nanoseconds: 2_000_000) }
        preconditionFailure(label)
    }
    @MainActor static func main() async throws {
        let url = URL(fileURLWithPath: "/private/segment.caf")
        let sdk = SFSpeechRecognizer(), request = NebulaLocalSpeechRequest(recognizer: sdk)
        var partials: [String] = [], terminal = false
        let result = Task { @MainActor in defer { terminal = true }; return try await request.run(url: url) { partials.append($0) } }
        try await waitFor("request starts") { sdk.callback != nil }
        precondition(sdk.request?.url == url)
        sdk.emit("draft"); try await waitFor("partial arrives") { partials == ["draft"] }; precondition(!terminal)
        sdk.emit("complete", final: true)
        let value = try await result.value; precondition(value == "complete" && sdk.work?.cancelled == true)
        sdk.emit("late", final: true); sdk.emit("late error", error: NebulaLocalTranscription.Failure.recognition)
        try await Task.sleep(nanoseconds: 5_000_000); precondition(partials == ["draft"])

        let cancelledSDK = SFSpeechRecognizer(), cancelledRequest = NebulaLocalSpeechRequest(recognizer: cancelledSDK)
        let cancelled = Task { @MainActor in try await cancelledRequest.run(url: url) { _ in preconditionFailure("cancelled partial") } }
        try await waitFor("cancel request starts") { cancelledSDK.callback != nil }; cancelled.cancel()
        do { _ = try await cancelled.value; preconditionFailure("cancel must throw") } catch is CancellationError {} catch { preconditionFailure("wrong cancel error") }
        precondition(cancelledSDK.work?.cancelled == true); cancelledSDK.emit("late", final: true)

        let earlySDK = SFSpeechRecognizer(), earlyRequest = NebulaLocalSpeechRequest(recognizer: earlySDK)
        let early = Task { @MainActor in try await earlyRequest.run(url: url) { _ in } }; early.cancel()
        do { _ = try await early.value; preconditionFailure("early cancel must throw") } catch is CancellationError {} catch { preconditionFailure("wrong early error") }
        precondition(earlySDK.request == nil)

        let unavailable = SFSpeechRecognizer(); unavailable.supportsOnDeviceRecognition = false
        do { _ = try await NebulaLocalSpeechRequest(recognizer: unavailable).run(url: url) { _ in }; preconditionFailure("no local model must fail") } catch {}
        precondition(unavailable.request == nil)

        let failedSDK = SFSpeechRecognizer(), failedRequest = NebulaLocalSpeechRequest(recognizer: failedSDK)
        let failed = Task { @MainActor in try await failedRequest.run(url: url) { _ in } }
        try await waitFor("error request starts") { failedSDK.callback != nil }
        failedSDK.emit("partial", error: NebulaLocalTranscription.Failure.recognition)
        do { _ = try await failed.value; preconditionFailure("error must not promote partial") } catch {}
        precondition(failedSDK.work?.cancelled == true)

        let longSDK = SFSpeechRecognizer(), longRequest = NebulaLocalSpeechRequest(recognizer: longSDK)
        let long = Task { @MainActor in try await longRequest.run(url: url) { _ in } }
        try await waitFor("oversized request starts") { longSDK.callback != nil }
        longSDK.emit(String(repeating: "🚀", count: 50_001), final: true)
        do { _ = try await long.value; preconditionFailure("oversized output must fail") } catch {}
        precondition(longSDK.work?.cancelled == true)

        let timeoutSDK = SFSpeechRecognizer(), timeoutRequest = NebulaLocalSpeechRequest(recognizer: timeoutSDK, timeout: 0.01)
        let timed = Task { @MainActor in try await timeoutRequest.run(url: url) { _ in } }
        do { _ = try await timed.value; preconditionFailure("deadline must fail") } catch NebulaLocalTranscription.Failure.timeout {} catch { preconditionFailure("wrong deadline error") }
        precondition(timeoutSDK.work?.cancelled == true)
        timeoutSDK.emit("late", final: true)
        print("OK: actual Swift local request final/partial, local-only gate, cancellation, late/error, output bounds and deadline")
    }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-local-speech-') as work:
    file = Path(work) / 'Check.swift'
    file.write_text(policy + '\n' + driver + '\n' + fixture, encoding='utf-8')
    binary = Path(work) / 'check'
    subprocess.run([swift, '-swift-version', '5', '-warnings-as-errors', '-parse-as-library', str(file), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True, timeout=15)
