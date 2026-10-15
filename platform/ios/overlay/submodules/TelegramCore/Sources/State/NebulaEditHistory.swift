import Foundation
import Postbox
import SwiftSignalKit

public struct NebulaEditVersion: Codable {
    public let text: String
    public let entities: [MessageTextEntity]
    public let timestamp: Int32
}

/// Account Postbox only. Collection 126 is reserved for Nebula, checked against upstream.
public enum NebulaEditHistory {
    public static let maximumVersions = 32
    public static let maximumMessages = 1000
    private static let collection: ItemCacheCollectionId = 126
    private struct Key: Codable, Equatable {
        let peer: Int64; let namespace: Int32; let id: Int32
        init(_ id: MessageId) { self.peer = id.peerId.toInt64(); self.namespace = id.namespace; self.id = id.id }
        var cacheId: ItemCacheEntryId {
            let key = ValueBoxKey(length: 16)
            key.setInt64(0, value: peer); key.setInt32(8, value: namespace); key.setInt32(12, value: id)
            return ItemCacheEntryId(collectionId: collection, key: key)
        }
    }
    private struct Versions: Codable { let items: [NebulaEditVersion] }
    private struct Index: Codable { let keys: [Key] }
    private static var indexId: ItemCacheEntryId { ItemCacheEntryId(collectionId: collection, key: ValueBoxKey("index")) }
    private static func setting(_ account: Int64) -> String { "nebula.editHistory.\(account)" }
    public static func enabled(account: Int64) -> Bool { account != 0 && UserDefaults.standard.bool(forKey: setting(account)) }
    public static func setEnabled(account: Int64, value: Bool) { UserDefaults.standard.set(value, forKey: setting(account)) }
    private static func entities(_ attributes: [MessageAttribute]) -> [MessageTextEntity] {
        (attributes.first { $0 is TextEntitiesMessageAttribute } as? TextEntitiesMessageAttribute)?.entities ?? []
    }
    private static func edited(_ attributes: [MessageAttribute]) -> Int32 {
        (attributes.first { $0 is EditedMessageAttribute } as? EditedMessageAttribute)?.date ?? 0
    }
    static func changed(previous: Message, incoming: StoreMessage) -> Bool {
        edited(incoming.attributes) > 0 && edited(incoming.attributes) >= edited(previous.attributes)
            && (previous.text != incoming.text || entities(previous.attributes) != entities(incoming.attributes))
    }
    static func eligible(_ message: Message) -> Bool {
        message.id.namespace == Namespaces.Message.Cloud && message.id.id > 0
            && message.id.peerId.namespace != Namespaces.Peer.SecretChat
            && message.id.peerId != PeerId(namespace: Namespaces.Peer.CloudUser, id: PeerId.Id._internalFromInt64Value(777000))
            && !message.flags.contains(.CopyProtected)
            && !message.media.contains { $0 is TelegramMediaAction }
            && !message.attributes.contains { $0 is AutoremoveTimeoutMessageAttribute || $0 is AutoclearTimeoutMessageAttribute }
            && message.text.utf16.count <= 50_000 && entities(message.attributes).count <= 1000
    }
    /// Same transaction as the native edit, before replacement. No dispatch or network.
    static func capture(transaction: Transaction, accountPeerId: PeerId, id: MessageId, incoming: StoreMessage) {
        guard enabled(account: accountPeerId.toInt64()), let previous = transaction.getMessage(id), eligible(previous),
              !incoming.flags.contains(.CopyProtected),
              !incoming.attributes.contains(where: { $0 is AutoremoveTimeoutMessageAttribute || $0 is AutoclearTimeoutMessageAttribute }),
              !incoming.media.contains(where: { $0 is TelegramMediaAction }), changed(previous: previous, incoming: incoming) else { return }
        if transaction.getPeer(id.peerId)?.isCopyProtectionEnabled == true { return }
        if let data = transaction.getPeerCachedData(peerId: id.peerId) as? CachedUserData, data.flags.contains(.copyProtectionEnabled) { return }
        let key = Key(id)
        var versions = transaction.retrieveItemCacheEntry(id: key.cacheId)?.get(Versions.self)?.items ?? []
        versions.append(NebulaEditVersion(text: previous.text, entities: entities(previous.attributes), timestamp: edited(previous.attributes) > 0 ? edited(previous.attributes) : previous.timestamp))
        versions = Array(versions.suffix(maximumVersions))
        guard let entry = CodableEntry(Versions(items: versions)) else { return }
        transaction.putItemCacheEntry(id: key.cacheId, entry: entry)
        var keys = transaction.retrieveItemCacheEntry(id: indexId)?.get(Index.self)?.keys ?? []
        keys.removeAll { $0 == key }; keys.append(key)
        while keys.count > maximumMessages { transaction.removeItemCacheEntry(id: keys.removeFirst().cacheId) }
        if let entry = CodableEntry(Index(keys: keys)) { transaction.putItemCacheEntry(id: indexId, entry: entry) }
    }
    public static func read(account: Account, id: MessageId) -> Signal<[NebulaEditVersion], NoError> {
        account.postbox.transaction { transaction in
            let values = transaction.retrieveItemCacheEntry(id: Key(id).cacheId)?.get(Versions.self)?.items ?? []
            return Array(values.suffix(maximumVersions).reversed())
        }
    }
    public static func clear(account: Account, id: MessageId? = nil) -> Signal<Bool, NoError> {
        account.postbox.transaction { transaction in
            if let id {
                let key = Key(id); transaction.removeItemCacheEntry(id: key.cacheId)
                let keys = transaction.retrieveItemCacheEntry(id: indexId)?.get(Index.self)?.keys.filter { $0 != key } ?? []
                if let entry = CodableEntry(Index(keys: keys)) { transaction.putItemCacheEntry(id: indexId, entry: entry) }
            } else { transaction.clearItemCacheCollection(collectionId: collection) }
            return true
        }
    }
}
