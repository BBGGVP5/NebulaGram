import Foundation
import UIKit
import NebulaLinkUI
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
    var openLink: (() -> Void)?
    var openNavigation: (() -> Void)?
    var openGlass: (() -> Void)?
    var openPrivacy: (() -> Void)?
    var updateKey: ((String, Bool) -> Void)?
    var clearHistory: (() -> Void)?

    init(transfer: NebulaSettingsFileTransfer, update: @escaping (Bool) -> Void) {
        self.transfer = transfer
        self.update = update
    }
}

private enum NebulaSettingsEntry: ItemListNodeEntry {
    case navigation(String)
    case contacts(String, Bool, Bool)
    case glass(String)
    case link(String)
    case privacy(String)
    case stories(String, Bool, Bool)
    case history(String, Bool, Bool)
    case clearHistory(String)
    case header(String)
    case hideCounters(String, Bool, Bool)
    case footer(String)
    case transferHeader(String)
    case importFile(String)
    case exportFile(String, Bool)
    case transferFooter(String)

    var section: ItemListSectionId { return stableId < 3 ? 0 : (stableId < 7 ? 1 : 2) }
    var stableId: Int32 {
        switch self {
        case .navigation: return 13
        case .contacts: return 14
        case .glass: return 12
        case .link: return 7
        case .privacy: return 8
        case .stories: return 9
        case .history: return 10
        case .clearHistory: return 11
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
        case let .stories(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?("show_stories", $0) })
        case let .history(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?("settings_search_history", $0) })
        case let .clearHistory(title):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: .generic, alignment: .natural, sectionId: section, style: .blocks, action: { arguments.clearHistory?() })
        case let .privacy(title):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: .generic, alignment: .natural, sectionId: section, style: .blocks, action: { arguments.openPrivacy?() })
        case let .contacts(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?("bottom_bar_contacts", $0) })
        case let .navigation(title):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: .generic, alignment: .natural, sectionId: section, style: .blocks, action: { arguments.openNavigation?() })
        case let .glass(title):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: .generic, alignment: .natural, sectionId: section, style: .blocks, action: { arguments.openGlass?() })
        case let .link(title):
            return ItemListActionItem(presentationData: presentationData, systemStyle: .glass, title: title, kind: .generic, alignment: .natural, sectionId: section, style: .blocks, action: { arguments.openLink?() })
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
                ? "Формат NebulaGram JSON v1. Импорт заменяет настройки после подтверждения. На iOS из файла применяются счётчики папок, качество стекла, порядок вкладок и показ контактов. Локальные настройки историй и поиска сохраняются отдельно; остальные допустимые параметры ожидают переноса. Аккаунты и ключи доступа не экспортируются."
                : "NebulaGram JSON v1. Import replaces preferences after confirmation. Folder counters, glass quality, tab order and Contacts visibility are applied from files. Local story/search settings are preserved separately; other valid settings await porting. Accounts and access keys are not exported."),
            .link("NebulaLink"),
            .privacy(ru ? "Конфиденциальность" : "Privacy"),
            .stories(ru ? "Показывать истории" : "Show stories", store.showStories, !store.hasLoadError),
            .history(ru ? "Сохранять и показывать историю поиска настроек" : "Save and show settings search history", store.settingsSearchHistory, !store.hasLoadError),
            .clearHistory(ru ? "Очистить историю поиска настроек" : "Clear settings search history"),
            .glass(ru ? "Адаптивное стекло" : "Adaptive glass"),
            .navigation(ru ? "Порядок нижних вкладок" : "Bottom tab order"),
            .contacts(ru ? "Контакты на нижней панели" : "Contacts in bottom bar", store.showContactsTab, !store.hasLoadError)
        ]
        let data = ItemListPresentationData(presentationData)
        let state = ItemListControllerState(presentationData: data, title: .text(ru ? "Настройки NebulaGram" : "NebulaGram Settings"), leftNavigationButton: nil, rightNavigationButton: nil, backNavigationButton: ItemListBackButton(title: presentationData.strings.Common_Back))
        return (state, (ItemListNodeState(presentationData: data, entries: entries, style: .blocks, animateChanges: true), arguments))
    }
    let controller = ItemListController(context: context, state: signal)
    transfer.host = controller
    arguments.updateKey = { key, value in
        do { try store.set(.boolean(value), for: key);writeFailed.set(false) }
        catch { writeFailed.set(true) }
    }
    arguments.clearHistory = { [weak controller] in
        guard let controller = controller else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        let alert = UIAlertController(title: ru ? "Очистить историю поиска настроек?" : "Clear settings search history?", message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: ru ? "Отмена" : "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: ru ? "Очистить" : "Clear", style: .destructive) { _ in clearRecentSettingsSearchItems(engine: context.engine) })
        controller.present(alert, animated: true)
    }
    arguments.openLink = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        NebulaLinkService.shared.configure(accountManager: context.sharedContext.accountManager)
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaLinkController(russian: ru)), animated: true)
    }
    arguments.openNavigation = { [weak controller] in
        guard let controller = controller else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.hasPrefix("ru")
        let alert = UIAlertController(title: ru ? "Первой показывать" : "Show first", message: ru ? "Звонки остаются рядом с контактами. Отдельная вкладка профиля пока не перенесена." : "Calls remain next to Contacts. A separate Profile tab is not yet ported.", preferredStyle: .alert)
        for (key, title) in [("chats", ru ? "Чаты" : "Chats"), ("contacts", ru ? "Контакты" : "Contacts"), ("settings", ru ? "Настройки" : "Settings")] {
            alert.addAction(UIAlertAction(title: title, style: .default) { _ in
                var order = store.bottomTabOrder; order.removeAll { $0 == key }; order.insert(key, at: 0)
                do { try store.set(.string(order.joined(separator: ",")), for: "bottom_bar_order"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            })
        }
        alert.addAction(UIAlertAction(title: ru ? "Отмена" : "Cancel", style: .cancel)); controller.present(alert, animated: true)
    }
    arguments.openGlass = { [weak controller] in
        guard let controller = controller else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        let alert = UIAlertController(title: ru ? "Адаптивное стекло" : "Adaptive glass", message: ru ? "Авто облегчает эффекты при энергосбережении и нагреве. Системное уменьшение прозрачности учитывается во всех режимах." : "Auto reduces effects during Low Power Mode and thermal pressure. Reduce Transparency is respected in every mode.", preferredStyle: .alert)
        for (mode, title) in (ru ? ["Автоматически", "Полное", "Облегчённое"] : ["Automatic", "Full", "Light"]).enumerated() {
            alert.addAction(UIAlertAction(title: (store.glassQuality == mode ? "✓ " : "") + title, style: .default) { _ in
                do { try store.set(.integer(mode), for: "glass_quality"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            })
        }
        alert.addAction(UIAlertAction(title: ru ? "Отмена" : "Cancel", style: .cancel))
        controller.present(alert, animated: true)
    }
    arguments.openPrivacy = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        let privacy = NebulaPrivacyController(context: context, russian: ru)
        privacy.openAppLock = { [weak controller] in
            let push: (ViewController) -> Void = { [weak controller] next in
                (controller?.navigationController as? NavigationController)?.pushViewController(next)
            }
            let _ = passcodeOptionsAccessController(context: context, pushController: push, completion: { _ in
                push(passcodeOptionsController(context: context))
            }).start(next: { next in if let next = next { push(next) } })
        }
        controller.present(UINavigationController(rootViewController: privacy), animated: true)
    }
    return controller
}
