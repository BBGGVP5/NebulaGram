"""Compile and execute the production Swift visible queue with cancellable fake transport."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
swift = shutil.which('swiftc')
if not swift:
    raise SystemExit('swiftc is required (run this check on the iOS CI host)')
production = root / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaLiveTranslation.swift'
source = '\n'.join(line for line in production.read_text(encoding='utf-8').splitlines() if not line.startswith('import '))
contract = root / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract'
source = '\n'.join((contract / name).read_text(encoding='utf-8') for name in ['NebulaTranslationPreferences.swift', 'NebulaTranslationActivity.swift']) + '\n' + source
stubs = r'''
import Foundation
public struct PeerId: Hashable { let value: Int64; var namespace: Int32 { 0 }; func toInt64() -> Int64 { value } }
struct MessageId: Hashable { let peerId: PeerId; let namespace: Int32; let id: Int32 }
enum Namespaces { enum Peer { static let SecretChat: Int32 = 1 }; enum Message { static let Cloud: Int32 = 0 } }
struct MessageFlags: OptionSet { let rawValue: Int; static let Incoming = MessageFlags(rawValue: 1) }
struct StoreMessageFlags { let flags: MessageFlags; init(_ flags: MessageFlags) { self.flags = flags } }
protocol MessageAttribute {}
typealias MessageTextEntity = Int
struct TextEntitiesMessageAttribute: MessageAttribute { let entities: [MessageTextEntity] }
func chatInputStateStringWithAppliedEntities(_ text: String, entities: [MessageTextEntity]) -> NSAttributedString { NSAttributedString(string: text) }
func generateChatInputTextEntities(_ text: NSAttributedString) -> [MessageTextEntity] { [] }
enum NebulaRichEditorTransform {
    static func generate(_ text: NSAttributedString, instruction: String) async throws -> NSAttributedString {
        NSAttributedString(string: try await NebulaAiService(instructions: instruction).generate(input: text.string))
    }
}
struct AutoremoveTimeoutMessageAttribute: MessageAttribute {}
struct AutoclearTimeoutMessageAttribute: MessageAttribute {}
struct TranslationMessageAttribute: MessageAttribute { let text: String; let entities: [Int]; let toLang: String }
struct Author { let id: PeerId }
struct ForwardInfo {}
struct StoreMessageForwardInfo { init(_ info: ForwardInfo) {} }
public final class Message {
    let id: MessageId; var text: String; let flags: MessageFlags
    var attributes: [MessageAttribute] = []; var protected = false
    var containsSecretMedia = false; var adAttribute: Int? = nil
    var author: Author? = nil; var globallyUniqueId: Int64? = nil; var groupingKey: Int64? = nil; var threadId: Int64? = nil
    var timestamp: Int32 = 0; var tags = 0; var globalTags = 0; var localTags = 0; var forwardInfo: ForwardInfo? = nil; var media: [Int] = []
    init(_ id: Int32, _ text: String, incoming: Bool = true) { self.id = MessageId(peerId: PeerId(value: 9), namespace: 0, id: id); self.text = text; flags = incoming ? .Incoming : [] }
    func isCopyProtected() -> Bool { protected }
}
struct StoreMessage {
    let id: MessageId; let customStableId: Int?; let globallyUniqueId: Int64?; let groupingKey: Int64?; let threadId: Int64?
    let timestamp: Int32; let flags: StoreMessageFlags; let tags: Int; let globalTags: Int; let localTags: Int
    let forwardInfo: StoreMessageForwardInfo?; let authorId: PeerId?; let text: String; let attributes: [MessageAttribute]; let media: [Int]
}
enum UpdateMessageResult { case skip; case update(StoreMessage) }
final class Transaction {
    var messages: [MessageId: Message] = [:]
    func updateMessage(_ id: MessageId, update: (Message) -> UpdateMessageResult) {
        guard let message = messages[id] else { return }
        if case let .update(value) = update(message) { precondition(value.text == message.text, "original text mutated"); message.attributes = value.attributes }
    }
}
struct Signal { func startStandalone() {} }
final class Postbox { let tx = Transaction(); func transaction(_ f: (Transaction) -> Void) -> Signal { f(tx); return Signal() } }
final class Account { let peerId = PeerId(value: 777777); let postbox = Postbox() }
struct Strings { let baseLanguageCode = "en" }
struct Presentation { let strings = Strings() }
struct PresentationData { func with<T>(_ f: (Presentation) -> T) -> T { f(Presentation()) } }
struct SharedContext { let currentPresentationData = PresentationData() }
public final class AccountContext { let account = Account(); let sharedContext = SharedContext() }
enum Provider: Int { case remote, appleIntelligence }
final class NebulaAiSettings { static let shared = NebulaAiSettings()
    func conversationIdentity(action: String, language: String, instructions: String) -> String { "\(provider.rawValue):\(model(for: provider)):\(customEndpoint)" }; var enabled = true; var provider = Provider.remote; var customEndpoint = "https://example.test"; var instructions = ""; func isConfigured() -> Bool { true }; func model(for: Provider) -> String { "model" } }
enum NebulaAiServiceError: Error { case invalidConfiguration }
struct NebulaAiService {
    static var localModelAvailable = true; static var active = 0; static var peak = 0; static var calls = 0; static var blocked = false
    let instructions: String
    func generate(input: String) async throws -> String {
        Self.calls += 1; Self.active += 1; Self.peak = max(Self.peak, Self.active)
        defer { Self.active -= 1 }
        while Self.blocked { try await Task.sleep(nanoseconds: 1_000_000) }
        await Task.yield(); try Task.checkCancellation(); return "AI:" + input
    }
    static func message(for: Error, russian: Bool) -> String { "Unavailable" }
}
@main struct Check {
    @MainActor static func waitFor(_ context: String, _ condition: () -> Bool) async throws {
        for _ in 0..<300 { if condition() { return }; try await Task.sleep(nanoseconds: 10_000_000) }
        preconditionFailure(context)
    }
    @MainActor static func main() async throws {
        let context = AccountContext(), peer = PeerId(value: 9), settings = NebulaTranslationPreferences.shared
        let account = String(context.account.peerId.toInt64()), key = String(peer.toInt64())
        defer { settings.update(account: account, peer: key) { $0.incoming = false; $0.outgoing = false; $0.draft = false } }
        let engine = NebulaLiveTranslation()
        let own = Message(1, "sent", incoming: false), incoming = Message(2, "incoming"), protected = Message(3, "private")
        protected.protected = true
        for message in [own, incoming, protected] { context.account.postbox.tx.messages[message.id] = message }
        settings.update(account: account, peer: key) { $0.incoming = false; $0.outgoing = true; $0.draft = false }
        engine.update(context: context, peer: peer, messages: [own, incoming, protected], allowed: true)
        try await waitFor("sent result completes") { own.attributes.count == 1 && NebulaAiService.active == 0 }
        precondition(own.attributes.count == 1 && incoming.attributes.isEmpty && protected.attributes.isEmpty, "independent outgoing-only consent")
        precondition(own.text == "sent", "original sent text preserved")
        let calls = NebulaAiService.calls
        engine.update(context: context, peer: peer, messages: [own], allowed: true)
        try await Task.sleep(nanoseconds: 20_000_000)
        precondition(NebulaAiService.calls == calls, "cached translated messages are not requested again")
        settings.update(account: account, peer: key) { $0.incoming = true; $0.outgoing = false }
        let a = Message(10, "A"), b = Message(11, "B"), c = Message(12, "C")
        for message in [a,b,c] { context.account.postbox.tx.messages[message.id] = message }
        NebulaAiService.peak = 0; NebulaAiService.blocked = true
        engine.update(context: context, peer: peer, messages: [a,b,c,protected], allowed: true)
        try await waitFor("two remote requests start") { NebulaAiService.active == 2 }
        precondition(NebulaAiService.active == 2, "two remote requests start together")
        engine.update(context: context, peer: peer, messages: [c], allowed: true)
        NebulaAiService.blocked = false
        try await waitFor("visible result completes after scroll") { c.attributes.count == 1 && NebulaAiService.active == 0 }
        precondition(a.attributes.isEmpty && b.attributes.isEmpty && c.attributes.count == 1, "scroll cancellation rejects offscreen completions")
        precondition(NebulaAiService.peak == 2 && protected.attributes.isEmpty, "bounded transport and protected text")
        let edited = Message(20, "before"); context.account.postbox.tx.messages[edited.id] = edited
        NebulaAiService.blocked = true
        engine.update(context: context, peer: peer, messages: [edited], allowed: true)
        try await waitFor("edited source request starts") { NebulaAiService.active == 1 }; edited.text = "after"
        engine.update(context: context, peer: peer, messages: [edited], allowed: true)
        NebulaAiService.blocked = false
        try await waitFor("edited result completes") { edited.attributes.count == 1 && NebulaAiService.active == 0 }
        precondition((edited.attributes.first as? TranslationMessageAttribute)?.text == "AI:after", "edited source invalidates active request")
        let revoked = Message(21, "revoked"); context.account.postbox.tx.messages[revoked.id] = revoked
        NebulaAiService.blocked = true
        engine.update(context: context, peer: peer, messages: [revoked], allowed: true)
        try await waitFor("revoked source request starts") { NebulaAiService.active == 1 }
        settings.update(account: account, peer: key) { $0.incoming = false }
        engine.update(context: context, peer: peer, messages: [revoked], allowed: true)
        NebulaAiService.blocked = false
        try await waitFor("revoked request cancelled") { NebulaAiService.active == 0 }
        precondition(revoked.attributes.isEmpty, "consent revoked during inference")
        settings.update(account: account, peer: key) { $0.incoming = true }
        NebulaAiSettings.shared.provider = .appleIntelligence; NebulaAiService.peak = 0
        let localCalls = NebulaAiService.calls
        engine.update(context: context, peer: peer, messages: [a,b,c], allowed: true)
        try await waitFor("local requests complete") { NebulaAiService.calls == localCalls + 3 && NebulaAiService.active == 0 }
        precondition(NebulaAiService.peak == 1, "local model remains serialized")
        engine.stop()
        print("Swift visible translation: independent sent consent, immutable originals, cache, scroll/edit/revocation cancellation, remote two/local one passed")
    }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-swift-live-') as temporary:
    path = Path(temporary)
    (path / 'Production.swift').write_text(source, encoding='utf-8')
    (path / 'Check.swift').write_text(stubs, encoding='utf-8')
    executable = path / 'check'
    subprocess.run([swift, '-swift-version', '5', str(path / 'Production.swift'), str(path / 'Check.swift'), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True, timeout=20)
