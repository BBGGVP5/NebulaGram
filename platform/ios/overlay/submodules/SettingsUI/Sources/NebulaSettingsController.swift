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
    var openIcons: (() -> Void)?
    var openBuildInfo: (() -> Void)?
    var openTransitions: (() -> Void)?
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
    case navigationToggle(String, String, Bool, Bool)
    case glass(String, String)
    case link(String)
    case privacy(String)
    case ai(String)
    case icons(String)
    case buildInfo(String)
    case transitions(String, String)
    case widePosts(String, Bool, Bool)
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
        case .toolsHeader, .link, .ai, .buildInfo: return 0
        case .appearanceHeader, .glass, .navigation, .contacts, .navigationToggle, .stories, .widePosts, .icons, .transitions: return 1
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
        case .link: return 10
        case .ai: return 20
        case .buildInfo: return 25
        case .appearanceHeader: return 30
        case .glass: return 40
        case .icons: return 45
        case .transitions: return 47
        case .navigation: return 50
        case .contacts: return 60
        case let .navigationToggle(key, _, _, _):
            switch key {
            case "bottom_bar_profile": return 61
            case "bottom_bar_settings": return 62
            case "tab_labels": return 63
            case "compact_bottom_bar": return 64
            case "hide_home_camera": return 65
            case "hide_home_compose": return 66
            case "hide_send_as": return 67
            case "hide_attach_camera": return 74
            case "centered_chat_header": return 68
            case "disable_next_channel": return 69
            default: return 72
            }
        case .widePosts: return 75
        case .stories: return 70
        case .header: return 80
        case .hideCounters: return 90
        case .footer: return 100
        case .privacyHeader: return 110
        case .privacy: return 120
        case .history: return 130
        case .clearHistory: return 140
        case .transferHeader: return 150
        case .importFile: return 160
        case .exportFile: return 170
        case .transferFooter: return 180
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
        case let .navigationToggle(key, _, _, _):
            switch key {
            case "bottom_bar_profile": return 30
            case "bottom_bar_settings": return 31
            case "tab_labels": return 32
            case "compact_bottom_bar": return 33
            case "hide_home_camera": return 34
            case "hide_home_compose": return 35
            case "hide_send_as": return 36
            case "hide_attach_camera": return 41
            case "centered_chat_header": return 37
            case "disable_next_channel": return 38
            default: return 39
            }
        case .glass: return 12
        case .icons: return 22
        case .transitions: return 24
        case .link: return 7
        case .privacy: return 8
        case .ai: return 15
        case .buildInfo: return 23
        case .widePosts: return 21
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
        case let .navigation(title, detail), let .glass(title, detail), let .transitions(title, detail): return title + " " + detail
        case let .widePosts(title, _, _), let .contacts(title, _, _), let .navigationToggle(_, title, _, _), let .stories(title, _, _), let .history(title, _, _),
             let .hideCounters(title, _, _), let .exportFile(title, _): return title
        case let .link(title), let .privacy(title), let .ai(title), let .icons(title), let .buildInfo(title), let .clearHistory(title),
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
            let magnifier = NSTextAttachment()
            magnifier.image = UIImage(systemName: "magnifyingglass", withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .medium))?
                .withTintColor(presentationData.theme.list.itemSecondaryTextColor, renderingMode: .alwaysOriginal)
            magnifier.bounds = CGRect(x: 0, y: -2, width: 18, height: 18)
            return ItemListSingleLineInputItem(presentationData: presentationData, systemStyle: .glass,
                title: NSAttributedString(attachment: magnifier), text: value, placeholder: placeholder,
                type: .regular(capitalization: false, autocorrection: false), returnKeyType: .search,
                spacing: 8, clearType: .always, maxLength: 200, sectionId: section,
                textUpdated: { arguments.searchUpdated?($0) }, action: {})
        case let .empty(text):
            return ItemListTextItem(presentationData: presentationData, text: .plain(text), sectionId: section)
        case let .header(text), let .transferHeader(text), let .toolsHeader(text), let .appearanceHeader(text), let .privacyHeader(text):
            return ItemListSectionHeaderItem(presentationData: presentationData, text: text, sectionId: section)
        case let .hideCounters(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: arguments.update)
        case let .footer(text), let .transferFooter(text):
            return ItemListTextItem(presentationData: presentationData, text: .markdown(text), sectionId: section)
        case let .widePosts(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?("wide_posts", $0) })
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
        case let .icons(title):
            return disclosure(title, ru ? "Выбрать иконку NebulaGram" : "Choose a NebulaGram icon", "app", { arguments.openIcons?() })
        case let .buildInfo(title):
            return disclosure(title, ru ? "Версия, основа и архитектура" : "Version, source and architecture", "info.circle", { arguments.openBuildInfo?() })
        case let .transitions(title, detail):
            return disclosure(title, detail, "square.on.square", { arguments.openTransitions?() })
        case let .contacts(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?("bottom_bar_contacts", $0) })
        case let .navigationToggle(key, title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: { arguments.updateKey?(key, $0) })
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
        // A custom Telegram theme must not recolor Nebula settings.
        let baseTheme = makeDefaultPresentationTheme(
            reference: UIScreen.main.traitCollection.userInterfaceStyle == .dark ? .night : .day,
            serviceBackgroundColor: nil)
        let accent = UIScreen.main.traitCollection.userInterfaceStyle == .dark
            ? UIColor(red: 168.0 / 255.0, green: 199.0 / 255.0, blue: 250.0 / 255.0, alpha: 1)
            : UIColor(red: 60.0 / 255.0, green: 141.0 / 255.0, blue: 240.0 / 255.0, alpha: 1)
        let settingsTheme = customizePresentationTheme(baseTheme, editing: true, accentColor: accent,
            outgoingAccentColor: nil, backgroundColors: [], bubbleColors: [], animateBubbleColors: nil)
            .withModalBlocksBackground()
        let presentationData = presentationData.withUpdated(theme: settingsTheme)
        let ru = presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        arguments.russian = ru
        let modes = ru ? ["Автоматически", "Полное", "Облегчённое"] : ["Automatic", "Full", "Light"]
        let transitionModes = ru ? ["Стандартная", "Системная", "Spring"] : ["Standard", "System", "Spring"]
        let firstTab = store.bottomTabOrder.first(where: {
            switch $0 {
            case "contacts": return store.showContactsTab
            case "settings": return store.showSettingsTab
            case "profile": return store.showProfileTab
            default: return true
            }
        }) ?? "chats"
        let tabName = firstTab == "contacts" ? (ru ? "Контакты" : "Contacts")
            : firstTab == "settings" ? (ru ? "Настройки" : "Settings")
            : firstTab == "profile" ? (ru ? "Профиль" : "Profile") : (ru ? "Чаты" : "Chats")
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
                ? "Формат NebulaGram JSON v1. Импорт заменяет настройки после подтверждения. Применяются подключённые параметры навигации, папок, стекла и широких постов. Остальные допустимые значения сохраняются до их переноса на iOS. Аккаунты и ключи доступа не экспортируются."
                : "NebulaGram JSON v1. Import replaces preferences after confirmation. Connected navigation, folder, glass and wide-post options are applied. Other valid values are retained until their iOS port. Accounts and access keys are not exported."),
            .link("NebulaLink"),
            .privacy(ru ? "Конфиденциальность" : "Privacy"),
            .widePosts(ru ? "Широкие посты в каналах" : "Wide posts in channels", store.widePosts, !store.hasLoadError),
            .stories(ru ? "Показывать истории" : "Show stories", store.showStories, !store.hasLoadError),
            .history(ru ? "Сохранять и показывать историю поиска настроек" : "Save and show settings search history", store.settingsSearchHistory, !store.hasLoadError),
            .clearHistory(ru ? "Очистить историю поиска настроек" : "Clear settings search history"),
            .glass(ru ? "Адаптивное стекло" : "Adaptive glass", modes[max(0, min(2, store.glassQuality))]),
            .icons(ru ? "Иконка приложения" : "App icon"),
            .transitions(ru ? "Анимация переходов" : "Transition animation", transitionModes[store.transitionStyle]),
            .navigation(ru ? "Порядок нижних вкладок" : "Bottom tab order", (ru ? "Сначала: " : "First: ") + tabName),
            .contacts(ru ? "Контакты на нижней панели" : "Contacts in bottom bar", store.showContactsTab, !store.hasLoadError),
            .navigationToggle("bottom_bar_profile", ru ? "Профиль на нижней панели" : "Profile in bottom bar", store.showProfileTab, !store.hasLoadError),
            .navigationToggle("bottom_bar_settings", ru ? "Настройки на нижней панели" : "Settings in bottom bar", store.showSettingsTab, !store.hasLoadError && store.showProfileTab),
            .navigationToggle("tab_labels", ru ? "Подписи вкладок" : "Tab labels", store.showTabLabels, !store.hasLoadError),
            .navigationToggle("compact_bottom_bar", ru ? "Компактная нижняя панель" : "Compact bottom bar", store.compactBottomBar, !store.hasLoadError),
            .navigationToggle("hide_home_camera", ru ? "Скрыть камеру на главной" : "Hide camera on home", store.hideHomeCamera, !store.hasLoadError),
            .navigationToggle("hide_home_compose", ru ? "Скрыть кнопку нового чата" : "Hide new-chat button", store.hideHomeCompose, !store.hasLoadError),
            .navigationToggle("hide_send_as", ru ? "Скрыть «Отправить от имени»" : "Hide Send As", store.hideSendAs, !store.hasLoadError),
            .navigationToggle("hide_attach_camera", ru ? "Скрыть камеру во вложениях" : "Hide camera in attachments", store.hideAttachCamera, !store.hasLoadError),
            .navigationToggle("centered_chat_header", ru ? "Заголовок чата по центру" : "Center chat title", store.centeredChatHeader, !store.hasLoadError),
            .navigationToggle("disable_next_channel", ru ? "Скрыть переход к следующему каналу" : "Hide next-channel prompt", store.disableNextChannel, !store.hasLoadError),
            .navigationToggle("seconds_in_time", ru ? "Секунды во времени сообщений" : "Show seconds in message times", store.secondsInTime, !store.hasLoadError),
            .ai(ru ? "Искусственный интеллект" : "AI assistant"),
            .buildInfo(ru ? "О сборке" : "Build information")
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
        let keys = ["chats", "contacts", "settings", "profile"]
        NebulaChoiceController.show(from: controller, title: ru ? "Первой показывать" : "Show first",
            choices: ru ? ["Чаты", "Контакты", "Настройки", "Профиль"] : ["Chats", "Contacts", "Settings", "Profile"],
            selected: keys.firstIndex(of: store.bottomTabOrder.first ?? "chats"),
            detail: ru ? "Звонки остаются рядом с контактами." : "Calls remain next to Contacts.", russian: ru) { index in
                var order = store.bottomTabOrder; order.removeAll { $0 == keys[index] }; order.insert(keys[index], at: 0)
                do { try store.set(.string(order.joined(separator: ",")), for: "bottom_bar_order"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            }
    }
    arguments.openGlass = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaGlassController(russian: ru)), animated: true)
    }
    arguments.openAi = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaAiController(russian: ru)), animated: true)
    }
    arguments.openIcons = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaIconController(russian: ru)), animated: true)
    }
    arguments.openBuildInfo = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaBuildInfoController(russian: ru)), animated: true)
    }
    arguments.openTransitions = { [weak controller] in
        guard let controller = controller else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        NebulaChoiceController.show(from: controller, title: ru ? "Анимация переходов" : "Transition animation",
            choices: ru ? ["Стандартная", "Системная", "Spring"] : ["Standard", "System", "Spring"], selected: store.transitionStyle,
            detail: ru ? "При включённом уменьшении движения iOS переходы остаются без анимации." : "Reduce Motion turns these animations off.", russian: ru) { style in
                do { try store.set(.integer(style), for: "fragment_transition_style"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            }
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
