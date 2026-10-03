import Foundation
import UIKit
import NebulaLinkUI
import Display
import SwiftSignalKit
import TelegramPresentationData
import ItemListUI
import PresentationDataUtils
import AccountContext
import TelegramCore
import ItemListPeerItem
import NebulaSettingsContract

private final class NebulaSettingsArguments {
    let context: AccountContext
    let update: (Bool) -> Void
    let transfer: NebulaSettingsFileTransfer
    var openLink: (() -> Void)?
    var openNavigation: (() -> Void)?
    var openGlass: (() -> Void)?
    var openPrivacy: (() -> Void)?
    var openAi: (() -> Void)?
    var openSupport: (() -> Void)?
    var openCommunity: (() -> Void)?
    var openIcons: (() -> Void)?
    var openBuildInfo: (() -> Void)?
    var openMemory: (() -> Void)?
    var openTransitions: (() -> Void)?
    var openFolderStyle: (() -> Void)?
    var openCategory: ((Int) -> Void)?
    var updateKey: ((String, Bool) -> Void)?
    var clearHistory: (() -> Void)?
    var searchUpdated: ((String) -> Void)?
    var russian = false

    init(context: AccountContext, transfer: NebulaSettingsFileTransfer, update: @escaping (Bool) -> Void) {
        self.context = context
        self.transfer = transfer
        self.update = update
    }
}

private enum NebulaSettingsEntry: ItemListNodeEntry {
    case category(Int, String, String, String)
    case search(String, String)
    case empty(String)
    case toolsHeader(String)
    case appearanceHeader(String)
    case chatHeader(Int, String)
    case privacyHeader(String)
    case navigation(String, String)
    case contacts(String, Bool, Bool)
    case navigationToggle(String, String, Bool, Bool)
    case glass(String, String)
    case link(String)
    case privacy(String)
    case ai(String)
    case support(String)
    case community(String, EnginePeer?, Int?)
    case icons(String)
    case buildInfo(String)
    case memory(String)
    case transitions(String, String)
    case widePosts(String, Bool, Bool)
    case stories(String, Bool, Bool)
    case history(String, Bool, Bool)
    case clearHistory(String)
    case header(String)
    case hideCounters(String, Bool, Bool)
    case folderStyle(String, String)
    case footer(String)
    case transferHeader(String)
    case importFile(String)
    case exportFile(String, Bool)
    case transferFooter(String)

