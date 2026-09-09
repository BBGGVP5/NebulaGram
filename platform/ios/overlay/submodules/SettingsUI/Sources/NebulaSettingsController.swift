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
    let transfer: NebulaSettingsFileTransfer

    init(transfer: NebulaSettingsFileTransfer, update: @escaping (Bool) -> Void) {
        self.transfer = transfer
        self.update = update
    }
}

private enum NebulaSettingsEntry: ItemListNodeEntry {
    case header(String)
    case hideCounters(String, Bool, Bool)
    case footer(String)
    case transferHeader(String)
    case importFile(String)
    case exportFile(String, Bool)
    case transferFooter(String)

    var section: ItemListSectionId { return stableId < 3 ? 0 : 1 }
    var stableId: Int32 {
        switch self {
        case .header: return 0
        case .hideCounters: return 1
        case .footer: return 2
        case .transferHeader: return 3
        case .importFile: return 4
        case .exportFile: return 5
        case .transferFooter: return 6
        }
    }

    static func < (lhs: NebulaSettingsEntry, rhs: NebulaSettingsEntry) -> Bool {
        return lhs.stableId < rhs.stableId
    }

    func item(presentationData: ItemListPresentationData, arguments: Any) -> ListViewItem {
        let arguments = arguments as! NebulaSettingsArguments
        switch self {
        case let .header(text), let .transferHeader(text):
            return ItemListSectionHeaderItem(presentationData: presentationData, text: text, sectionId: section)
        case let .hideCounters(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: arguments.update)
        case let .footer(text), let .transferFooter(text):
            return ItemListTextItem(presentationData: presentationData, text: .markdown(text), sectionId: section)
        case let .importFile(title):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: .generic, alignment: .natural, sectionId: section, style: .blocks, action: { arguments.transfer.importFile() })
        case let .exportFile(title, enabled):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: enabled ? .generic : .disabled, alignment: .natural, sectionId: section, style: .blocks, action: { if enabled { arguments.transfer.exportFile() } })
        }
    }
}

/// Deliberately expose only preferences with a native consumer, not planned ports.
public func nebulaSettingsController(context: AccountContext) -> ViewController {
    let store = NebulaSettingsStore.shared
    let writeFailed = ValuePromise(false, ignoreRepeated: true)
    let transfer = NebulaSettingsFileTransfer(store: store, isRussian: {
        context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
    }, didImport: { writeFailed.set(false) })
    let arguments = NebulaSettingsArguments(transfer: transfer, update: { value in
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
            .footer(footer),
            .transferHeader(ru ? "Перенос настроек" : "Transfer settings"),
            .importFile(ru ? "Импорт из файла" : "Import from file"),
            .exportFile(ru ? "Экспорт в файл" : "Export to file", !store.hasLoadError),
            .transferFooter(ru
                ? "Формат NebulaGram JSON v1. Импорт заменяет настройки после подтверждения. Пока на iOS применяется только скрытие счётчиков папок; остальные допустимые параметры сохраняются для будущего переноса. Аккаунты и ключи доступа не экспортируются."
                : "NebulaGram JSON v1. Import replaces preferences after confirmation. Only folder counter hiding is currently applied on iOS; other valid settings are retained for future ports. Accounts and access keys are not exported.")
        ]
        let data = ItemListPresentationData(presentationData)
        let state = ItemListControllerState(presentationData: data, title: .text("NebulaGram"), leftNavigationButton: nil, rightNavigationButton: nil, backNavigationButton: ItemListBackButton(title: presentationData.strings.Common_Back))
        return (state, (ItemListNodeState(presentationData: data, entries: entries, style: .blocks, animateChanges: true), arguments))
    }
    let controller = ItemListController(context: context, state: signal)
    transfer.host = controller
    return controller
}
