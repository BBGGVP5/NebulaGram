import Foundation
import UIKit
import TelegramCore
import TelegramUIPreferences
import SwiftSignalKit
import AccountContext
import NebulaSettingsContract
import TelegramPresentationData

/// Per-account local privacy controls; never invokes Telegram's remote deletion API.
final class NebulaPrivacyController: UITableViewController {
    private let context: AccountContext
    private let ru: Bool
    private let sections: [Int]
    private let mode: Int
    var openAppLock: (() -> Void)?
    private var operation: Disposable?
    private var busy = false
    private var helpExpanded = false
    private var account: Int64 { context.account.peerId.toInt64() }
    private var currentTheme: PresentationTheme { context.sharedContext.currentPresentationData.with { $0 }.theme }
    private let archive = NebulaDeletedArchive.shared
    private lazy var hero = NebulaSettingsHero(symbol: "hand.raised",
        title: text("Локальные копии", "Local copies"),
        summary: text("Сохраняйте сообщения в чате. Выбирайте значок и очищайте копии, когда нужно.",
                      "Keep messages in the chat. Choose their marker and clear copies when needed."))
    init(context: AccountContext, russian: Bool, mode: Int = 0) {
        self.context = context; self.ru = russian; self.mode = mode
        self.sections = mode == 1 ? [7, 5] : mode == 2 ? [8] : [0, 1, 2, 3, 6, 4]
        super.init(style: .insetGrouped)
        title = mode == 1 ? (russian ? "Поведение чатов" : "Chat behavior") : mode == 2 ? (russian ? "Истории" : "Stories") : (russian ? "Удалённые сообщения" : "Deleted messages")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { operation?.dispose() }
    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }
    override func viewDidLoad() {
        super.viewDidLoad()
        let theme = currentTheme
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.backgroundColor = theme.list.blocksBackgroundColor
        tableView.separatorColor = theme.list.itemSecondaryTextColor.withAlphaComponent(0.12)
        view.tintColor = theme.list.itemAccentColor
        let navigationAppearance = UINavigationBarAppearance()
        navigationAppearance.configureWithOpaqueBackground()
        navigationAppearance.backgroundColor = theme.list.blocksBackgroundColor
        navigationAppearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        navigationController?.navigationBar.standardAppearance = navigationAppearance
        navigationController?.navigationBar.scrollEdgeAppearance = navigationAppearance
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard mode == 0 else { return }
        hero.setStatus(archive.enabled(account: account) ? text("Сохранение включено", "Retention on")
            : text("Сохранение выключено", "Retention off"), active: archive.enabled(account: account))
        hero.fit(in: tableView)
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { sections.count }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection displaySection: Int) -> Int { let section = sections[displaySection]; return section == 6 ? NebulaRetentionScope.allCases.count : section == 0 || section == 7 || section == 8 ? 3 : (section == 3 ? 2 : 1) }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection displaySection: Int) -> String? {
        let section = sections[displaySection]
        return [text("Удалённые сообщения", "Deleted messages"), text("Оформление", "Appearance"), text("Локальный кэш", "Local cache"), text("Защита", "Protection"), nil, text("Пересылка", "Forwarding"), text("Где сохранять", "Where to save"), text("Чаты", "Chats"), text("Истории", "Stories")][section]
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection displaySection: Int) -> String? {
        let section = sections[displaySection]
        if section == 6 { return text("В «Избранном» сохраняется уже полученное на iPhone, удалённое с другого устройства. Удаление в этом приложении работает как обычно.", "Saved Messages already received on this iPhone are kept when deleted on another device. Deleting in this app works as usual.") }
        if section == 7 { return text("Скрытый архив можно снова открыть из списка чатов. Настройка сохраняется в аккаунте Telegram.", "The hidden archive can still be opened from the chat list. Telegram stores this setting for your account.") }
        if section == 8 { return text("Истории выбранных типов будут скрываться через штатный архив Telegram для этого аккаунта.", "Stories from selected peer types will be hidden using Telegram's native story archive for this account.") }
        if section == 5 { return text("Редактор текста и подписей в меню пересылки. Вложения и альбомы сохраняются. Копия без автора, отправка вручную.", "Edit text and captions from forwarding options. Keep attachments and albums. An anonymous copy, sent manually.") }
        if section == 4 {
            return helpExpanded ? [details(0), details(2), details(3)].joined(separator: "\n\n") : nil
        }
        if section == 0 { return text("Секретные и исчезающие сообщения требуют отдельных переключателей. Выключение не очищает прежние копии.", "Secret and expiring messages require separate switches. Turning this off keeps existing copies.") }
        if section == 1 { return details(1) }
        if section == 2 { return text("Только локальные копии текущего аккаунта. Обычная переписка не удаляется.", "Only local copies for this account. Ordinary history is not deleted.") }
        return nil
    }
    private func details(_ section: Int) -> String {
        if section == 0 {
            return text("Полученные сообщения остаются на своём месте в чате. Секретные и исчезающие — только при отдельных включённых переключателях. Защита от копирования сохраняется. Фоновая работа возможна только когда iOS позволяет приложению обрабатывать обновления.", "Received messages remain in place. Secret and expiring messages require their separate switches. Copy protection is respected. Background retention requires iOS to allow the app to process updates.")
        }
        if section == 1 { return text("Вместо слова «Удалено» — выбранный значок. Сохранённые сообщения отображаются приглушённо.", "The chosen icon replaces the word Deleted. Retained messages are visually muted.") }
        if section == 3 { return text("Блокировка приложения защищает и сообщения в переписке. Отдельной блокировки только экрана архива недостаточно. Исключить чат из сохранения можно через меню очистки удалённых сообщений; старые копии очищаются отдельно.", "The app lock also protects inline messages. A lock on the archive settings alone would not. Exclude a chat using its retained-message cleanup menu; clear old copies separately.") }
        return text("Число сохранённых сообщений не ограничено, срок хранения — по умолчанию бессрочный. Текст и медиа остаются в обычном локальном хранилище приложения. Уже загруженные вложения доступны, пока их не очистит стандартный медиакэш. Удаление кэша здесь не отправляет запросов на сервер и не удаляет обычную переписку. Выключение сохранения не очищает прежние копии.", "The number of retained messages is not limited and retention is unlimited by default. Text and media stay in the app’s normal local storage. Downloaded attachments remain available until standard media cache eviction. Clearing here is local only and does not erase ordinary history. Turning retention off keeps existing copies.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt displayPath: IndexPath) -> UITableViewCell {
        let indexPath = IndexPath(row: displayPath.row, section: sections[displayPath.section])
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        let theme = currentTheme
        defer { NebulaSettingsStyle.finish(cell, theme: theme) }
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
        if indexPath.section == 8 {
            let key = storyArchiveKey(indexPath.row)
            NebulaSettingsHero.style(cell, symbol: "circle.dotted.circle")
            cell.textLabel?.text = ru ? ["Автоматически архивировать истории", "Истории пользователей", "Истории каналов"][indexPath.row]
                : ["Automatically archive stories", "User stories", "Channel stories"][indexPath.row]
            let toggle = UISwitch(); toggle.tag = indexPath.row
            toggle.isOn = UserDefaults.standard.object(forKey: key) == nil && indexPath.row == 1
                || UserDefaults.standard.bool(forKey: key)
            toggle.isEnabled = indexPath.row == 0 || UserDefaults.standard.bool(forKey: storyArchiveKey(0))
            toggle.addTarget(self, action: #selector(storyArchiveChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
            return cell
        }
        if indexPath.section == 7 {
            NebulaSettingsHero.style(cell, symbol: indexPath.row == 0 ? "archivebox" : indexPath.row == 1 ? "hand.tap" : "snowflake")
            if indexPath.row == 0 {
                cell.textLabel?.text = text("Архив в списке чатов", "Archive in chat list")
                cell.detailTextLabel?.text = text("Скрыть или показать", "Hide or show")
                cell.accessoryType = .disclosureIndicator
            } else if indexPath.row == 1 {
                cell.textLabel?.text = text("Отключить вибрацию в чатах", "Disable chat vibration")
                let toggle = UISwitch()
                toggle.isOn = UserDefaults.standard.bool(forKey: "nebula.chat.disableHaptics")
                toggle.addTarget(self, action: #selector(chatHapticsChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle; cell.selectionStyle = .none
            } else {
                cell.textLabel?.text = text("Снежинки в чатах", "Snowflakes in chats")
                let toggle = UISwitch()
                toggle.isOn = UserDefaults.standard.bool(forKey: "nebula.chat.snowflakes")
                toggle.addTarget(self, action: #selector(snowflakesChanged(_:)), for: .valueChanged)
                cell.accessoryView = toggle; cell.selectionStyle = .none
            }
            return cell
        }
        if indexPath.section == 6 {
            let scopes = NebulaRetentionScope.allCases
            cell.textLabel?.text = ru ? ["Личные чаты", "Группы", "Каналы", "Боты", "Избранное"][indexPath.row]
                : ["Private chats", "Groups", "Channels", "Bots", "Saved Messages"][indexPath.row]
            let toggle = UISwitch(); toggle.tag = indexPath.row
            toggle.isOn = archive.scopeEnabled(account: account, scope: scopes[indexPath.row])
            toggle.addTarget(self, action: #selector(scopeChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
            return cell
        }
        if indexPath.section == 5 {
            NebulaSettingsHero.style(cell, symbol: "square.and.pencil")
            cell.textLabel?.text = text("Редактирование перед пересылкой", "Edit before forwarding")
            let toggle = UISwitch()
            toggle.isOn = NebulaForwardEditing.shared.enabled
            toggle.addTarget(self, action: #selector(forwardEditingChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
            return cell
        }
        if indexPath.section == 4 {
            NebulaSettingsHero.style(cell, symbol: "info.circle")
            cell.textLabel?.text = text("Как работает сохранение", "How retention works")
            cell.detailTextLabel?.text = helpExpanded ? text("Скрыть пояснение", "Hide explanation")
                : text("Медиа, фон и ограничения", "Media, background and limitations")
            cell.accessoryType = .disclosureIndicator
            return cell
        }
        let symbols = indexPath.section == 0 ? ["archivebox", "lock", "timer"] : ["archivebox", "face.smiling", "trash", "lock.shield"]
        NebulaSettingsHero.style(cell, symbol: symbols[indexPath.section == 0 ? indexPath.row : indexPath.section])
        if indexPath.section == 0 {
            cell.textLabel?.text = [text("Сохранять удалённые сообщения", "Retain deleted messages"), text("Сохранять в секретных чатах", "Retain in secret chats"), text("Сохранять исчезающие сообщения", "Retain expiring messages")][indexPath.row]
            let toggle = UISwitch(); toggle.tag = indexPath.row
            toggle.isOn = [archive.enabled(account: account), archive.saveSecret(account: account), archive.saveExpiring(account: account)][indexPath.row]
            toggle.isEnabled = !busy && (indexPath.row == 0 || archive.enabled(account: account))
            toggle.addTarget(self, action: #selector(changed(_:)), for: .valueChanged)
            cell.accessoryView = toggle;cell.selectionStyle = .none
        } else if indexPath.section == 1 {
            cell.textLabel?.text = text("Значок сообщения", "Message icon")
            cell.detailTextLabel?.text = archive.icon;cell.accessoryType = .disclosureIndicator
        } else if indexPath.section == 3 {
            cell.textLabel?.text = indexPath.row == 0 ? text("Код-пароль / Face ID приложения", "App passcode / Face ID") : text("Срок хранения", "Retention period")
            cell.accessoryType = .disclosureIndicator
            if indexPath.row == 1 { cell.detailTextLabel?.text = retentionTitle(archive.retentionDays(account: account)) }
        } else {
            cell.textLabel?.text = busy ? text("Очистка…", "Clearing…") : text("Очистить удаленки", "Clear deleted messages")
            cell.textLabel?.textColor = .systemRed
            cell.imageView?.image = NebulaSettingsStyle.icon(symbol: "trash", color: .systemRed)
        }
        return cell
    }
    @objc private func scopeChanged(_ toggle: UISwitch) {
        archive.setScopeEnabled(account: account, scope: NebulaRetentionScope.allCases[toggle.tag], value: toggle.isOn)
    }
    private func storyArchiveKey(_ row: Int) -> String {
        "nebula.story.archive.\(account).\(row)"
    }
    @objc private func storyArchiveChanged(_ toggle: UISwitch) {
        UserDefaults.standard.set(toggle.isOn, forKey: storyArchiveKey(toggle.tag))
        if toggle.tag == 0 { tableView.reloadSections(IndexSet(integer: 8), with: .none) }
        NotificationCenter.default.post(name: Notification.Name("NebulaStoryArchiveSettingsChanged"), object: nil)
    }
    @objc private func chatHapticsChanged(_ toggle: UISwitch) {
        UserDefaults.standard.set(toggle.isOn, forKey: "nebula.chat.disableHaptics")
    }
    @objc private func snowflakesChanged(_ toggle: UISwitch) {
        UserDefaults.standard.set(toggle.isOn, forKey: "nebula.chat.snowflakes")
    }
    @objc private func forwardEditingChanged(_ toggle: UISwitch) {
        NebulaForwardEditing.shared.enabled = toggle.isOn
    }
    @objc private func changed(_ toggle: UISwitch) {
        let kind = toggle.tag
        if !toggle.isOn { set(kind, false); return }
        toggle.setOn(false, animated: true)
        let alert = UIAlertController(title: text("Оставлять локальные копии?", "Keep local copies?"), message: text("Копия останется после удаления или истечения таймера. Для секретных и исчезающих сообщений это меняет ожидаемое поведение. Уже исчезнувшее содержимое восстановить нельзя.", "A local copy remains after deletion or expiry. For secret and expiring messages this changes the expected behavior. Content that already disappeared cannot be recovered."), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Включить", "Enable"), style: .default) { [weak self] _ in self?.set(kind, true) })
        present(alert, animated: true)
    }
    private func set(_ kind: Int, _ value: Bool) {
        if kind == 0 { archive.setEnabled(account: account, value: value) }
        else if kind == 1 { archive.setSaveSecret(account: account, value: value) }
        else { archive.setSaveExpiring(account: account, value: value) }
        tableView.reloadData()
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt displayPath: IndexPath) {
        let indexPath = IndexPath(row: displayPath.row, section: sections[displayPath.section])
        tableView.deselectRow(at: displayPath, animated: true)
        guard !busy else { return }
        if indexPath.section == 7 && indexPath.row == 0 {
            NebulaChoiceController.show(from: self, title: text("Архив в списке чатов", "Archive in chat list"),
                choices: [text("Скрыть архив", "Hide archive"), text("Показать архив", "Show archive")],
                selected: nil, russian: ru, theme: currentTheme) { [weak self] index in
                guard let self = self else { return }
                let hidden = index == 0
                let _ = updateChatArchiveSettings(engine: self.context.engine, { settings in
                    var settings = settings
                    settings.isHiddenByDefault = hidden
                    return settings
                }).startStandalone()
            }
            return
        }
        if indexPath.section == 4 { helpExpanded.toggle(); tableView.reloadData(); return }
        if indexPath.section == 1 { pickIcon() }
        if indexPath.section == 3 {
            if indexPath.row == 0 { let open = openAppLock; dismiss(animated: true) { open?() }; return }
            let choices = NebulaDeletedArchive.retentionChoices
            NebulaChoiceController.show(from: self, title: text("Срок хранения", "Retention period"),
                choices: choices.map(retentionTitle), selected: choices.firstIndex(of: archive.retentionDays(account: account)),
                detail: text("При выбранном сроке старые копии очищаются во время обработки обновлений. По умолчанию — бессрочно.", "A chosen period prunes old copies while processing updates. Unlimited by default."), russian: ru, theme: currentTheme) { [weak self] index in
                guard let self = self else { return }
                self.archive.setRetentionDays(account: self.account, value: choices[index]); self.tableView.reloadData()
            }
        }
        if indexPath.section == 2 {
            let alert = UIAlertController(title: text("Очистить все сохранённые копии?", "Clear all retained copies?"), message: text("Только текущий аккаунт на этом устройстве. Обычная переписка и общий медиакэш не удаляются.", "Only this account on this device. Ordinary history and shared media cache are not deleted."), preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
            alert.addAction(UIAlertAction(title: text("Очистить", "Clear"), style: .destructive) { [weak self] _ in self?.clear() })
            present(alert, animated: true)
        }
    }
    private func retentionTitle(_ days: Int) -> String {
        days == 0 ? text("Бессрочно", "Unlimited") : "\(days) " + text("дн.", "days")
    }
    private func pickIcon() {
        let icons = ["🗑", "✕", "◌"]
        NebulaChoiceController.show(from: self, title: text("Значок сообщения", "Message icon"),
            choices: icons + [text("Свой символ / эмодзи", "Custom symbol / emoji")], selected: icons.firstIndex(of: archive.icon), russian: ru, theme: currentTheme) { [weak self] index in
            guard let self = self else { return }
            if index < icons.count { self.archive.icon = icons[index]; self.tableView.reloadData(); return }
            let custom = UIAlertController(title: self.text("Свой значок", "Custom icon"), message: self.text("До четырёх символов или эмодзи", "Up to four symbols or emoji"), preferredStyle: .alert)
            custom.addTextField { $0.text = self.archive.icon }
            custom.addAction(UIAlertAction(title: self.text("Отмена", "Cancel"), style: .cancel))
            custom.addAction(UIAlertAction(title: self.text("Сохранить", "Save"), style: .default) { [weak self, weak custom] _ in
                self?.archive.icon = custom?.textFields?.first?.text ?? ""; self?.tableView.reloadData()
            })
            self.present(custom, animated: true)
        }
    }
    private func clear() {
        busy = true;tableView.reloadData()
        operation = (NebulaDeletedMessages.clear(account: context.account) |> deliverOnMainQueue).start(next: { [weak self] success in
            guard let self = self else { return }
            self.busy = false;self.tableView.reloadData()
            if !success {
                let alert = UIAlertController(title: self.text("Не удалось очистить кэш", "Could not clear cache"), message: nil, preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "OK", style: .default));self.present(alert, animated: true)
            }
        })
    }
}
