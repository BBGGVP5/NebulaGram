import Display
import Foundation
import UIKit
import NebulaSettingsContract
import TelegramPresentationData
import AccountContext

/// AI connection settings. The key is written here and never read back into
/// the screen: the field shows whether one is stored, not what it is.
final class NebulaAiController: UITableViewController {
    private let translationAccount: String
    private let translationPeer: String?
    private let ru: Bool
    private let theme: PresentationTheme?
    private let context: AccountContext?
    private let settings = NebulaAiSettings.shared
    private let secrets = NebulaAiSecrets.shared
    private lazy var hero = NebulaSettingsHero(symbol: "sparkles",
        title: text("ИИ-помощник", "AI assistant"),
        summary: text("Ваш провайдер. Ваши инструкции. Только тот текст, который выберете вы.",
                      "Your provider. Your instructions. Only the text you choose."), context: context, theme: theme)
    private var provider: NebulaAiProvider
    private var readinessTimer: Timer?
    private var previousReadiness = ""
    private var visible = false

    init(russian: Bool, theme: PresentationTheme? = nil, account: String = "", peer: String? = nil, context: AccountContext? = nil) {
        self.context = context
        self.translationAccount = account; self.translationPeer = peer
        self.ru = russian
        self.theme = theme
        self.provider = NebulaAiSettings.shared.provider
        super.init(style: .insetGrouped)
        title = russian ? "Искусственный интеллект" : "AI assistant"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }

