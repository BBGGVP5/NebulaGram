"""Execute the production recording eligibility gate with native-message-shaped fixtures."""
from pathlib import Path
import shutil, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
swift = shutil.which('swiftc')
if not swift: raise SystemExit('swiftc is required; run on the macOS CI host')
source = (root / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaAudioTranscriptionController.swift').read_text(encoding='utf-8')
start = source.index('    public nonisolated static func eligibleFile(')
opening = source.index('{', start); depth = 1; end = opening + 1
while depth:
    if source[end] == '{': depth += 1
    elif source[end] == '}': depth -= 1
    end += 1
method = source[start:end]
policy = (root / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaAudioTranscription.swift').read_text(encoding='utf-8')
capture = (root / 'platform/ios/overlay/submodules/TelegramCore/Sources/State/NebulaDeletedCapture.swift').read_text(encoding='utf-8')
assert 'public enum NebulaDeletedMessages' in capture and 'public static func isRetained(_ message: Message) -> Bool' in capture
fixture = r'''
import Foundation
enum Namespaces { enum Message { static let Cloud: Int32 = 0 }; enum Peer { static let SecretChat: Int32 = 3 } }
struct PeerId { var namespace: Int32 = 0 }
struct MessageId { var namespace: Int32 = 0, id: Int32 = 1; var peerId = PeerId() }
final class TelegramMediaFile { var isVoice = false, isInstantVideo = false, isVideo = false; var size: Int64? = 1 }
final class Message { var id = MessageId(); var containsSecretMedia = false, protected = false, retained = false; var effectiveMedia: [Any] = []; func isCopyProtected() -> Bool { protected } }
enum NebulaDeletedMessages { static func isRetained(_ message: Message) -> Bool { message.retained } }
@MainActor final class Gate {
'''
fixture += method + r'''
}
@main struct Check {
    static func main() {
        func message(_ file: TelegramMediaFile) -> Message { let message = Message(); message.effectiveMedia = ["not a recording", file]; return message }
        func denied(_ value: Message) { precondition(Gate.eligibleFile(value) == nil, "recording export gate failed") }
        let voice = TelegramMediaFile(); voice.isVoice = true
        let round = TelegramMediaFile(); round.isInstantVideo = true
        let video = TelegramMediaFile(); video.isVideo = true
        for file in [voice, round, video] { precondition(Gate.eligibleFile(message(file)) === file) }
        denied(message(TelegramMediaFile())); denied(Message())
        for size in [nil, 0, -1, 14_000_001, Int64.max] as [Int64?] { let file = TelegramMediaFile(); file.isVoice = true; file.size = size; denied(message(file)) }
        let maximum = TelegramMediaFile(); maximum.isVoice = true; maximum.size = 14_000_000; precondition(Gate.eligibleFile(message(maximum)) === maximum)
        let secret = message(voice); secret.id.peerId.namespace = 3; denied(secret)
        let protected = message(voice); protected.protected = true; denied(protected)
        let temporary = message(voice); temporary.containsSecretMedia = true; denied(temporary)
        let retained = message(voice); retained.retained = true; denied(retained)
        let local = message(voice); local.id.namespace = 1; denied(local)
        for id in [Int32(-1), 0] { let invalid = message(voice); invalid.id.id = id; denied(invalid) }
        print("OK: actual Swift gate admits voice/round/video and rejects protected, secret, temporary, retained, non-cloud and invalid-size recordings")
    }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-recording-gate-') as work:
    file = Path(work) / 'Check.swift'; file.write_text(policy + '\n' + fixture, encoding='utf-8')
    binary = Path(work) / 'check'
    subprocess.run([swift, '-swift-version', '5', '-warnings-as-errors', '-parse-as-library', str(file), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True, timeout=15)
