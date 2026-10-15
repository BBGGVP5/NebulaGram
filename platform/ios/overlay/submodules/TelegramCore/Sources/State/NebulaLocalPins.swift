import Foundation
import Postbox
import SwiftSignalKit

/// Projects local pins into list views; the remote pin/filter storage is unchanged.
public enum NebulaLocalPins {
    private static let store = NebulaLocalPinStore()
    private static let revisionLock = NSLock()
    private static var revision = 0
    private static let revisionValue = ValuePromise<Int>(0, ignoreRepeated: false)
    public static var changes: Signal<Int, NoError> { revisionValue.get() }
    private static func changed() { revisionLock.lock(); revision += 1; let value = revision; revisionLock.unlock(); revisionValue.set(value) }
    public static func scope(group: PeerGroupId, filterId: Int32?) -> String { if let filterId, filterId > 0 { return "filter:\(filterId)" }; return "root:\(group.rawValue)" }
    public static func scope(location: TogglePeerChatPinnedLocation) -> String {
        switch location { case let .group(group): return scope(group: group, filterId: nil); case let .filter(id): return scope(group: .root, filterId: id) }
    }
    public static func snapshot(account: Account) -> NebulaLocalPinStore.Snapshot { store.read(owner: account.peerId.toInt64()) }
    public static func pinned(account: Account, location: TogglePeerChatPinnedLocation, peer: PeerId) -> Bool { snapshot(account: account).orders[scope(location: location)]?.contains(peer.toInt64()) == true }
    @discardableResult public static func toggle(account: Account, location: TogglePeerChatPinnedLocation, peer: PeerId) -> Bool {
        guard peer.namespace != Namespaces.Peer.SecretChat else { return false }
        let success = store.pin(owner: account.peerId.toInt64(), scope: scope(location: location), peer: peer.toInt64(), pinned: !pinned(account: account, location: location, peer: peer))
        if success { changed() }; return success
    }
    public static func setEnabled(account: Account, value: Bool) { if store.setEnabled(owner: account.peerId.toInt64(), value: value) { changed() } }
    public static func clear(account: Account) { if store.clear(owner: account.peerId.toInt64()) { changed() } }
    /// A view dependency, not a server request. Normal peer/group membership is retained.
    public static func projectedPins(transaction: Transaction, accountPeerId: PeerId, group: PeerGroupId, filterId: Int32?, native: [PeerId], excluded: Set<PeerId>) -> [PeerId] {
        let state = store.read(owner: accountPeerId.toInt64()); guard state.enabled else { return native }
        let local = state.orders[scope(group: group, filterId: filterId)] ?? []
        let accepted = local.compactMap { value -> Int64? in
            let peer = PeerId(value)
            guard peer.namespace != Namespaces.Peer.SecretChat, !excluded.contains(peer), let inclusion = transaction.getPeerChatListIndex(peer) else { return nil }
            if filterId == nil || filterId == 0 { guard inclusion.0 == group else { return nil } }
            return value
        }
        return NebulaLocalPinStore.merge(remote: native.map { $0.toInt64() }, local: accepted).map(PeerId.init)
    }
    static func remoteReorder(transaction: Transaction, accountPeerId: PeerId, location: TogglePeerChatPinnedLocation, requested: [PinnedItemId]) -> [PinnedItemId] {
        let state = store.read(owner: accountPeerId.toInt64()); guard state.enabled else { return requested }
        let key = scope(location: location); let local = Set(state.orders[key] ?? [])
        guard !local.isEmpty else { return requested }
        func value(_ id: PinnedItemId) -> Int64 { switch id { case let .peer(peer): return peer.toInt64() } }
        let native = Set(_internal_getPinnedItemIds(transaction: transaction, location: location).map(value))
        let next = NebulaLocalPinStore.merge(remote: [], local: requested.map(value).filter { local.contains($0) })
        let nextSet = Set(next)
        let previous = state.orders[key] ?? []
        if Array(next + previous.filter { !nextSet.contains($0) }) != previous, store.reorder(owner: accountPeerId.toInt64(), scope: key, requested: next) { changed() }
        return requested.filter { !local.contains(value($0)) || native.contains(value($0)) }
    }
}
