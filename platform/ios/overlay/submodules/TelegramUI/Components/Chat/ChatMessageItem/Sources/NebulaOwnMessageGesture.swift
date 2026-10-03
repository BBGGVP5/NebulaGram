import Foundation
import Postbox
import TelegramCore
import AccountContext
import ChatControllerInteraction
import ChatMessageItemCommon
import NebulaSettingsContract

extension ChatMessageItem {
    public func nebulaPerformOwnDoubleTap(_ message: EngineRawMessage) -> Bool {
        let item = self
        guard item.controllerInteraction.selectionState == nil, !item.sending, !item.unsent, !item.failed,
              message.id.namespace == Namespaces.Message.Cloud,
              message.id.peerId.namespace != Namespaces.Peer.SecretChat else { return false }
        if let subject = item.associatedData.subject {
            switch subject {
            case .messageOptions, .scheduledMessages, .customChatContents: return false
            default: break
            }
        }
        var isChannelPost = false
        if let channel = message.peers[message.id.peerId] as? TelegramChannel, case .broadcast = channel.info {
            isChannelPost = true
        }
        let mayReply: Bool
        if case .reply = item.controllerInteraction.canSetupReply(message) {
            mayReply = true
        } else {
            mayReply = false
        }
        let action = NebulaMessageGesturePolicy.doubleTap(
            preference: NebulaSettingsStore.shared.ownDoubleTap,
            owned: message.author?.id == item.context.account.peerId && !message.flags.contains(.Incoming),
            channelPost: isChannelPost,
            canEdit: item.controllerInteraction.canEditMessageRichText(message) && item.controllerInteraction.nebulaEditMessage != nil,
            canReply: mayReply,
            canCopy: !item.associatedData.isCopyProtectionEnabled && !message.isCopyProtected(),
            hasText: !message.text.isEmpty
        )
        switch action {
        case .reaction:
            return false
        case .edit:
            item.controllerInteraction.nebulaEditMessage?(message.id)
        case .reply:
            item.controllerInteraction.setupReply(message.id)
        case .copy:
            item.controllerInteraction.copyText(message.text)
        case .none:
            break
        }
        return true
    }

}
