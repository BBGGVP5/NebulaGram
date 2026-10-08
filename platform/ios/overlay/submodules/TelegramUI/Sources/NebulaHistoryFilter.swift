import Foundation
import AccountContext
import Postbox
import TelegramCore
import SwiftSignalKit
import NebulaSettingsContract

/// Presentation copies only: the original Postbox message, IDs and pagination survive.
final class NebulaHistoryFilter {
    private static let prefix = "nebulagram-filter://" + UUID().uuidString + "/"
    private let context: AccountContext
    private let changed: () -> Void
    private var observer: NSObjectProtocol?
    private var blockedContext: BlockedPeersContext?
    private var blockedDisposable: Disposable?
    init(context: AccountContext, changed: @escaping () -> Void) {
        self.context = context; self.changed = changed
        observer = NotificationCenter.default.addObserver(forName: NebulaMessageFilter.changed, object: nil, queue: .main) { [weak self] note in
            guard let self, let account = note.object as? Int64, account == self.context.account.peerId.toInt64() else { return }
            self.refreshBlocked(); self.changed()
        }
        refreshBlocked()
    }
    private func refreshBlocked() {
        let account = context.account.peerId.toInt64(), rules = NebulaMessageFilter.shared.rules(account: context.account.peerId.toInt64())
        guard rules.enabled && rules.blockedAuthors else { blockedDisposable?.dispose(); blockedDisposable = nil; blockedContext = nil; return }
        guard blockedContext == nil else { return }
        let blocked = BlockedPeersContext(account: context.account, subject: .blocked); blockedContext = blocked
        blockedDisposable = (blocked.state |> deliverOnMainQueue).start(next: { [weak self, weak blocked] state in
            guard self != nil else { return }
            NebulaMessageFilter.shared.updateBlocked(Set(state.peers.map { $0.peerId.toInt64() }), account: account)
            if state.canLoadMore && !state.isLoadingMore {
                Queue.mainQueue().async { [weak blocked] in blocked?.loadMore() }
            }
        })
    }
    deinit { if let observer { NotificationCenter.default.removeObserver(observer) }; blockedDisposable?.dispose() }

    static func mask(_ message: Message, account: PeerId, russian: Bool) -> Message {
        guard !message.media.contains(where: { $0 is TelegramMediaAction }),
              NebulaMessageFilter.shared.hidden(account: account.toInt64(), peer: message.id.peerId.toInt64(), namespace: message.id.namespace, id: message.id.id,
                author: message.author?.id.toInt64(), text: message.text, incoming: message.effectivelyIncoming(account), sponsored: message.attributes.contains { $0 is AdMessageAttribute }) else { return message }
        let label = russian ? "Скрыто по фильтру · показать" : "Filtered message · reveal"
        let url = prefix + "\(account.toInt64())/\(message.id.peerId.toInt64())/\(message.id.namespace)/\(message.id.id)"
        return Message(stableId: message.stableId, stableVersion: message.stableVersion ^ (1 << 30), id: message.id,
            globallyUniqueId: message.globallyUniqueId, groupingKey: nil, groupInfo: nil, threadId: message.threadId,
            timestamp: message.timestamp, flags: message.flags, tags: [], globalTags: [], localTags: message.localTags,
            customTags: [], forwardInfo: nil, author: message.author, text: label,
            attributes: [TextEntitiesMessageAttribute(entities: [MessageTextEntity(range: 0..<(label as NSString).length, type: .TextUrl(url: url))])],
            media: [], peers: message.peers, associatedMessages: SimpleDictionary(), associatedMessageIds: [], associatedMedia: [:], associatedThreadInfo: message.associatedThreadInfo, associatedStories: [:])
    }
    static func reveal(url: String, account: PeerId) -> Bool {
        guard url.hasPrefix(prefix) else { return false }
        let parts = url.dropFirst(prefix.count).split(separator: "/")
        guard parts.count == 4, Int64(parts[0]) == account.toInt64(), let peer = Int64(parts[1]), let namespace = Int32(parts[2]), let id = Int32(parts[3]) else { return true }
        NebulaMessageFilter.shared.reveal(account: account.toInt64(), peer: peer, namespace: namespace, id: id)
        return true
    }
}