    override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        if let theme {
            tableView.backgroundColor = theme.list.blocksBackgroundColor
            tableView.separatorColor = theme.list.itemSecondaryTextColor.withAlphaComponent(0.12)
            view.tintColor = theme.list.itemAccentColor
        }
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 56
        NotificationCenter.default.addObserver(self, selector: #selector(refreshState), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(pauseReadiness), name: UIApplication.willResignActiveNotification, object: nil)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let ready = provider == .appleIntelligence ? NebulaAiService.localModelAvailable : settings.isConfigured(secrets: secrets)
        hero.setStatus(!settings.enabled ? text("ИИ выключен", "AI is off")
            : ready ? text("Доступно · ", "Available · ") + provider.title
            : text("Нужна настройка или поддерживаемое устройство", "Setup or a supported device needed"), active: settings.enabled && ready)
        hero.fit(in: tableView)
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        visible = true
        provider = settings.provider
        hero.setPageVisible(true)
        tableView.reloadData()
        refreshState()
    }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); visible = false; hero.setPageVisible(false); pauseReadiness() }
    deinit { readinessTimer?.invalidate(); NotificationCenter.default.removeObserver(self) }
    @objc private func pauseReadiness() { readinessTimer?.invalidate(); readinessTimer = nil }
    @objc private func refreshState() {
        guard isViewLoaded, visible else { return }
        updateReadiness()
        pauseReadiness()
        if UIApplication.shared.applicationState == .active {
            readinessTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in
                guard let self = self, self.view.window != nil else { return }
                self.updateReadiness()
            }
        }
    }
    private func updateReadiness() {
        let value = "\(provider.rawValue):\(settings.enabled):" + NebulaAiService.localModelStatus(russian: ru)
        guard previousReadiness != value else { return }
        previousReadiness = value
        tableView.reloadRows(at: [IndexPath(row: 0, section: 3)], with: .none)
        view.setNeedsLayout()
    }
    @objc private func close() { dismiss(animated: true) }

    override func numberOfSections(in tableView: UITableView) -> Int { 5 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 2 && settings.provider == .appleIntelligence ? 0 : [3, 4, 3, 1, 2][section] }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        [nil, text("Основные", "Settings"), text("Генерация", "Generation"), text("Состояние", "State"), text("Чат", "Chat")][section]
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        guard section == 2 else { return nil }
        return settings.provider == .appleIntelligence
            ? text("Параметрами системной модели управляет iOS. История хранится на устройстве.", "iOS manages the on-device model parameters. History stays on this device.")
            : text("Дополнительные рассуждения доступны для поддерживаемых моделей. Выключение оставляет настройки модели по умолчанию. История хранится на устройстве.", "Additional reasoning is available for supported models. Turning it off keeps model defaults. History stays on this device.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        defer { NebulaSettingsStyle.finish(cell, theme: theme) }
        func toggle(_ title: String, _ value: Bool, _ tag: Int) {
            cell.textLabel?.text = title
            let control = NebulaSwitchControl(); control.tag = tag; control.isOn = value
            control.addTarget(self, action: #selector(changeToggle(_:)), for: .valueChanged)
            control.accessibilityLabel = title; cell.accessoryView = control; cell.selectionStyle = .none
        }
        switch (indexPath.section, indexPath.row) {
        case (0, 0): toggle(text("Включить ИИ", "Enable AI"), settings.enabled, 0)
        case (0, 1): toggle(text("ИИ на главной", "AI on the home screen"), settings.homeShortcut, 1)
        case (0, 2):
            cell.textLabel?.text = text("ИИ в чате", "AI in chats"); cell.detailTextLabel?.text = text("Кнопка и автоматический перевод", "Shortcut and live translation"); cell.accessoryType = .disclosureIndicator
        case (1, 0):
            cell.textLabel?.text = text("Сервисы", "Services"); cell.detailTextLabel?.text = settings.services.active?.name ?? text("Выберите подключение", "Choose a connection"); cell.accessoryType = .disclosureIndicator
        case (1, 1):
            cell.textLabel?.text = text("Роли", "Roles"); cell.detailTextLabel?.text = settings.roles.selected(russian: ru)?.name ?? text("Свои инструкции", "General instructions"); cell.accessoryType = .disclosureIndicator
        case (1, 2): toggle(text("История сообщений", "Message history"), settings.historyEnabled, 2)
        case (1, 3): cell.textLabel?.text = text("Аудио и голоса", "Audio & voices"); cell.detailTextLabel?.text = text("Расшифровка, перевод и озвучивание", "Transcription, translation and speech"); cell.accessoryType = .disclosureIndicator
        case (2, 0): toggle(text("Потоковый ответ", "Streaming response"), settings.streaming, 3)
        case (2, 1):
            toggle(text("Рассуждения", "Reasoning"), settings.reasoning, 4)
            let supported = NebulaAiGenerationPolicy.supportsReasoning(provider: settings.provider, model: settings.model(for: settings.provider))
            cell.accessoryView?.isUserInteractionEnabled = supported; cell.accessoryView?.alpha = supported ? 1 : 0.4
            if !supported { cell.detailTextLabel?.text = text("Не поддерживается выбранной моделью", "Not supported by the selected model") }
        case (2, 2):
            cell.textLabel?.text = text("Температура", "Temperature"); cell.detailTextLabel?.text = String(format: "%.1f", settings.temperature); cell.accessoryType = .disclosureIndicator
        case (4, 0): cell.textLabel?.text = text("Открыть ИИ-чат", "Open AI chat"); cell.accessoryType = .disclosureIndicator
        case (4, 1): cell.textLabel?.text = text("История запросов", "Request history"); cell.accessoryType = .disclosureIndicator
        default:
            cell.textLabel?.text = settings.isConfigured() ? text("Сервис выбран", "Service selected") : text("Настройте сервис", "Set up a service")
            if settings.provider == .appleIntelligence { cell.detailTextLabel?.text = NebulaAiService.localModelStatus(russian: ru) }
            cell.selectionStyle = .none
        }
        return cell
    }
    @objc private func changeToggle(_ sender: NebulaSwitchControl) {
        switch sender.tag {
        case 0: settings.enabled = sender.isOn
        case 1: settings.homeShortcut = sender.isOn
        case 2: settings.historyEnabled = sender.isOn
        case 3: settings.streaming = sender.isOn
        default: settings.reasoning = sender.isOn
        }
        view.setNeedsLayout()
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let next: UIViewController
        switch (indexPath.section, indexPath.row) {
        case (0, 2): next = NebulaTranslationController(account: translationAccount, peer: translationPeer, russian: ru, theme: theme)
        case (1, 0): next = NebulaAiServicesController(russian: ru, theme: theme, context: context)
        case (1, 1): next = NebulaAiRolesController(russian: ru, theme: theme, context: context)
        case (1, 3): next = NebulaAudioSettingsController(russian: ru, theme: theme, context: context)
        case (2, 2):
            let values = (0...20).map { Double($0) / 10 }
            NebulaChoiceController.show(from: self, title: text("Температура", "Temperature"), choices: values.map { String(format: "%.1f", $0) }, selected: Int((settings.temperature * 10).rounded()), russian: ru, theme: theme) { [weak self] index in
                self?.settings.temperature = values[index]; self?.tableView.reloadData()
            }
            return
        case (4, 0): next = NebulaAiChatController(russian: ru, theme: theme, account: translationAccount, peer: translationPeer)
        case (4, 1): next = NebulaAiHistoryController(russian: ru, theme: theme)
        default: return
        }
        navigationController?.pushViewController(next, animated: true)
    }
}
