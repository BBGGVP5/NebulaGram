import Foundation
import UIKit
import TelegramCore
import SwiftSignalKit
import AccountContext
import NebulaSettingsContract

/// Per-account local privacy controls; never invokes Telegram's remote deletion API.
final class NebulaPrivacyController: UITableViewController {
    private let context: AccountContext
    private let ru: Bool
    var openAppLock: (() -> Void)?
    private var operation: Disposable?
    private var busy = false
    private var account: Int64 { context.account.peerId.toInt64() }
    private let archive = NebulaDeletedArchive.shared
    init(context: AccountContext, russian: Bool) {
        self.context = context; self.ru = russian
        super.init(style: .insetGrouped)
        title = russian ? "Конфиденциальность" : "Privacy"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { operation?.dispose() }
    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }
    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 4 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 3 : (section == 3 ? 2 : 1) }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        [text("Удалённые сообщения", "Deleted messages"), text("Оформление", "Appearance"), text("Локальный кэш", "Local cache"), text("Защита", "Protection")][section]
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 {
            return text("Полученные сообщения остаются на своём месте в чате. Секретные и исчезающие — только при отдельных включённых переключателях. Защита от копирования сохраняется. Фоновая работа возможна только когда iOS позволяет приложению обрабатывать обновления.", "Received messages remain in place. Secret and expiring messages require their separate switches. Copy protection is respected. Background retention requires iOS to allow the app to process updates.")
        }
        if section == 1 { return text("Вместо слова «Удалено» — выбранный значок. Сохранённые сообщения отображаются приглушённо.", "The chosen icon replaces the word Deleted. Retained messages are visually muted.") }
        if section == 3 { return text("Блокировка приложения защищает и сообщения в переписке. Отдельной блокировки только экрана архива недостаточно. Исключить чат из сохранения можно через меню очистки удалённых сообщений; старые копии очищаются отдельно.", "The app lock also protects inline messages. A lock on the archive settings alone would not. Exclude a chat using its retained-message cleanup menu; clear old copies separately.") }
        return text("Число сохранённых сообщений не ограничено, срок хранения — по умолчанию бессрочный. Текст и медиа остаются в обычном локальном хранилище приложения. Уже загруженные вложения доступны, пока их не очистит стандартный медиакэш. Удаление кэша здесь не отправляет запросов на сервер и не удаляет обычную переписку. Выключение сохранения не очищает прежние копии.", "The number of retained messages is not limited and retention is unlimited by default. Text and media stay in the app’s normal local storage. Downloaded attachments remain available until standard media cache eviction. Clearing here is local only and does not erase ordinary history. Turning retention off keeps existing copies.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .value1, reuseIdentifier: nil)
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
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
            cell.textLabel?.text = busy ? text("Очистка…", "Clearing…") : text("Очистить кэш удалённых сообщений", "Clear retained-message cache")
            cell.textLabel?.textColor = .systemRed
        }
        return cell
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
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !busy else { return }
        if indexPath.section == 1 { pickIcon() }
        if indexPath.section == 3 {
            if indexPath.row == 0 { let open = openAppLock; dismiss(animated: true) { open?() }; return }
            let alert = UIAlertController(title: text("Срок хранения", "Retention period"), message: text("По умолчанию копии хранятся бессрочно. Если задать срок, просроченные копии очищаются при следующей обработке удалений, не по фоновому таймеру, и после очистки это необратимо.", "By default copies are kept indefinitely. If a period is set, expired copies are pruned on the next deletion update, not by a background timer, and pruning is irreversible."), preferredStyle: .alert)
            for days in NebulaDeletedArchive.retentionChoices {
                alert.addAction(UIAlertAction(title: retentionTitle(days), style: .default) { [weak self] _ in
                    guard let self = self else { return }
                    self.archive.setRetentionDays(account: self.account, value: days); self.tableView.reloadData()
                })
            }
            alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel)); present(alert, animated: true)
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
        let alert = UIAlertController(title: text("Значок вместо «Удалено»", "Icon instead of Deleted"), message: nil, preferredStyle: .alert)
        for icon in ["🗑", "✕", "◌"] {
            alert.addAction(UIAlertAction(title: icon, style: .default) { [weak self] _ in self?.archive.icon = icon;self?.tableView.reloadData() })
        }
        alert.addAction(UIAlertAction(title: text("Свой символ / эмодзи", "Custom symbol / emoji"), style: .default) { [weak self] _ in
            guard let self = self else { return }
            let custom = UIAlertController(title: self.text("Свой значок", "Custom icon"), message: self.text("До четырёх символов или эмодзи", "Up to four symbols or emoji"), preferredStyle: .alert)
            custom.addTextField { $0.text = self.archive.icon }
            custom.addAction(UIAlertAction(title: self.text("Отмена", "Cancel"), style: .cancel))
            custom.addAction(UIAlertAction(title: self.text("Сохранить", "Save"), style: .default) { [weak self, weak custom] _ in
                self?.archive.icon = custom?.textFields?.first?.text ?? ""
                self?.tableView.reloadData()
            })
            self.present(custom, animated: true)
        })
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        present(alert, animated: true)
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
