import UIKit
import TelegramCore
import Postbox
import SwiftSignalKit
import AccountContext
import ChatControllerInteraction
import ChatPresentationInterfaceState
import ChatInterfaceState
import NebulaSettingsContract
import SettingsUI
import TextFormat
import Pasteboard

extension ChatControllerImpl {
    func nebulaAvailableSwipeActions(_ message: EngineRawMessage) -> [Int] {
        guard presentationInterfaceState.interfaceState.selectionState == nil,
              presentationInterfaceState.subject == nil, message.adAttribute == nil,
              message.flags.intersection([.Failed, .Sending, .Unsent]).isEmpty,
              !message.media.contains(where: { $0 is TelegramMediaExpiredContent }) else { return [] }
        let readable = !message.media.contains(where: { $0 is TelegramMediaAction }) && !presentationInterfaceState.copyProtectionEnabled && !message.isCopyProtected() && !message.containsSecretMedia && !message.text.isEmpty
        return NebulaSettingsStore.shared.swipeActions.filter { action in
            if action == .reply {
                if case .reply? = controllerInteraction?.canSetupReply(message) { return true }
                return false
            }
            return readable && (action == .copy || message.id.peerId.namespace != Namespaces.Peer.SecretChat)
        }.map(\.rawValue)
    }

    func nebulaPerformSwipeAction(_ id: EngineMessage.Id, raw: Int) {
        guard let action = NebulaSwipeAction(rawValue: raw) else { return }
        let _ = (context.engine.data.get(TelegramEngine.EngineData.Item.Messages.Message(id: id)) |> deliverOnMainQueue).start(next: { [weak self] value in
            guard let self, let value, self.nebulaAvailableSwipeActions(value._asMessage()).contains(raw),
                  self.isNodeLoaded, self.displayNode.view.window != nil else { return }
            if action == .reply { self.interfaceInteraction?.setupReplyMessage(id, nil, { _, f in f() }); return }
            let message = value._asMessage()
            let entities = (message.attributes.first(where: { $0 is TextEntitiesMessageAttribute }) as? TextEntitiesMessageAttribute)?.entities ?? []
            if action == .copy { storeMessageTextInPasteboard(message.text, entities: entities); return }
            guard self.navigationController?.presentedViewController == nil else { return }
            let source = chatInputStateStringWithAppliedEntities(message.text, entities: entities)
            let russian = self.presentationData.strings.baseLanguageCode.hasPrefix("ru")
            let controller: UIViewController
            if action == .translate {
                controller = NebulaAiEditorController(source: source, russian: russian, theme: self.presentationData.theme, account: String(self.context.account.peerId.toInt64()), peer: String(id.peerId.toInt64()))
            } else {
                controller = NebulaMessageToolsController(text: message.text, russian: russian, theme: self.presentationData.theme, accountId: String(self.context.account.peerId.toInt64()), peerId: String(id.peerId.toInt64()), attributedSource: source, context: self.context)
            }
            self.navigationController?.present(NebulaToolsNavigationController(root: controller), animated: true)
        })
    }
}
