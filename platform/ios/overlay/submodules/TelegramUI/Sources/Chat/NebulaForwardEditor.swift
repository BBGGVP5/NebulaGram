import Foundation
import UIKit
import Postbox
import TelegramCore
import SwiftSignalKit
import AccountContext
import ChatPresentationInterfaceState
import ChatInterfaceState
import TextFormat
import NebulaSettingsContract

/// Only ordinary messages and caption-bearing attachments. Never drop unknown media.
func nebulaCanEditForward(_ messages: [EngineRawMessage]) -> Bool {
    guard NebulaForwardEditing.shared.enabled, !messages.isEmpty else { return false }
    for message in messages {
        if message.isCopyProtected() || message.richText != nil || message.minAutoremoveOrClearTimeout != nil
            || message.id.peerId.namespace == Namespaces.Peer.SecretChat { return false }
        var hasAttachment = false
        var attachmentCount = 0
        for media in message.media {
            if media is TelegramMediaWebpage { continue }
            if media is TelegramMediaImage { hasAttachment = true; attachmentCount += 1; continue }
            if let file = media as? TelegramMediaFile, !file.isSticker, !file.isAnimatedSticker, !file.isVideoSticker, !file.isVoice, !file.isInstantVideo {
                hasAttachment = true; attachmentCount += 1; continue
            }
            return false
        }
        if attachmentCount > 1 { return false }
        if !hasAttachment && message.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
    }
    return true
}

extension ChatControllerImpl {
    func nebulaEditForward(_ messages: [EngineRawMessage]) {
        guard nebulaCanEditForward(messages), let peerId = chatLocation.peerId,
              peerId.namespace != Namespaces.Peer.SecretChat,
              canSendMessagesToChat(presentationInterfaceState) else { return }
        let ru = presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        let controller = NebulaForwardEditor(messages: messages, russian: ru, captionLimit: Int(context.userLimits.maxCaptionLength))
        controller.send = { [weak self, weak controller] texts in
            guard let self = self, let controller = controller,
                  self.chatLocation.peerId == peerId, nebulaCanEditForward(messages),
                  canSendMessagesToChat(self.presentationInterfaceState) else { return }
            // Use native posting restrictions and paid/scheduled-send UI, never enqueue from menu selection.
            func banned(_ flag: TelegramChatBannedRightsFlags) -> Bool {
                if let channel = self.presentationInterfaceState.renderedPeer?.peer as? TelegramChannel {
                    return channel.hasBannedPermission(flag) != nil
                }
                if let group = self.presentationInterfaceState.renderedPeer?.peer as? TelegramGroup { return group.hasBannedPermission(flag) }
                return false
            }
            for message in messages {
                let hasMedia = message.media.contains { $0 is TelegramMediaImage || $0 is TelegramMediaFile }
                if banned(hasMedia ? .banSendMedia : .banSendText) {
                    controller.report(ru ? "Нет разрешения на отправку в этот чат." : "Sending is not allowed in this chat."); return
                }
                for media in message.media {
                    if media is TelegramMediaImage && banned(.banSendPhotos) { controller.report(ru ? "В этом чате нельзя отправлять фото." : "Photos are not allowed in this chat."); return }
                    if let file = media as? TelegramMediaFile {
                        let flag: TelegramChatBannedRightsFlags = file.isAnimated ? .banSendGifs : file.isVideo ? .banSendVideos : file.isMusic ? .banSendMusic : .banSendFiles
                        if banned(flag) { controller.report(ru ? "В этом чате нельзя отправить это вложение." : "This attachment is not allowed in this chat."); return }
                    }
                }
            }
            var groups: [Int64: Int64] = [:]
            var outgoing: [EnqueueMessage] = []
            for (index, message) in messages.enumerated() {
                var attributes: [MessageAttribute] = []
                let entities = generateChatInputTextEntities(texts[index], generateLinks: false)
                if !entities.isEmpty { attributes.append(TextEntitiesMessageAttribute(entities: entities)) }
                for attribute in message.attributes {
                    if attribute is MediaSpoilerMessageAttribute || attribute is InvertMediaMessageAttribute { attributes.append(attribute) }
                }
                var reference: AnyMediaReference?
                for media in message.media where media is TelegramMediaImage || media is TelegramMediaFile {
                    reference = .message(message: MessageReference(message), media: media)
                }
                var groupingKey: Int64?
                if let group = message.groupingKey, reference != nil {
                    if groups[group] == nil { groups[group] = Int64.random(in: 1 ... Int64.max) }
                    groupingKey = groups[group]
                }
                outgoing.append(.message(text: texts[index].string, attributes: attributes, inlineStickers: [:],
                    mediaReference: reference, threadId: self.chatLocation.threadId, replyToMessageId: nil,
                    replyToStoryId: nil, localGroupingKey: groupingKey, correlationId: nil, bubbleUpEmojiOrStickersets: []))
            }
            self.presentPaidMessageAlertIfNeeded(count: Int32(outgoing.count), completion: { [weak self, weak controller] postpone in
                guard let self = self, let controller = controller, self.chatLocation.peerId == peerId else { return }
                let deliver: ([EnqueueMessage]) -> Void = { [weak self, weak controller] prepared in
                    guard let self = self, let controller = controller, !controller.didSubmit else { return }
                    controller.didSubmit = true
                    controller.navigationItem.rightBarButtonItem?.isEnabled = false
                    controller.dismiss(animated: true) {
                        self.sendMessages(prepared, media: messages.contains(where: { !$0.media.isEmpty }), postpone: postpone, commit: true)
                        // Do not erase an independently typed composer draft.
                        if Set(self.presentationInterfaceState.interfaceState.forwardMessageIds ?? []) == Set(messages.map { $0.id }) {
                            self.updateChatPresentationInterfaceState(animated: true, interactive: true, {
                                $0.updatedInterfaceState { $0.withUpdatedForwardMessageIds(nil).withUpdatedForwardOptionsState(nil) }
                            })
                        }
                    }
                }
                if case .scheduledMessages = self.presentationInterfaceState.subject {
                    self.presentScheduleTimePicker(style: .media, dismissByTapOutside: false, completion: { [weak self] result in
                        guard let self = self else { return }
                        deliver(self.transformEnqueueMessages(outgoing, silentPosting: result.silentPosting, scheduleTime: result.time, repeatPeriod: result.repeatPeriod, postpone: postpone))
                    })
                } else { deliver(outgoing) }
            })
        }
        var presenter = view.window?.rootViewController
        while let next = presenter?.presentedViewController { presenter = next }
        presenter?.present(UINavigationController(rootViewController: controller), animated: true)
    }
}

