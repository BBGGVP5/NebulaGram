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
    var openAi: (() -> Void)?
    var updateKey: ((String, Bool) -> Void)?
    var clearHistory: (() -> Void)?
    var searchUpdated: ((String) -> Void)?
    var russian = false

    init(transfer: NebulaSettingsFileTransfer, update: @escaping (Bool) -> Void) {
        self.transfer = transfer
        self.update = update
    }
}

private enum NebulaSettingsEntry: ItemListNodeEntry {
    case search(String, String)
    case empty(String)
    case toolsHeader(String)
    case appearanceHeader(String)
    case privacyHeader(String)
    case navigation(String, String)
    case contacts(String, Bool, Bool)
    case glass(String, String)
    case link(String)
    case privacy(String)
    case ai(String)
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

    var section: ItemListSectionId {
        switch self {
        case .search, .empty: return -1
        case .toolsHeader, .link, .ai: return 0
        case .appearanceHeader, .glass, .navigation, .contacts, .stories: return 1
        case .header, .hideCounters, .footer: return 2
        case .privacyHeader, .privacy, .history, .clearHistory: return 3
        case .transferHeader, .importFile, .exportFile, .transferFooter: return 4
        }
    }
    private var order: Int {
        switch self {
        case .search: return -2
        case .empty: return -1
        case .toolsHeader: return 0
        case .link: return 1
        case .ai: return 2
        case .appearanceHeader: return 3
        case .glass: return 4
        case .navigation: return 5
        case .contacts: return 6
        case .stories: return 7
        case .header: return 8
        case .hideCounters: return 9
        case .footer: return 10
        case .privacyHeader: return 11
        case .privacy: return 12
        case .history: return 13
        case .clearHistory: return 14
        case .transferHeader: return 15
        case .importFile: return 16
        case .exportFile: return 17
        case .transferFooter: return 18
        }
    }
    var stableId: Int32 {
        switch self {
        case .search: return 19
        case .empty: return 20
        case .toolsHeader: return 16
        case .appearanceHeader: return 17
        case .privacyHeader: return 18
        case .navigation: return 13
        case .contacts: return 14
        case .glass: return 12
        case .link: return 7
        case .privacy: return 8
        case .ai: return 15
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
        return lhs.order < rhs.order
    }

    var searchableText: String? {
        switch self {
        case let .navigation(title, detail), let .glass(title, detail): return title + " " + detail
        case let .contacts(title, _, _), let .stories(title, _, _), let .history(title, _, _),
             let .hideCounters(title, _, _), let .exportFile(title, _): return title
        case let .link(title), let .privacy(title), let .ai(title), let .clearHistory(title),
             let .importFile(title): return title
        default: return nil
        }
    }

    var isHeader: Bool {
        switch self {
        case .toolsHeader, .appearanceHeader, .privacyHeader, .header, .transferHeader: return true
        default: return false
        }
    }

    func item(presentationData: ItemListPresentationData, arguments: Any) -> ListViewItem {
        let arguments = arguments as! NebulaSettingsArguments
        let ru = arguments.russian
        func disclosure(_ title: String, _ detail: String, _ symbol: String, _ action: (() -> Void)?) -> ListViewItem {
            return ItemListDisclosureItem(presentationData: presentationData, systemStyle: .glass,
                icon: NebulaSettingsStyle.icon(symbol: symbol), title: title,
                label: detail, labelStyle: .multilineDetailText, sectionId: section,
                style: .blocks, action: action)
        }
        switch self {
        case let .search(value, placeholder):
            return ItemListSingleLineInputItem(presentationData: presentationData, systemStyle: .glass,
                title: NSAttributedString(string: ""), text: value, placeholder: placeholder,
                type: .regular(capitalization: false, autocorrection: false), returnKeyType: .search,
                clearType: .always, maxLength: 200, sectionId: section,
                textUpdated: { arguments.searchUpdated?($0) }, action: {})
        case let .empty(text):
            return ItemListTextItem(presentationData: presentationData, text: .plain(text), sectionId: section)
        case let .header(text), let .transferHeader(text), let .toolsHeader(text), let .appearanceHeader(text), let .privacyHeader(text):
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
            return disclosure(title, ru ? "Локальные копии и защита" : "Local copies and protection", "hand.raised", { arguments.openPrivacy?() })
        case let .ai(title):
            return disclosure(title, ru ? "Провайдер, модель и инструкции" : "Provider, model and instructions", "sparkles", { arguments.openAi?() })
        case let .contacts(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?("bottom_bar_contacts", $0) })
        case let .navigation(title, detail):
            return disclosure(title, detail, "rectangle.bottomthird.inset.filled", { arguments.openNavigation?() })
        case let .glass(title, detail):
            return disclosure(title, detail, "slider.horizontal.3", { arguments.openGlass?() })
        case let .link(title):
            return disclosure(title, ru ? "Подписки, серверы и подключение" : "Subscriptions, servers and connection", "shield", { arguments.openLink?() })
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
    let searchQuery = ValuePromise("", ignoreRepeated: true)
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
    arguments.searchUpdated = { searchQuery.set($0) }
    let signal = combineLatest(queue: .mainQueue(), context.sharedContext.presentationData, settings, writeFailed.get(), searchQuery.get())
    |> deliverOnMainQueue
    |> map { presentationData, hideCounters, failed, query -> (ItemListControllerState, (ItemListNodeState, Any)) in
        let presentationData = presentationData.withUpdated(theme: presentationData.theme.withModalBlocksBackground())
        let ru = presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        arguments.russian = ru
        let modes = ru ? ["Автоматически", "Полное", "Облегчённое"] : ["Automatic", "Full", "Light"]
        let firstTab = store.bottomTabOrder.first(where: { $0 != "contacts" || store.showContactsTab }) ?? "chats"
        let tabName = firstTab == "contacts" ? (ru ? "Контакты" : "Contacts")
            : firstTab == "settings" ? (ru ? "Настройки" : "Settings") : (ru ? "Чаты" : "Chats")
        var footer = ru
            ? "Скрывает числа на вкладках папок. Непрочитанные сообщения и уведомления не изменяются."
            : "Hides numbers on folder tabs. Unread messages and notifications are unchanged."
        if failed || store.hasLoadError {
            footer += ru
                ? "\n\nНе удалось прочитать или сохранить настройки. Сохранённые данные не сброшены."
                : "\n\nCould not read or save preferences. Stored data has not been reset."
        }
        var entries: [NebulaSettingsEntry] = [
            .search(query, ru ? "Поиск настроек" : "Search settings"),
            .toolsHeader(ru ? "Подключение и инструменты" : "Connection and tools"),
            .appearanceHeader(ru ? "Интерфейс и навигация" : "Interface and navigation"),
            .privacyHeader(ru ? "Конфиденциальность и поиск" : "Privacy and search"),
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
            .glass(ru ? "Адаптивное стекло" : "Adaptive glass", modes[max(0, min(2, store.glassQuality))]),
            .navigation(ru ? "Порядок нижних вкладок" : "Bottom tab order", (ru ? "Сначала: " : "First: ") + tabName),
            .contacts(ru ? "Контакты на нижней панели" : "Contacts in bottom bar", store.showContactsTab, !store.hasLoadError),
            .ai(ru ? "Искусственный интеллект" : "AI assistant")
        ]
        if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let matches = entries.filter { entry in
                guard let title = entry.searchableText else { return false }
                return NebulaSettingsSearch.matches(query, in: title)
            }
            let sections = Set(matches.map { $0.section })
            entries = entries.filter { entry in
                if case .search = entry { return true }
                if case .footer = entry, failed || store.hasLoadError { return true }
                return matches.contains(where: { $0.stableId == entry.stableId })
                    || entry.isHeader && sections.contains(entry.section)
            }
            if matches.isEmpty { entries.append(.empty(ru ? "Ничего не найдено" : "No settings found")) }
        }
        entries.sort()
        let data = ItemListPresentationData(presentationData)
        let state = ItemListControllerState(presentationData: data, title: .text(ru ? "Настройки NebulaGram" : "NebulaGram Settings"), leftNavigationButton: nil, rightNavigationButton: nil, backNavigationButton: ItemListBackButton(title: presentationData.strings.Common_Back))
        return (state, (ItemListNodeState(presentationData: data, entries: entries, style: .blocks, animateChanges: false), arguments))
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
        // Тип задан явно и список вынесен из выражения: перечислять тернарник
        // прямо на месте компилятор отказывался — до разбора образца пары он
        // не успевал вывести элемент последовательности.
        let modes: [String] = ru ? ["Автоматически", "Полное", "Облегчённое"] : ["Automatic", "Full", "Light"]
        for (mode, title) in modes.enumerated() {
            alert.addAction(UIAlertAction(title: (store.glassQuality == mode ? "✓ " : "") + title, style: .default) { _ in
                do { try store.set(.integer(mode), for: "glass_quality"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            })
        }
        alert.addAction(UIAlertAction(title: ru ? "Отмена" : "Cancel", style: .cancel))
        controller.present(alert, animated: true)
    }
    arguments.openAi = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaAiController(russian: ru)), animated: true)
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
