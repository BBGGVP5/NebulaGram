import UIKit
import Display
import TelegramCore
import Postbox
import AccountContext
import SwiftSignalKit
import ChatPresentationInterfaceState
import ChatInterfaceState
import Pasteboard
import NebulaSettingsContract
import ContextUI

extension ChatControllerImpl {
    func nebulaSelectionButtons(state: ChatPresentationInterfaceState) -> [UIBarButtonItem]? {
        guard let selection = state.interfaceState.selectionState, state.reportReason == nil, state.subject == nil else {
            self.nebulaSelectionDisposable.set(nil)
            self.nebulaSelectionIds = nil
            self.nebulaSelectionBarItems = []
            self.nebulaSelectionCanForward = false
            self.nebulaSelectionCanCopy = false
            self.nebulaSelectionCanDelete = false
            return nil
        }
        let ids = selection.selectedIds
        if self.nebulaSelectionIds != ids {
            self.nebulaSelectionIds = ids
            self.nebulaSelectionCanForward = false
            self.nebulaSelectionCanCopy = false
            self.nebulaSelectionCanDelete = false
            let ru = state.strings.baseLanguageCode.hasPrefix("ru")
            let definitions: [(String, String, Selector)] = [
                ("trash", state.strings.VoiceOver_MessageContextDelete, #selector(nebulaDeleteSelection)),
                ("arrowshape.turn.up.right", state.strings.VoiceOver_MessageContextForward, #selector(nebulaForwardSelected)),
                ("doc.on.doc", ru ? "Копировать" : "Copy", #selector(nebulaCopySelection)),
                ("checklist", ru ? "Выбрать загруженные сообщения (до 100)" : "Select loaded messages (up to 100)", #selector(nebulaSelectLoaded))
            ]
            self.nebulaSelectionBarItems = definitions.map { symbol, label, action in
                let image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 19, weight: .regular))
                    ?? UIImage(systemName: "list.bullet", withConfiguration: UIImage.SymbolConfiguration(pointSize: 19, weight: .regular))
                let item = UIBarButtonItem(image: image, style: .plain, target: self, action: action)
                item.accessibilityLabel = label
                item.isEnabled = false
                return item
            }
            self.nebulaSelectionBarItems[3].isEnabled = !ids.isEmpty && ids.count < 100
            self.nebulaSelectionDisposable.set((self.context.sharedContext.chatAvailableMessageActions(
                engine: self.context.engine, accountPeerId: self.context.account.peerId, messageIds: ids, keepUpdated: true
            ) |> deliverOnMainQueue).start(next: { [weak self] actions in
                guard let self, self.nebulaSelectionIds == ids,
                      self.presentationInterfaceState.interfaceState.selectionState?.selectedIds == ids,
                      self.nebulaSelectionBarItems.count == 4 else { return }
                let protected = actions.isCopyProtected || self.presentationInterfaceState.copyProtectionEnabled
                self.nebulaSelectionCanDelete = !ids.isEmpty && !actions.disableDelete
                    && !actions.options.intersection([.deleteLocally, .deleteGlobally]).isEmpty
                self.nebulaSelectionCanForward = !ids.isEmpty && !protected && actions.options.contains(.forward)
                self.nebulaSelectionCanCopy = !ids.isEmpty && !protected
                self.nebulaSelectionBarItems[0].isEnabled = self.nebulaSelectionCanDelete
                self.nebulaSelectionBarItems[1].isEnabled = self.nebulaSelectionCanForward
                self.nebulaSelectionBarItems[2].isEnabled = self.nebulaSelectionCanCopy
            }))
        }
        // Native title needs room on compact phones and at larger text sizes.
        if self.view.bounds.width < 375 || UIApplication.shared.preferredContentSizeCategory.isAccessibilityCategory {
            let more = UIBarButtonItem(image: UIImage(systemName: "ellipsis"), style: .plain, target: self, action: #selector(nebulaSelectionMore(_:)))
            more.accessibilityLabel = state.strings.baseLanguageCode.hasPrefix("ru") ? "Ещё" : "More"
            return [more, self.nebulaSelectionBarItems[2], self.nebulaSelectionBarItems[3]]
        }
        return self.nebulaSelectionBarItems
    }

    @objc func nebulaSelectLoaded() {
        guard let selected = self.presentationInterfaceState.interfaceState.selectionState?.selectedIds,
              self.presentationInterfaceState.reportReason == nil, self.presentationInterfaceState.subject == nil else { return }
        var loaded: [EngineMessage.Id] = []
        self.chatDisplayNode.historyNode.forEachMessageInCurrentHistoryView { message in
            if message.id.namespace == Namespaces.Message.Cloud, !message.flags.isSending,
               message.adAttribute == nil, !message.containsSecretMedia,
               !message.media.contains(where: { $0 is TelegramMediaAction }) {
                loaded.append(message.id)
            }
            return true
        }
        let added = NebulaSelectionPolicy.addingLoaded(loaded, to: selected).subtracting(selected)
        guard !added.isEmpty else { return }
        self.updateChatPresentationInterfaceState(animated: !UIAccessibility.isReduceMotionEnabled, interactive: true,
            { $0.updatedInterfaceState { $0.withToggledSelectedMessages(Array(added), value: true) } })
    }

    @objc func nebulaDeleteSelection() {
        guard self.nebulaSelectionCanDelete else { return }
        self.interfaceInteraction?.deleteSelectedMessages(nil)
    }
    @objc func nebulaForwardSelected() { self.nebulaForwardSelection(withoutAuthor: false) }

    func nebulaForwardSelection(withoutAuthor: Bool) {
        guard let ids = self.presentationInterfaceState.interfaceState.selectionState?.selectedIds, !ids.isEmpty,
              !self.presentationInterfaceState.copyProtectionEnabled,
              self.presentationInterfaceState.reportReason == nil, self.presentationInterfaceState.subject == nil else { return }
        if withoutAuthor {
            guard NebulaMessagePreferences.shared.enabled("selection_without_author"),
                  ids.allSatisfy({ $0.peerId.namespace != Namespaces.Peer.SecretChat }) else { return }
        }
        let _ = (self.context.sharedContext.chatAvailableMessageActions(engine: self.context.engine,
            accountPeerId: self.context.account.peerId, messageIds: ids, keepUpdated: false)
            |> take(1) |> deliverOnMainQueue).startStandalone(next: { [weak self] actions in
                guard let self, self.presentationInterfaceState.interfaceState.selectionState?.selectedIds == ids,
                      !self.presentationInterfaceState.copyProtectionEnabled, !actions.isCopyProtected,
                      actions.options.contains(.forward) else { return }
                self.commitPurposefulAction()
                self.forwardMessages(messageIds: Array(ids).sorted(), options: withoutAuthor
                    ? ChatInterfaceForwardOptionsState(hideNames: true, hideCaptions: false, unhideNamesOnCaptionChange: false) : nil)
            })
    }

    @objc func nebulaCopySelection() {
        guard self.nebulaSelectionCanCopy, !self.presentationInterfaceState.copyProtectionEnabled,
              let ids = self.presentationInterfaceState.interfaceState.selectionState?.selectedIds, !ids.isEmpty else { return }
        let _ = (self.context.engine.data.get(EngineDataMap(ids.map(TelegramEngine.EngineData.Item.Messages.Message.init)))
            |> deliverOnMainQueue).startStandalone(next: { [weak self] values in
                guard let self, self.presentationInterfaceState.interfaceState.selectionState?.selectedIds == ids,
                      !self.presentationInterfaceState.copyProtectionEnabled else { return }
                let messages = values.values.compactMap { $0?._asMessage() }.sorted { $0.id < $1.id }
                guard messages.count == ids.count,
                      messages.allSatisfy({ !$0.isCopyProtected() && !$0.containsSecretMedia && $0.adAttribute == nil }) else { return }
                var text = ""
                var entities: [MessageTextEntity] = []
                for message in messages where !message.text.isEmpty {
                    if !text.isEmpty { text += "\n\n" }
                    let offset = text.utf16.count
                    for attribute in message.attributes {
                        if let attribute = attribute as? TextEntitiesMessageAttribute {
                            entities += attribute.entities.map { MessageTextEntity(range: ($0.range.lowerBound + offset)..<($0.range.upperBound + offset), type: $0.type) }
                        }
                    }
                    text += message.text
                }
                if !text.isEmpty { storeMessageTextInPasteboard(text, entities: entities) }
            })
    }

    @objc func nebulaSelectionMore(_ sender: UIBarButtonItem) {
        let strings = self.presentationData.strings
        let sheet = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        let forward = UIAlertAction(title: strings.VoiceOver_MessageContextForward, style: .default) { [weak self] _ in self?.nebulaForwardSelected() }
        forward.isEnabled = self.nebulaSelectionCanForward; sheet.addAction(forward)
        let delete = UIAlertAction(title: strings.VoiceOver_MessageContextDelete, style: .destructive) { [weak self] _ in self?.nebulaDeleteSelection() }
        delete.isEnabled = self.nebulaSelectionCanDelete; sheet.addAction(delete)
        sheet.addAction(UIAlertAction(title: strings.Common_Cancel, style: .cancel))
        sheet.popoverPresentationController?.barButtonItem = sender
        self.present(sheet, animated: true)
    }

    func nebulaHistoryMenuItems() -> [ContextMenuItem] {
        guard self.presentationInterfaceState.subject == nil, let peerId = self.chatLocation.peerId,
              peerId.namespace != Namespaces.Peer.SecretChat else { return [] }
        let ru = self.presentationData.strings.baseLanguageCode.hasPrefix("ru")
        return [
            .action(ContextMenuActionItem(text: ru ? "В начало" : "Go to beginning", icon: { theme in
                generateTintedImage(image: UIImage(systemName: "arrow.up.to.line"), color: theme.contextMenu.primaryColor)
            }, action: { [weak self] _, finish in finish(.default); self?.scrollToStartOfHistory() })),
            .action(ContextMenuActionItem(text: ru ? "Очистить удалёнки" : "Clear deleted messages", icon: { theme in
                generateTintedImage(image: UIImage(systemName: "trash"), color: theme.contextMenu.primaryColor)
            }, action: { [weak self] _, finish in finish(.default); self?.nebulaConfirmClearRetained(peerId: peerId) }))
        ]
    }
}
