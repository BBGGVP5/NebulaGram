import Foundation
import Display
import SwiftSignalKit
import TelegramPresentationData
import ItemListUI
import PresentationDataUtils
import AccountContext
import NebulaSettingsContract

private final class NebulaSettingsArguments {
    let update: (Bool) -> Void

    init(update: @escaping (Bool) -> Void) {
        self.update = update
    }
}

private enum NebulaSettingsEntry: ItemListNodeEntry {
    case header(String)
    case hideCounters(String, Bool, Bool)
    case footer(String)

    var section: ItemListSectionId { return 0 }
    var stableId: Int32 {
        switch self {
        case .header: return 0
        case .hideCounters: return 1
        case .footer: return 2
        }
    }

    static func < (lhs: NebulaSettingsEntry, rhs: NebulaSettingsEntry) -> Bool {
        return lhs.stableId < rhs.stableId
    }

    func item(presentationData: ItemListPresentationData, arguments: Any) -> ListViewItem {
        let arguments = arguments as! NebulaSettingsArguments
        switch self {
        case let .header(text):
            return ItemListSectionHeaderItem(presentationData: presentationData, text: text, sectionId: section)
        case let .hideCounters(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: arguments.update)
        case let .footer(text):
            return ItemListTextItem(presentationData: presentationData, text: .markdown(text), sectionId: section)
        }
    }
}

/// Deliberately expose only preferences with a native consumer, not planned ports.
public func nebulaSettingsController(context: AccountContext) -> ViewController {
    let store = NebulaSettingsStore.shared
    let writeFailed = ValuePromise(false, ignoreRepeated: true)
    let arguments = NebulaSettingsArguments(update: { value in
        do {
            try store.set(.boolean(value), for: "hide_tab_counters")
            writeFailed.set(false)
        } catch {
            writeFailed.set(true)
        }
    })
    let settings = Signal<Bool, NoError> { subscriber in
        let observation = store.observe {
            subscriber.putNext(store.hideTabCounters)
        }
        subscriber.putNext(store.hideTabCounters)
        return ActionDisposable { observation.cancel() }
    }
    let signal = combineLatest(queue: .mainQueue(), context.sharedContext.presentationData, settings, writeFailed.get())
    |> deliverOnMainQueue
    |> map { presentationData, hideCounters, failed -> (ItemListControllerState, (ItemListNodeState, Any)) in
        let presentationData = presentationData.withUpdated(theme: presentationData.theme.withModalBlocksBackground())
        let ru = presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        var footer = ru
            ? "Скрывает числа на вкладках папок. Непрочитанные сообщения и уведомления не изменяются.\n\nЭкспериментальный перенос NebulaGram на iOS. Остальные настройки появятся постепенно."
            : "Hides numbers on folder tabs. Unread messages and notifications are unchanged.\n\nExperimental NebulaGram port for iOS. More settings will be added gradually."
        if failed || store.hasLoadError {
            footer += ru
                ? "\n\nНе удалось прочитать или сохранить настройки. Сохранённые данные не сброшены."
                : "\n\nCould not read or save preferences. Stored data has not been reset."
        }
        let entries: [NebulaSettingsEntry] = [
            .header(ru ? "Папки чатов" : "Chat folders"),
            .hideCounters(ru ? "Скрыть счётчики папок" : "Hide folder counters", hideCounters, !store.hasLoadError),
            .footer(footer)
        ]
        let data = ItemListPresentationData(presentationData)
        let state = ItemListControllerState(presentationData: data, title: .text("NebulaGram"), leftNavigationButton: nil, rightNavigationButton: nil, backNavigationButton: ItemListBackButton(title: presentationData.strings.Common_Back))
        return (state, (ItemListNodeState(presentationData: data, entries: entries, style: .blocks, animateChanges: true), arguments))
    }
    return ItemListController(context: context, state: signal)
}
