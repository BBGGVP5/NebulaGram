import UIKit
import Display
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaCloudSettingsController: UITableViewController {
    private let context: AccountContext
    private let account: String
    private let theme: PresentationTheme
    private let ru: Bool
    private let sync = NebulaCloudSettingsSync.shared
    private var choices: [NebulaCloudSettingsDocument] = []
    private lazy var hero = NebulaSettingsHero(symbol: "🔄", title: text("Синхронизация", "Sync"), summary: text("Ваши настройки на разных устройствах", "Your settings across devices"), context: context, theme: theme)
    init(context: AccountContext) {
        self.context = context; account = String(context.account.peerId.toInt64())
        let data = context.sharedContext.currentPresentationData.with { $0 }; theme = data.theme; ru = data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(style: .insetGrouped); title = text("Синхронизация", "Sync")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 68
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        NotificationCenter.default.addObserver(self, selector: #selector(refresh), name: NebulaCloudSettingsSync.changed, object: nil)
        refresh()
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true); refresh() }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    @objc private func close() { dismiss(animated: true) }
    @objc private func refresh() { choices = sync.accountId == account ? sync.choices : []; tableView.reloadData() }
    override func numberOfSections(in tableView: UITableView) -> Int { choices.isEmpty ? 2 : 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 1 : section == 1 ? 2 : choices.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 { return text("Оформление и общие настройки сохраняются в вашем «Избранном». Ключи ИИ, аккаунты и содержимое чатов не передаются. Синхронизация работает, пока этот аккаунт открыт в приложении.", "Appearance and general preferences are saved in your Saved Messages. AI keys, accounts and chat contents are excluded. Sync runs while this account is open in the app.") }
        if section == 1 {
            guard sync.accountId == account else { return text("Переключитесь обратно на этот аккаунт", "Switch back to this account") }
            let date = sync.lastTime > 0 ? "\n" + DateFormatter.localizedString(from: Date(timeIntervalSince1970: sync.lastTime), dateStyle: .medium, timeStyle: .short) : ""
            return sync.status + date
        }
        return text("Выбранная версия заменит общие настройки на устройствах с включённой синхронизацией", "The selected version replaces shared preferences on devices with sync enabled")
    }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { section == 2 ? text("Версии с других устройств", "Device versions") : nil }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme)
        cell.textLabel?.numberOfLines = 0; cell.detailTextLabel?.numberOfLines = 0
        let available = sync.accountId == account
        if indexPath.section == 0 {
            cell.textLabel?.text = text("Синхронизировать настройки", "Sync settings")
            let toggle = NebulaSwitchControl(); toggle.isOn = available && sync.enabled; toggle.isEnabled = available; toggle.onTintColor = theme.list.itemAccentColor
            toggle.addTarget(self, action: #selector(toggle(_:)), for: .valueChanged); cell.accessoryView = toggle; cell.selectionStyle = .none
        } else if indexPath.section == 1 {
            cell.textLabel?.text = indexPath.row == 0 ? text("Синхронизировать сейчас", "Sync now") : text("Использовать настройки этого устройства", "Use this device’s settings")
            cell.textLabel?.textColor = theme.list.itemAccentColor
            cell.isUserInteractionEnabled = available && sync.enabled && !sync.busy
            cell.contentView.alpha = cell.isUserInteractionEnabled ? 1 : 0.45
        } else {
            let doc = choices[indexPath.row]
            cell.textLabel?.text = text("Устройство ", "Device ") + String(doc.device.prefix(8))
            cell.detailTextLabel?.text = text("Параметров: ", "Preferences: ") + String(doc.settings.count)
            cell.accessoryType = .disclosureIndicator; cell.isUserInteractionEnabled = available && sync.enabled && !sync.busy
        }
        return cell
    }
    @objc private func toggle(_ sender: NebulaSwitchControl) {
        guard sync.accountId == account else { refresh(); return }
        if !sender.isOn { sync.setEnabled(false); return }
        sender.isOn = false
        confirm(text("Включить синхронизацию?", "Enable sync?"), text("NebulaGram создаст служебное сообщение в «Избранном» этого аккаунта и будет обновлять его при изменении настроек. При различиях вы сможете выбрать нужную версию.", "NebulaGram will create a settings message in this account’s Saved Messages and update it when preferences change. If versions differ, you can choose which one to use.")) { self.sync.setEnabled(true) }
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard sync.accountId == account, sync.enabled, !sync.busy else { return }
        if indexPath.section == 1 && indexPath.row == 0 { sync.sync(); return }
        guard indexPath.section > 0 else { return }
        let selected = indexPath.section == 2 ? choices[indexPath.row] : nil
        confirm(text("Использовать эту версию?", "Use this version?"), text("Она заменит общие настройки на синхронизируемых устройствах. Личные данные и ключи останутся на месте.", "This replaces shared preferences on synced devices. Personal data and keys are retained.")) { self.sync.sync(keepLocal: selected == nil, selected: selected) }
    }
    private func confirm(_ title: String, _ message: String, action: @escaping () -> Void) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Продолжить", "Continue"), style: .default) { [weak self] _ in guard let self, self.sync.accountId == self.account else { return }; action() })
        present(alert, animated: true)
    }
    deinit { NotificationCenter.default.removeObserver(self) }
}