    var section: ItemListSectionId {
        switch self {
        case .search, .empty: return -1
        case let .category(index, _, _, _): return index == 10 || index == 12 || index == 16 ? 9 : index == 11 ? 10 : index >= 13 ? 1 : 0
        case .toolsHeader, .link, .ai, .buildInfo, .memory, .support, .community: return 0
        case let .chatHeader(section, _): return Int32(section)
        case .widePosts: return 9
        case .stories: return 10
        case .appearanceHeader, .glass, .navigation, .contacts, .icons, .transitions: return 1
        case let .navigationToggle(key, _, _, _):
            if key.hasPrefix("profile_") { return 5 }
            if ["folder_title", "folder_outline", "hide_all_chats", "center_home"].contains(key) { return 2 }
            if ["hide_dividers", "hide_search_field"].contains(key) { return 6 }
            if key.hasPrefix("menu_") || ["centered_chat_header", "adaptive_chat_header", "floating_chat_header_v2", "header_unread", "message_menu_blur"].contains(key) { return 7 }
            if ["hide_send_as", "hide_attach_camera"].contains(key) { return 8 }
            if key.hasPrefix("reply_") || ["seconds_in_time", "disable_next_channel"].contains(key) { return 9 }
            return 1
        case .header, .hideCounters, .folderStyle, .footer: return 2
        case .privacyHeader, .privacy, .history, .clearHistory: return 3
        case .transferHeader, .importFile, .exportFile, .transferFooter: return 4
        }
    }
    private var order: Int {
        switch self {
        case let .category(index, _, _, _): return index == 7 ? 25 : index == 8 ? 35 : index * 10
        case .search: return -2
        case .empty: return -1
        case .toolsHeader: return 0
        case .chatHeader: return -1
        case .link: return 1
        case .ai: return 20
        case .support: return 5
        case .community: return 6
        case .buildInfo: return 25
        case .memory: return 24
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
            case "hide_dividers": return 78
            case "menu_search": return 73
            case "menu_mute": return 75
            case "menu_call": return 76
            case "menu_video": return 77
            case "centered_chat_header": return 68
            case "disable_next_channel": return 69
            case "folder_title": return 86
            case "folder_outline": return 87
            case "hide_search_field": return 72
            default: return 72
            }
        case .widePosts: return 80
        case .stories: return 70
        case .header: return 81
        case .folderStyle: return 85
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
        case let .category(index, _, _, _): return Int32(200 + index)
        case .search: return 19
        case .empty: return 20
        case .toolsHeader: return 16
        case let .chatHeader(section, _): return Int32(300 + section)
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
            case "hide_dividers": return 46
            case "menu_search": return 42
            case "menu_mute": return 43
            case "menu_call": return 44
            case "menu_video": return 45
            case "centered_chat_header": return 37
            case "disable_next_channel": return 38
            case "folder_title": return 40
            case "folder_outline": return 47
            case "hide_search_field": return 48
            case "hide_premium_status": return 55
            case "bottom_bar": return 70
            case "hide_all_chats": return 71
            case "center_home": return 72
            case "adaptive_chat_header": return 73
            case "floating_chat_header_v2": return 74
            case "header_unread": return 75
            case "message_menu_blur": return 76
            case "login_style": return 77
            case "profile_style": return 69
            case "profile_channel": return 60
            case "profile_birthday": return 61
            case "profile_business": return 62
            case "profile_background": return 63
            case "profile_emoji": return 64
            case "profile_photo_banner": return 65
            case "reply_background": return 66
            case "reply_colors": return 67
            case "reply_emoji": return 68
            default: return 39
            }
        case .glass: return 12
        case .icons: return 22
        case .transitions: return 24
        case .link: return 7
        case .privacy: return 8
        case .ai: return 15
        case .support: return 53
        case .community: return 54
        case .buildInfo: return 23
        case .memory: return 52
        case .widePosts: return 21
        case .stories: return 9
        case .history: return 10
        case .clearHistory: return 11
        case .header: return 0
        case .hideCounters: return 1
        case .folderStyle: return 49
        case .footer: return 2
        case .transferHeader: return 3
        case .importFile: return 4
        case .exportFile: return 5
        case .transferFooter: return 6
        }
    }

    static func < (lhs: NebulaSettingsEntry, rhs: NebulaSettingsEntry) -> Bool {
        if lhs.section != rhs.section { return lhs.section < rhs.section }
        return lhs.order == rhs.order ? lhs.stableId < rhs.stableId : lhs.order < rhs.order
    }

    var searchableText: String? {
        switch self {
        case let .category(_, title, detail, _): return title + " " + detail
        case let .navigation(title, detail), let .glass(title, detail), let .transitions(title, detail), let .folderStyle(title, detail): return title + " " + detail
        case let .widePosts(title, _, _), let .contacts(title, _, _), let .navigationToggle(_, title, _, _), let .stories(title, _, _), let .history(title, _, _),
             let .hideCounters(title, _, _), let .exportFile(title, _): return title
        case let .link(title), let .privacy(title), let .ai(title), let .support(title), let .community(title, _, _), let .icons(title), let .buildInfo(title), let .memory(title), let .clearHistory(title),
             let .importFile(title): return title
        default: return nil
        }
    }

    var isHeader: Bool {
        switch self {
        case .toolsHeader, .appearanceHeader, .chatHeader, .privacyHeader, .header, .transferHeader: return true
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
        case let .community(title, peer, count):
            if let peer {
                let data = arguments.context.sharedContext.currentPresentationData.with { $0 }
                return ItemListPeerItem(presentationData: presentationData, systemStyle: .glass,
                    dateTimeFormat: data.dateTimeFormat, nameDisplayOrder: data.nameDisplayOrder, context: arguments.context,
                    peer: peer, presence: nil, text: .text(count.map { data.strings.PeerInfo_Community(Int32(clamping: $0)) } ?? "", .secondary),
                    label: .none, editing: ItemListPeerItemEditing(editable: false, editing: false, revealed: false),
                    switchValue: nil, enabled: true, selectable: true, sectionId: section, action: { arguments.openCommunity?() },
                    setPeerIdWithRevealedOptions: { _, _ in }, removePeer: { _ in }, hasTopStripe: false, hasTopGroupInset: false, noInsets: false)
            }
            return disclosure(title, ru ? "Нажмите, чтобы загрузить" : "Tap to load", "bubble.left", { arguments.openCommunity?() })
        case let .support(title):
            return disclosure(title, ru ? "Разработка и значок за поддержку" : "Development and a supporter badge", "heart", { arguments.openSupport?() })
        case let .category(index, title, detail, symbol):
            return disclosure(title, detail, symbol, { arguments.openCategory?(index) })
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
        case let .chatHeader(_, text), let .header(text), let .transferHeader(text), let .toolsHeader(text), let .appearanceHeader(text), let .privacyHeader(text):
            return ItemListSectionHeaderItem(presentationData: presentationData, text: text, sectionId: section)
        case let .hideCounters(title, value, enabled):
            return ItemListSwitchItem(presentationData: presentationData, systemStyle: .glass, title: title, value: value, enabled: enabled, sectionId: section, style: .blocks, updated: arguments.update)
        case let .folderStyle(title, detail):
            return disclosure(title, detail, "folder", { arguments.openFolderStyle?() })
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
        case let .memory(title):
            return disclosure(title, ru ? "Состояние памяти приложения" : "App memory status", "memorychip", { arguments.openMemory?() })
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
public func nebulaSettingsController(context: AccountContext, page: Int = 0) -> ViewController {
    let store = NebulaSettingsStore.shared
    let writeFailed = ValuePromise(false, ignoreRepeated: true)
    let searchQuery = ValuePromise("", ignoreRepeated: true)
    let transfer = NebulaSettingsFileTransfer(store: store, isRussian: {
        context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
    }, didImport: { writeFailed.set(false) })
    let arguments = NebulaSettingsArguments(context: context, transfer: transfer, update: { value in
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
    let communityReload = ValuePromise(0, ignoreRepeated: false)
    var communityPeer: EnginePeer?
    let signal = combineLatest(queue: .mainQueue(), context.sharedContext.presentationData, settings, writeFailed.get(), searchQuery.get(), communityReload.get() |> mapToSignal { _ -> Signal<NebulaCommunityState, NoError> in
        return page == 0 || page == 1 ? nebulaCommunity(context: context) : .single(NebulaCommunityState(peer: nil, count: nil))
    })
    |> deliverOnMainQueue
    |> map { presentationData, hideCounters, failed, query, community -> (ItemListControllerState, (ItemListNodeState, Any)) in
        // Follow the active Telegram theme, including custom backgrounds and accents.
        let ru = presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        arguments.russian = ru
        communityPeer = community.peer
        let modes = ru ? ["Автоматически", "Полное", "Облегчённое"] : ["Automatic", "Full", "Light"]
        let transitionModes = ru ? ["Стандартная", "Системная", "Spring"] : ["Standard", "System", "Spring"]
        let folderModes = ru ? ["Названия", "Только значки", "Значки и названия"] : ["Titles", "Icons only", "Icons and titles"]
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
            ? "Скрывает числа на вкладках. Уведомления останутся."
            : "Hides numbers on tabs. Notifications remain active."
        if failed || store.hasLoadError {
            footer += ru
                ? "\n\nНе удалось прочитать или сохранить настройки. Сохранённые данные не сброшены."
                : "\n\nCould not read or save preferences. Stored data has not been reset."
        }
        var entries: [NebulaSettingsEntry] = [
            .search(query, ru ? "Поиск настроек" : "Search settings"),
            .toolsHeader(ru ? "Основное" : "Essentials"),
            .appearanceHeader(ru ? "Интерфейс" : "Interface"),
            .privacyHeader(ru ? "Конфиденциальность" : "Privacy"),
            .header(ru ? "Папки чатов" : "Chat folders"),
            .folderStyle(ru ? "Стиль папок" : "Folder style", folderModes[max(0, min(2, store.folderStyle))]),
            .hideCounters(ru ? "Скрыть счётчики папок" : "Hide folder counters", hideCounters, !store.hasLoadError),
            .footer(footer),
            .transferHeader(ru ? "Перенос настроек" : "Transfer settings"),
            .importFile(ru ? "Импорт из файла" : "Import from file"),
            .exportFile(ru ? "Экспорт в файл" : "Export to file", !store.hasLoadError),
            .transferFooter(ru
                ? "JSON NebulaGram · применимые параметры включатся сразу. Аккаунты и ключи не экспортируются."
                : "NebulaGram JSON · supported settings apply immediately. Accounts and keys are not exported."),
            .link("NebulaLink"),
            .support(ru ? "Поддержать проект" : "Support the project"),
            .community("NebulaHub", community.peer, community.count),
            .privacy(ru ? "Конфиденциальность" : "Privacy"),
            .widePosts(ru ? "Широкие посты в каналах" : "Wide posts in channels", store.widePosts, !store.hasLoadError),
            .stories(ru ? "Показывать истории" : "Show stories", store.showStories, !store.hasLoadError),
            .history(ru ? "История поиска настроек" : "Settings search history", store.settingsSearchHistory, !store.hasLoadError),
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
            .navigationToggle("hide_dividers", ru ? "Скрыть разделители чатов" : "Hide chat dividers", store.hideDividers, !store.hasLoadError),
            .navigationToggle("menu_search", ru ? "Поиск в меню чата" : "Search in chat menu", store.menuSearch, !store.hasLoadError),
            .navigationToggle("menu_mute", ru ? "Уведомления в меню темы" : "Mute in topic menu", store.menuMute, !store.hasLoadError),
            .navigationToggle("menu_call", ru ? "Звонок в меню чата" : "Voice call in chat menu", store.menuCall, !store.hasLoadError),
            .navigationToggle("menu_video", ru ? "Видеозвонок в меню чата" : "Video call in chat menu", store.menuVideo, !store.hasLoadError),
            .navigationToggle("folder_title", ru ? "Имя выбранной папки в шапке" : "Selected folder in header", store.folderTitle, !store.hasLoadError),
            .navigationToggle("folder_outline", ru ? "Контур выбранной папки" : "Selected folder outline", store.folderOutline, !store.hasLoadError),
            .navigationToggle("hide_search_field", ru ? "Скрыть строку поиска в чатах" : "Hide chat-list search field", store.hideSearchField, !store.hasLoadError),
            .navigationToggle("hide_premium_status", ru ? "Скрыть Premium-статусы" : "Hide Premium status", store.hidePremiumStatus, !store.hasLoadError),
            .navigationToggle("centered_chat_header", ru ? "Заголовок чата по центру" : "Center chat title", store.centeredChatHeader, !store.hasLoadError),
            .navigationToggle("disable_next_channel", ru ? "Скрыть переход к следующему каналу" : "Hide next-channel prompt", store.disableNextChannel, !store.hasLoadError),
            .navigationToggle("seconds_in_time", ru ? "Секунды во времени сообщений" : "Show seconds in message times", store.secondsInTime, !store.hasLoadError),
            .navigationToggle("bottom_bar", ru ? "Нижняя панель" : "Bottom bar", store.showBottomBar, !store.hasLoadError),
            .navigationToggle("hide_all_chats", ru ? "Скрыть «Все чаты»" : "Hide All Chats", store.hideAllChats, !store.hasLoadError),
            .navigationToggle("center_home", ru ? "Заголовок списка по центру" : "Center chat-list title", store.centerHome, !store.hasLoadError),
            .navigationToggle("adaptive_chat_header", ru ? "Ширина шапки по заголовку" : "Fit header to title", store.adaptiveChatHeader, !store.hasLoadError),
            .navigationToggle("floating_chat_header_v2", ru ? "Стеклянная шапка чата" : "Glass chat header", store.floatingChatHeader, !store.hasLoadError),
            .navigationToggle("header_unread", ru ? "Счётчик в шапке чата" : "Unread badge in chat header", store.headerUnread, !store.hasLoadError),
            .navigationToggle("message_menu_blur", ru ? "Размывать фон меню сообщения" : "Blur message-menu background", store.messageMenuBlur, !store.hasLoadError),
            .navigationToggle("profile_style", ru ? "Стеклянные кнопки и плавный баннер" : "Glass buttons and seamless banner", store.profileStyle, !store.hasLoadError),
            .navigationToggle("profile_channel", ru ? "Канал" : "Channel", store.profileChannel, !store.hasLoadError),
            .navigationToggle("profile_birthday", ru ? "День рождения" : "Birthday", store.profileBirthday, !store.hasLoadError),
            .navigationToggle("profile_business", ru ? "Часы работы и адрес" : "Business hours and location", store.profileBusiness, !store.hasLoadError),
            .navigationToggle("profile_background", ru ? "Цветной фон" : "Profile background", store.profileBackground, !store.hasLoadError),
            .navigationToggle("profile_emoji", ru ? "Узор из эмодзи" : "Emoji pattern", store.profileEmoji, !store.hasLoadError),
            .navigationToggle("profile_photo_banner", ru ? "Раскрывать фото в шапке" : "Expand photo in header", store.profilePhotoBanner, !store.hasLoadError),
            .navigationToggle("reply_background", ru ? "Фон ответа" : "Reply background", store.replyBackground, !store.hasLoadError),
            .navigationToggle("reply_colors", ru ? "Цвета автора в ответах" : "Author colors in replies", store.replyColors, !store.hasLoadError),
            .navigationToggle("reply_emoji", ru ? "Эмодзи в ответах" : "Emoji in replies", store.replyEmoji, !store.hasLoadError),
            .category(12, ru ? "Двойное нажатие на своё сообщение" : "Double tap your message",
                (ru ? ["Реакция", "Редактировать", "Ответить", "Копировать", "Ничего"] : ["React", "Edit", "Reply", "Copy", "Nothing"])[store.ownDoubleTap], "hand.tap"),
            .category(13, ru ? "Аватары" : "Avatars", ru ? "Форма и скругление" : "Shape and corners", "person.crop.circle"),
            .category(14, ru ? "Значки интерфейса" : "Interface icons", ["Telegram", "Cupertino", "Solar"][store.iconPack], "square.grid.2x2"),
            .category(15, ru ? "Переключатели" : "Switches", (ru ? ["Системные", "Округлые", "Компактные", "Минималистичные"] : ["System", "Rounded", "Compact", "Minimal"])[store.switchStyle], "switch.2"),
            .navigationToggle("login_style", ru ? "Экран входа NebulaGram" : "NebulaGram welcome screen", store.loginStyle, !store.hasLoadError),
            .category(16, ru ? "Уведомления и действия" : "Notifications and actions", ru ? "Упоминания, сохранение, защита" : "Mentions, saving, protection", "bell.badge"),
            .category(9, ru ? "Задачи" : "Tasks", "", "checkmark.circle"),
            .category(10, ru ? "Поведение чатов" : "Chat behavior", ru ? "Архив, вибрация, пересылка" : "Archive, vibration, forwarding", "hand.tap"),
            .category(11, ru ? "Архивация историй" : "Story archiving", "", "circle.dotted.circle"),
            .ai(ru ? "Искусственный интеллект" : "AI assistant"),
            .buildInfo(ru ? "О сборке" : "Build information"),
            .memory(ru ? "Память приложения" : "App memory")
        ]
        func isChatOption(_ entry: NebulaSettingsEntry) -> Bool {
            switch entry {
            case .widePosts, .stories:
                return true
            case let .category(index, _, _, _): return index == 10 || index == 11 || index == 12 || index == 16
            case let .navigationToggle(key, _, _, _):
                return key.hasPrefix("reply_") || ["hide_dividers", "hide_send_as", "hide_attach_camera", "menu_search", "menu_mute",
                    "menu_call", "menu_video", "centered_chat_header", "adaptive_chat_header", "floating_chat_header_v2", "header_unread", "message_menu_blur", "disable_next_channel", "seconds_in_time", "hide_search_field"].contains(key)
            default:
                return false
            }
        }
        func isNavigationOption(_ entry: NebulaSettingsEntry) -> Bool {
            switch entry {
            case .navigation, .contacts: return true
            case let .navigationToggle(key, _, _, _):
                return ["bottom_bar", "bottom_bar_profile", "bottom_bar_settings", "tab_labels", "compact_bottom_bar",
                    "hide_home_camera", "hide_home_compose"].contains(key)
            default: return false
            }
        }
        func isFolderOption(_ entry: NebulaSettingsEntry) -> Bool {
            if entry.section == 2 { return true }
            if case let .navigationToggle(key, _, _, _) = entry {
                return ["folder_title", "folder_outline", "hide_all_chats", "center_home"].contains(key)
            }
            return false
        }
        if page == 0 && query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            entries = [
                .search(query, ru ? "Поиск настроек" : "Search settings"),
                .toolsHeader(ru ? "Разделы" : "Sections"),
                .link("NebulaLink"),
                .category(1, ru ? "Основные" : "General", ru ? "Подключение, ИИ, сборка" : "Connection, AI, build", "gearshape"),
                .category(2, ru ? "Внешний вид" : "Appearance", ru ? "Стекло, значки, анимации" : "Glass, icons, animations", "paintpalette"),
                .category(7, ru ? "Навигация" : "Navigation", ru ? "Нижняя панель и кнопки" : "Bottom bar and buttons", "rectangle.bottomthird.inset.filled"),
                .category(3, ru ? "Чаты" : "Chats", ru ? "Список, сообщения, меню" : "List, messages, menus", "bubble.left"),
                .category(8, ru ? "Профиль" : "Profile", ru ? "Фото, фон и информация" : "Photo, background and details", "person.crop.circle"),
                .category(4, ru ? "Папки" : "Folders", ru ? "Вкладки и счётчики" : "Tabs and counters", "folder"),
                .category(5, ru ? "Конфиденциальность" : "Privacy", ru ? "Архив, защита, поиск" : "Archive, protection, search", "hand.raised"),
                .category(6, ru ? "Перенос настроек" : "Transfer", ru ? "Импорт и экспорт" : "Import and export", "arrow.triangle.2.circlepath"),
                .support(ru ? "Поддержать проект" : "Support the project"),
                .community("NebulaHub", community.peer, community.count)
            ]
        } else if page != 0 {
            entries = entries.filter { entry in
                if case .search = entry { return true }
                switch page {
                case 1: return entry.section == 0 && !isChatOption(entry)
                case 2: return entry.section == 1 && !isChatOption(entry) && !isNavigationOption(entry) && !isFolderOption(entry)
                case 3: return isChatOption(entry)
                case 4: return isFolderOption(entry)
                case 5: return entry.section == 3
                case 6: return entry.section == 4
                case 7: return isNavigationOption(entry)
                case 8: return entry.section == 5
                default: return false
                }
            }
            if page == 3 {
                let titles = ru ? ["Список чатов", "Заголовок и меню", "Ввод сообщения", "Сообщения", "Истории"] : ["Chat list", "Header and menu", "Composer", "Messages", "Stories"]
                for (index, title) in titles.enumerated() { entries.append(.chatHeader(6 + index, title)) }
            }
            if page == 7 { entries.append(.appearanceHeader(ru ? "Нижняя панель" : "Bottom bar")) }
        }
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
        let pageTitles = ru
            ? ["Настройки NebulaGram", "Основные", "Внешний вид", "Чаты", "Папки", "Конфиденциальность", "Перенос настроек", "Навигация", "Профиль"]
            : ["NebulaGram Settings", "General", "Appearance", "Chats", "Folders", "Privacy", "Transfer", "Navigation", "Profile"]
        let state = ItemListControllerState(presentationData: data, title: .text(pageTitles[max(0, min(pageTitles.count - 1, page))]), leftNavigationButton: nil, rightNavigationButton: nil, backNavigationButton: ItemListBackButton(title: presentationData.strings.Common_Back))
        return (state, (ItemListNodeState(presentationData: data, entries: entries, style: .blocks, animateChanges: false), arguments))
    }
    let controller = ItemListController(context: context, state: signal)
    transfer.host = controller
    arguments.openCategory = { [weak controller] index in
        guard let controller else { return }
        if index == 14 || index == 15 {
            let data = context.sharedContext.currentPresentationData.with { $0 }
            let ru = data.strings.baseLanguageCode.hasPrefix("ru")
            let icons = index == 14
            NebulaChoiceController.show(from: controller,
                title: icons ? (ru ? "Значки интерфейса" : "Interface icons") : (ru ? "Переключатели" : "Switches"),
                choices: icons ? ["Telegram", "Cupertino", "Solar"] : (ru ? ["Системные", "Округлые", "Компактные", "Минималистичные"] : ["System", "Rounded", "Compact", "Minimal"]),
                selected: icons ? store.iconPack : store.switchStyle,
                detail: icons ? (ru ? "Перезапустите приложение, чтобы обновить значки на всех экранах." : "Restart the app to update icons on every screen.") : nil,
                russian: ru, theme: data.theme) { value in
                    do { try store.set(.integer(value), for: icons ? "icon_pack" : "switch_style"); writeFailed.set(false) }
                    catch { writeFailed.set(true) }
                }
            return
        }
        if index == 16 {
            let behavior = NebulaBehaviorController(context: context) { [weak controller] writable, completion in
                guard let controller, let host = controller.presentedViewController else { return }
                let picker = context.sharedContext.makePeerSelectionController(PeerSelectionControllerParams(context: context,
                    filter: writable ? [.onlyWriteable, .excludeDisabled, .excludeSecretChats, .doNotSearchMessages] : [.excludeSecretChats, .doNotSearchMessages], hasContactSelector: false))
                picker.peerSelected = { [weak picker] peer, _ in
                    completion(peer.id.toInt64(), peer.compactDisplayTitle)
                    picker?.dismiss()
                }
                let navigation = NavigationController(mode: .single, theme: NavigationControllerTheme(presentationTheme: context.sharedContext.currentPresentationData.with { $0 }.theme))
                navigation.setViewControllers([picker], animated: false)
                host.present(navigation, animated: true)
            }
            controller.present(UINavigationController(rootViewController: behavior), animated: true)
            return
        }
        if index == 12 {
            let data = context.sharedContext.currentPresentationData.with { $0 }
            let ru = data.strings.baseLanguageCode.hasPrefix("ru")
            NebulaChoiceController.show(from: controller, title: ru ? "Двойное нажатие" : "Double tap",
                choices: ru ? ["Реакция", "Редактировать", "Ответить", "Копировать", "Ничего"] : ["React", "Edit", "Reply", "Copy", "Nothing"],
                selected: store.ownDoubleTap, russian: ru, theme: data.theme) { value in
                    do { try store.set(.integer(value), for: "own_double_tap"); writeFailed.set(false) }
                    catch { writeFailed.set(true) }
                }
            return
        }
        if index == 13 {
            let data = context.sharedContext.currentPresentationData.with { $0 }
            controller.present(UINavigationController(rootViewController: NebulaAvatarSettingsController(russian: data.strings.baseLanguageCode.hasPrefix("ru"), theme: data.theme)), animated: true)
            return
        }
        if index == 10 || index == 11 {
            let data = context.sharedContext.currentPresentationData.with { $0 }
            controller.present(UINavigationController(rootViewController: NebulaPrivacyController(context: context, russian: data.strings.baseLanguageCode.hasPrefix("ru"), mode: index == 10 ? 1 : 2)), animated: true)
            return
        }
        if index == 9 {
            let data = context.sharedContext.currentPresentationData.with { $0 }
            controller.present(UINavigationController(rootViewController: NebulaTasksController(accountId: String(context.account.peerId.toInt64()), russian: data.strings.baseLanguageCode.hasPrefix("ru"), theme: data.theme)), animated: true)
            return
        }
        guard (1...8).contains(index) else { return }
        (controller.navigationController as? NavigationController)?.pushViewController(
            nebulaSettingsController(context: context, page: index))
    }
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
            detail: ru ? "Звонки остаются рядом с контактами." : "Calls remain next to Contacts.", russian: ru,
            theme: context.sharedContext.currentPresentationData.with { $0 }.theme) { index in
                var order = store.bottomTabOrder; order.removeAll { $0 == keys[index] }; order.insert(keys[index], at: 0)
                do { try store.set(.string(order.joined(separator: ",")), for: "bottom_bar_order"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            }
    }
    arguments.openGlass = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaGlassController(russian: ru,
            theme: context.sharedContext.currentPresentationData.with { $0 }.theme)), animated: true)
    }
    arguments.openAi = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaAiController(russian: ru,
            theme: context.sharedContext.currentPresentationData.with { $0 }.theme)), animated: true)
    }
    arguments.openSupport = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let presentation = context.sharedContext.currentPresentationData.with { $0 }
        controller.present(UINavigationController(rootViewController: NebulaSupportController(
            russian: presentation.strings.baseLanguageCode.lowercased().hasPrefix("ru"),
            theme: presentation.theme, context: context, openCommunity: { [weak controller] id in
                controller?.dismiss(animated: true, completion: { [weak controller] in
                    (controller?.navigationController as? NavigationController)?.pushViewController(context.sharedContext.makeCommunityViewScreen(context: context, communityId: id, mode: .sheet))
                })
            })), animated: true)
    }
    arguments.openCommunity = { [weak controller] in
        guard let controller else { return }
        guard let peer = communityPeer else { communityReload.set(0); return }
        (controller.navigationController as? NavigationController)?.pushViewController(
            context.sharedContext.makeCommunityViewScreen(context: context, communityId: peer.id, mode: .sheet))
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
    arguments.openMemory = { [weak controller] in
        guard let controller = controller, controller.presentedViewController == nil else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        controller.present(UINavigationController(rootViewController: NebulaMemoryController(context: context, russian: ru)), animated: true)
    }
    arguments.openTransitions = { [weak controller] in
        guard let controller = controller else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        NebulaChoiceController.show(from: controller, title: ru ? "Анимация переходов" : "Transition animation",
            choices: ru ? ["Стандартная", "Системная", "Spring"] : ["Standard", "System", "Spring"], selected: store.transitionStyle,
            detail: ru ? "При включённом уменьшении движения iOS переходы остаются без анимации." : "Reduce Motion turns these animations off.", russian: ru,
            theme: context.sharedContext.currentPresentationData.with { $0 }.theme) { style in
                do { try store.set(.integer(style), for: "fragment_transition_style"); writeFailed.set(false) }
                catch { writeFailed.set(true) }
            }
    }
    arguments.openFolderStyle = { [weak controller] in
        guard let controller = controller else { return }
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        NebulaChoiceController.show(from: controller, title: ru ? "Стиль папок" : "Folder style",
            choices: ru ? ["Названия", "Только значки", "Значки и названия"] : ["Titles", "Icons only", "Icons and titles"],
            selected: max(0, min(2, store.folderStyle)), russian: ru,
            theme: context.sharedContext.currentPresentationData.with { $0 }.theme) { style in
                do { try store.set(.integer(style), for: "folder_style"); writeFailed.set(false) }
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
