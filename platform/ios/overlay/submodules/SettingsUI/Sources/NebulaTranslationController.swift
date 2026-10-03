import Display
import UIKit
import NebulaSettingsContract
import TelegramPresentationData

public final class NebulaTranslationController: UITableViewController {
    public var chooseChat: (() -> Void)?
    private let account: String
    private let peer: String?
    private let russian: Bool
    private let theme: PresentationTheme?
    private let settings = NebulaTranslationPreferences.shared
    public init(account: String, peer: String?, russian: Bool, theme: PresentationTheme?) {
        self.account = account; self.peer = peer; self.russian = russian; self.theme = theme
        super.init(style: .insetGrouped); title = russian ? "ИИ в чате" : "AI in chats"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }
    private var options: NebulaTranslationOptions { settings.options(account: account, peer: peer ?? "") }
    public override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 56
        if navigationController?.viewControllers.first === self {
            navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        }
    }
    @objc private func close() { dismiss(animated: true) }
    public override func numberOfSections(in tableView: UITableView) -> Int { peer == nil ? 1 : 3 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section > 0 || (section == 0 && peer == nil && chooseChat != nil) ? 3 : 2 }
    public override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 1 ? text("Входящие сообщения", "Incoming messages") : section == 2 ? text("Мой текст", "My text") : nil
    }
    public override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 && peer == nil { return text("Выберите чат для перевода входящих сообщений и текста при наборе.", "Choose a chat to translate incoming messages and your text while typing.") }
        if section == 1, let peer = peer { return NebulaLiveTranslation.error(account: account, peer: peer) }
        if section == 2 { return text("Включённые режимы автоматически передают текст выбранному провайдеру ИИ. Черновик заменяется только кнопкой «Применить».", "Enabled modes automatically process text using your selected AI provider. Your draft changes only when you tap Apply.") }
        return nil
    }
    public override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        defer { NebulaSettingsStyle.finish(cell, theme: theme) }
        cell.textLabel?.numberOfLines = 0; cell.detailTextLabel?.numberOfLines = 0
        if indexPath.row == 0 {
            let toggle = NebulaSwitchControl(); toggle.tag = indexPath.section
            toggle.isOn = indexPath.section == 0 ? settings.composerShortcut : indexPath.section == 1 ? options.incoming : options.draft
            toggle.addTarget(self, action: #selector(toggled(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
            cell.textLabel?.text = indexPath.section == 0 ? text("Кнопка ИИ в поле ввода", "AI button in composer") : indexPath.section == 1 ? text("Переводить через ИИ", "Translate using AI") : text("Переводить при наборе", "Translate while typing")
            cell.detailTextLabel?.text = indexPath.section == 0 ? text("Справа · удержание открывает настройки", "On the right · hold for settings") : indexPath.section == 2 ? text("Предпросмотр с кнопкой применения", "Preview with an Apply button") : nil
        } else {
            cell.accessoryType = .disclosureIndicator
            if indexPath.section == 0 {
                cell.textLabel?.text = indexPath.row == 1 ? text("Провайдер и модель", "Provider and model") : text("Перевод в реальном времени", "Real-time translation")
                if indexPath.row == 2 { cell.detailTextLabel?.text = text("Выбрать чат", "Choose chat") }
            }
            else if indexPath.row == 1 {
                cell.textLabel?.text = text("Язык перевода", "Translation language")
                cell.detailTextLabel?.text = NebulaResultLanguage.title(indexPath.section == 1 ? options.incomingLanguage : options.draftLanguage, russian: russian)
            } else if indexPath.section == 1 { cell.textLabel?.text = text("Повторить перевод", "Retry translation")
            } else { cell.textLabel?.text = text("Пауза после ввода", "Pause after typing"); cell.detailTextLabel?.text = "\(options.delay) s" }
        }
        return cell
    }
    @objc private func toggled(_ sender: NebulaSwitchControl) {
        if sender.tag == 0 { settings.composerShortcut = sender.isOn; return }
        if sender.isOn && !NebulaLiveTranslation.ready {
            sender.isOn = false
            let alert = UIAlertController(title: text("Настройте ИИ", "Configure AI"), message: text("Включите ИИ и выберите доступного провайдера и модель.", "Enable AI and select an available provider and model."), preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true); return
        }
        guard let peer = peer else { return }
        settings.update(account: account, peer: peer) { if sender.tag == 1 { $0.incoming = sender.isOn } else { $0.draft = sender.isOn } }
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 0 && indexPath.row == 1 { navigationController?.pushViewController(NebulaAiController(russian: russian, theme: theme), animated: true); return }
        if indexPath.section == 0 && indexPath.row == 2 { chooseChat?(); return }
        guard let peer = peer, indexPath.section > 0 else { return }
        if indexPath.row == 1 {
            NebulaResultLanguage.show(from: self, selected: indexPath.section == 1 ? options.incomingLanguage : options.draftLanguage, russian: russian, theme: theme) { [weak self] code in
                guard let self = self else { return }
                self.settings.update(account: self.account, peer: peer) { if indexPath.section == 1 { $0.incomingLanguage = code } else { $0.draftLanguage = code } }
                self.tableView.reloadData()
            }
        } else if indexPath.row == 2 && indexPath.section == 1 {
            NebulaLiveTranslation.retry(account: account, peer: peer); tableView.reloadData()
        } else if indexPath.row == 2 {
            NebulaChoiceController.show(from: self, title: text("Пауза после ввода", "Pause after typing"), choices: ["0.5 s", "1 s", "2 s"], selected: [0.5, 1, 2].firstIndex(of: options.delay), russian: russian, theme: theme) { [weak self] index in
                guard let self = self else { return }
                self.settings.update(account: self.account, peer: peer) { $0.delay = [0.5, 1, 2][index] }; self.tableView.reloadData()
            }
        }
    }
}
