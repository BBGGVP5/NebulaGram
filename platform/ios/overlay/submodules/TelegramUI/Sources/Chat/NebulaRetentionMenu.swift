import Foundation
import TelegramCore
import SwiftSignalKit
import AlertUI
import PresentationDataUtils

extension ChatControllerImpl {
    func nebulaConfirmClearRetained(peerId: EnginePeer.Id) {
        let ru = self.presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        let alert = textAlertController(context: self.context,
            title: ru ? "Очистить сохранённые удалённые сообщения?" : "Clear retained messages?",
            text: ru ? "Локальные копии во всём этом чате. Обычная переписка и общий медиакэш не удаляются." : "Local retained copies in this chat only. Ordinary history and shared media cache are not deleted.",
            actions: [
                TextAlertAction(type: .genericAction, title: self.presentationData.strings.Common_Cancel, action: {}),
                TextAlertAction(type: .destructiveAction, title: ru ? "Очистить" : "Clear", action: { [weak self] in
                    guard let self = self else { return }
                    let _ = (NebulaDeletedMessages.clear(account: self.context.account, peer: peerId) |> deliverOnMainQueue).start(next: { [weak self] success in
                        guard let self = self, !success else { return }
                        self.present(textAlertController(context: self.context, title: nil,
                            text: ru ? "Не удалось очистить кэш." : "Could not clear cache.",
                            actions: [TextAlertAction(type: .defaultAction, title: "OK", action: {})]), in: .window(.root))
                    })
                })
            ])
        self.present(alert, in: .window(.root))
    }
}
