import Foundation
import Postbox
import SwiftSignalKit
import NebulaSettingsContract

/// Local retention only. Original message IDs/grouping/media and native viewers stay intact.
public enum NebulaDeletedMessages {
    private static let tag = LocalMessageTags(rawValue: 1 << 29)
    public static func isRetained(_ message: Message) -> Bool { message.localTags.contains(tag) }
    public static var icon: String { NebulaDeletedArchive.shared.icon }
    private static func messageId(_ entry: NebulaDeletedEntry) -> MessageId {
        MessageId(peerId: PeerId(entry.peer), namespace: entry.namespace ?? Namespaces.Message.Cloud, id: entry.id)
    }
    public static func clear(account: Account, peer: PeerId? = nil) -> Signal<Bool, NoError> {
        account.postbox.transaction { transaction -> Bool in
            do {
                let archive = NebulaDeletedArchive.shared
                let all = try archive.entries(account: account.peerId.toInt64())
                let remove = all.filter { peer == nil || $0.peer == peer?.toInt64() }
                let ids = remove.map(messageId).filter { id in transaction.getMessage(id).map(isRetained) ?? false }
                // Resource files may be shared with ordinary messages. Only delete retained history rows.
                transaction.deleteMessages(ids, forEachMedia: nil)
                try archive.replace(all.filter { peer != nil && $0.peer != peer?.toInt64() }, account: account.peerId.toInt64())
                return true
            } catch { return false }
        }
    }
    static func retain(transaction: Transaction, accountPeerId: PeerId, ids: [MessageId]) -> [MessageId] {
        let archive = NebulaDeletedArchive.shared
        let account = accountPeerId.toInt64()
        do {
            let all = try archive.entries(account: account)
            var entries = NebulaDeletedArchive.pruned(all)
            var retained = Set(entries.map(messageId))
            var changed: [MessageId] = []
            if archive.enabled(account: account) {
                for id in ids.prefix(NebulaDeletedArchive.limit) {
                    let secret = id.peerId.namespace == Namespaces.Peer.SecretChat
                    guard (id.namespace == Namespaces.Message.Cloud || secret && archive.saveSecret(account: account)),
                          let message = transaction.getMessage(id), message.flags.contains(.Incoming),
                          !message.flags.contains(.CopyProtected),
                          id.peerId != accountPeerId,
                          id.peerId != PeerId(namespace: Namespaces.Peer.CloudUser, id: PeerId.Id._internalFromInt64Value(777000)) else { continue }
                    if message.media.contains(where: { $0 is TelegramMediaAction }) { continue }
                    let expiring = message.attributes.contains(where: { $0 is AutoremoveTimeoutMessageAttribute || $0 is AutoclearTimeoutMessageAttribute })
                    guard NebulaRetentionPolicy.allowed(enabled: archive.enabled(account: account), secret: secret, expiring: expiring, saveSecret: archive.saveSecret(account: account), saveExpiring: archive.saveExpiring(account: account), protectedContent: message.flags.contains(.CopyProtected), incoming: message.flags.contains(.Incoming), service: message.media.contains(where: { $0 is TelegramMediaAction }), validId: id.id != 0) else { continue }
                    if transaction.getPeer(id.peerId)?.isCopyProtectionEnabled == true { continue }
                    if let cached = transaction.getPeerCachedData(peerId: id.peerId) as? CachedUserData, cached.flags.contains(.copyProtectionEnabled) { continue }
                    if retained.insert(id).inserted {
                        entries.append(NebulaDeletedEntry(peer: id.peerId.toInt64(), id: id.id, timestamp: message.timestamp, text: message.text, namespace: id.namespace))
                        changed.append(id)
                    }
                }
            }
            entries = NebulaDeletedArchive.pruned(entries)
            retained = Set(entries.map(messageId))
            try archive.replace(entries, account: account) // Persist marker before suppressing a deletion.
            for entry in all where !retained.contains(messageId(entry)) {
                let id = messageId(entry)
                if let message = transaction.getMessage(id), isRetained(message) { transaction.deleteMessages([id], forEachMedia: nil) }
            }
            for id in changed where retained.contains(id) {
                transaction.updateMessage(id, update: { current in
                    var forward: StoreMessageForwardInfo?
                    if let f = current.forwardInfo {
                        forward = StoreMessageForwardInfo(authorId: f.author?.id, sourceId: f.source?.id, sourceMessageId: f.sourceMessageId, date: f.date, authorSignature: f.authorSignature, psaType: f.psaType, flags: f.flags)
                    }
                    return .update(StoreMessage(id: current.id, customStableId: nil, globallyUniqueId: current.globallyUniqueId, groupingKey: current.groupingKey, threadId: current.threadId, timestamp: current.timestamp, flags: StoreMessageFlags(current.flags), tags: current.tags, globalTags: current.globalTags, localTags: current.localTags.union(tag), forwardInfo: forward, authorId: current.author?.id, text: current.text, attributes: current.attributes.filter { !($0 is AutoremoveTimeoutMessageAttribute) && !($0 is AutoclearTimeoutMessageAttribute) }, media: current.media))
                })
            }
            return ids.filter { !retained.contains($0) }
        } catch { return ids }
    }
}