private final class NebulaForwardEditor: UITableViewController {
    private let messages: [EngineRawMessage]
    private let ru: Bool
    private let captionLimit: Int
    private var editors: [UITextView] = []
    var send: (([NSAttributedString]) -> Void)?
    var didSubmit = false
    init(messages: [EngineRawMessage], russian: Bool, captionLimit: Int) {
        self.messages = messages; self.ru = russian; self.captionLimit = captionLimit
        super.init(style: .insetGrouped)
        title = ru ? "Изменить и отправить" : "Edit and send"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .cancel, target: self, action: #selector(cancel))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: ru ? "Отправить" : "Send", style: .done, target: self, action: #selector(submit))
        tableView.rowHeight = UITableView.automaticDimension
        for message in messages {
            let editor = UITextView()
            editor.font = .preferredFont(forTextStyle: .body)
            editor.adjustsFontForContentSizeCategory = true
            editor.textColor = .label
            editor.backgroundColor = .clear
            var entities: [MessageTextEntity] = []
            for attribute in message.attributes { if let text = attribute as? TextEntitiesMessageAttribute { entities = text.entities } }
            let attributed = NSMutableAttributedString(attributedString: chatInputStateStringWithAppliedEntities(message.text, entities: entities))
            attributed.addAttributes([.font: UIFont.preferredFont(forTextStyle: .body), .foregroundColor: UIColor.label], range: NSRange(location: 0, length: attributed.length))
            editor.attributedText = attributed
            editor.typingAttributes[.font] = UIFont.preferredFont(forTextStyle: .body)
            editor.typingAttributes[.foregroundColor] = UIColor.label
            editors.append(editor)
        }
    }
    @objc private func cancel() { dismiss(animated: true) }
    @objc private func submit() {
        var texts: [NSAttributedString] = []
        for (index, editor) in editors.enumerated() {
            let hasMedia = messages[index].media.contains { $0 is TelegramMediaImage || $0 is TelegramMediaFile }
            let text = editor.attributedText ?? NSAttributedString(string: "")
            if !hasMedia && text.string.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                report(ru ? "Введите текст сообщения." : "Enter message text."); return
            }
            if text.length > (hasMedia ? captionLimit : 4096) {
                report(ru ? "Текст слишком длинный. Сократите его перед отправкой." : "Text is too long. Shorten it before sending."); return
            }
            texts.append(NSAttributedString(attributedString: text))
        }
        send?(texts)
    }
    func report(_ text: String) {
        let alert = UIAlertController(title: text, message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
    override func numberOfSections(in tableView: UITableView) -> Int { messages.count }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        let media = messages[section].media.contains { $0 is TelegramMediaImage || $0 is TelegramMediaFile }
        return "\(section + 1) · " + (media ? (ru ? "Вложение · подпись" : "Attachment · caption") : (ru ? "Текст" : "Text"))
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 0 ? (ru ? "Новая копия без автора. Вложения и альбомы сохраняются. Исходные сообщения не изменяются." : "New copies without authors. Attachments and albums are kept. Original messages are unchanged.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        let editor = editors[indexPath.section]
        editor.translatesAutoresizingMaskIntoConstraints = false
        cell.contentView.addSubview(editor)
        NSLayoutConstraint.activate([
            editor.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 12),
            editor.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -12),
            editor.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 8),
            editor.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -8),
            editor.heightAnchor.constraint(equalToConstant: 156)
        ])
        return cell
    }
}
