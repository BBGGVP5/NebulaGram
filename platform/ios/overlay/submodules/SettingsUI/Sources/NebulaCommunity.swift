import UIKit
import SwiftSignalKit
import TelegramCore
import AccountContext
import TelegramPresentationData
import AvatarNode

struct NebulaCommunityState: Equatable {
    let peer: EnginePeer?
    let count: Int?
}

/// The engine's username cache and MediaBox own identity and artwork per account.
func nebulaCommunity(context: AccountContext) -> Signal<NebulaCommunityState, NoError> {
    let empty = NebulaCommunityState(peer: nil, count: nil)
    let resolved = context.engine.peers.resolvePeerByName(name: "nebulaguard_channel", referrer: nil)
    |> mapToSignal { result -> Signal<EnginePeer?, NoError> in
        guard case let .result(peer) = result else { return .complete() }
        guard let peer else { return .single(nil) }
        context.account.viewTracker.forceUpdateCachedPeerData(peerId: peer.id)
        return context.engine.data.subscribe(TelegramEngine.EngineData.Item.Peer.Peer(id: peer.id))
    }
    |> map { peer -> EnginePeer.Id? in
        guard let peer else { return nil }
        if case let .channel(channel) = peer { return channel.linkedCommunityId }
        if case .community = peer { return peer.id }
        return nil
    }
    |> distinctUntilChanged
    |> mapToSignal { id -> Signal<NebulaCommunityState, NoError> in
        guard let id else { return .single(empty) }
        context.account.viewTracker.forceUpdateCachedPeerData(peerId: id)
        return combineLatest(context.engine.data.subscribe(TelegramEngine.EngineData.Item.Peer.Peer(id: id)),
            context.engine.data.subscribe(TelegramEngine.EngineData.Item.Peer.CachedData(id: id)))
        |> map { peer, cached in
            NebulaCommunityState(peer: peer, count: (cached as? CachedCommunityData)?.linkedPeers.count)
        }
    }
    return (.single(empty) |> then(resolved)) |> distinctUntilChanged |> deliverOnMainQueue
}

final class NebulaCommunityCell: UITableViewCell {
    private let avatar = AvatarNode(font: avatarPlaceholderFont(size: 18))
    private var peer: EnginePeer?
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: .subtitle, reuseIdentifier: reuseIdentifier)
        contentView.addSubview(avatar.view)
        accessoryType = .disclosureIndicator
        textLabel?.numberOfLines = 0
        detailTextLabel?.numberOfLines = 0
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(context: AccountContext, theme: PresentationTheme, state: NebulaCommunityState, russian: Bool) {
        if peer != state.peer {
            peer = state.peer
            avatar.setPeer(context: context, theme: theme, peer: state.peer, clipStyle: .roundedRect, displayDimensions: CGSize(width: 44, height: 44))
        }
        if case let .community(value)? = state.peer { textLabel?.text = value.title } else { textLabel?.text = "NebulaHub" }
        detailTextLabel?.text = state.count.map { context.sharedContext.currentPresentationData.with { $0 }.strings.PeerInfo_Community(Int32(clamping: $0)) }
            ?? (russian ? "Загрузка сообщества…" : "Loading community…")
        NebulaSettingsStyle.finish(self, theme: theme)
        setNeedsLayout()
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        avatar.frame = CGRect(x: 16, y: (bounds.height - 44) / 2, width: 44, height: 44)
        for label in [textLabel, detailTextLabel] {
            guard let label else { continue }
            label.frame = CGRect(x: 74, y: label.frame.minY, width: max(1, contentView.bounds.width - 90), height: label.frame.height)
        }
    }
}
