import Foundation
import Postbox
import SwiftSignalKit
import TelegramApi
import MtProtoKit

public enum NebulaCloudSettingsTransport {
    public enum Failure: Error { case unavailable }
    public struct Record { public let id: Int32; public let text: String }
    public struct Page { public let records: [Record]; public let next: Int32? }
    public static func read(account: Account, offset: Int32) -> Signal<Page, Failure> {
        account.network.request(Api.functions.messages.search(flags: 0, peer: .inputPeerSelf, q: "#NebulaGramSettingsV1", fromId: nil, savedPeerId: nil, savedReaction: nil, topMsgId: nil, filter: .inputMessagesFilterEmpty, minDate: 0, maxDate: 0, offsetId: offset, addOffset: 0, limit: 50, maxId: 0, minId: 0, hash: 0))
        |> mapError { _ in Failure.unavailable }
        |> mapToSignal { result -> Signal<Page, Failure> in
            let messages: [Api.Message]
            switch result {
            case let .messages(data): messages = data.messages
            case let .messagesSlice(data): messages = data.messages
            default: return .fail(.unavailable)
            }
            var records: [Record] = []; var last: Int32?
            for api in messages {
                guard let value = StoreMessage(apiMessage: api, accountPeerId: account.peerId, peerIsForum: false), case let .Id(id) = value.id else { return .fail(.unavailable) }
                last = id.id
                guard id.peerId == account.peerId, value.authorId == account.peerId, value.forwardInfo == nil, !value.flags.contains(.Incoming), value.text.hasPrefix("#NebulaGramSettingsV1\n") else { continue }
                records.append(Record(id: id.id, text: value.text))
            }
            return .single(Page(records: records, next: messages.count == 50 ? last : nil))
        }
    }
    public static func write(account: Account, id: Int32?, text: String, randomId: Int64) -> Signal<Void, Failure> {
        guard text.hasPrefix("#NebulaGramSettingsV1\n"), text.utf16.count <= 4000, randomId != 0 else { return .fail(.unavailable) }
        let request: Signal<Api.Updates, MTRpcError>
        if let id {
            request = account.network.request(Api.functions.messages.editMessage(flags: (1 << 11) | (1 << 1), peer: .inputPeerSelf, id: id, message: text, media: nil, replyMarkup: nil, entities: nil, scheduleDate: nil, scheduleRepeatPeriod: nil, quickReplyShortcutId: nil, richMessage: nil))
        } else {
            request = account.network.request(Api.functions.messages.sendMessage(flags: (1 << 1) | (1 << 5), peer: .inputPeerSelf, replyTo: nil, message: text, randomId: randomId, replyMarkup: nil, entities: nil, scheduleDate: nil, scheduleRepeatPeriod: nil, sendAs: nil, quickReplyShortcut: nil, effect: nil, allowPaidStars: nil, suggestedPost: nil, richMessage: nil))
        }
        return request |> map { updates -> Void in account.stateManager.addUpdates(updates) }
        |> `catch` { error -> Signal<Void, Failure> in error.errorDescription == "MESSAGE_NOT_MODIFIED" ? .single(()) : .fail(.unavailable) }
    }
}
