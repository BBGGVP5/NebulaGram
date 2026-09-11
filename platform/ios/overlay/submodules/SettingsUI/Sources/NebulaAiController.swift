import Foundation
import UIKit
import AccountContext
import NebulaSettingsContract

/// AI connection settings. The key is written here and never read back into
/// the screen: the field shows whether one is stored, not what it is.
final class NebulaAiController: UITableViewController {
    private let ru: Bool
    private let settings = NebulaAiSettings.shared
    private let secrets = NebulaAiSecrets.shared
    private var provider: NebulaAiProvider

    init(russian: Bool) {
        self.ru = russian
        self.provider = NebulaAiSettings.shared.provider
        super.init(style: .insetGrouped)
        title = russian ? "Искусственный интеллект" : "AI assistant"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 56
    }
    @objc private func close() { dismiss(animated: true) }

    // Sections: switch, connection, instructions, state.
    override func numberOfSections(in tableView: UITableView) -> Int { 4 }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return 1
        // Provider, model, key, remove key — plus the address for a custom one.
        case 1: return provider == .custom ? 5 : 4
        case 2: return 1
        default: return 1
        }
    }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        [nil,
         text("Подключение", "Connection"),
         text("Инструкции для ИИ", "AI instructions"),
         text("Состояние", "State")][section]
    }

    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        switch section {
        case 0:
            return text("Пункт в меню сообщений появляется после того, как подключение настроено. Текст, который вы выберете, уходит выбранному провайдеру — это внешний сервис, не Telegram и не NebulaGram.",
                        "The entry appears in message menus once a connection is configured. Whatever you pick is sent to the provider you chose — an outside service, not Telegram and not NebulaGram.")
        case 1:
            return text("Ключ хранится в связке ключей устройства: он не попадает в iCloud, в резервные копии и в перенос настроек. Обратно на экран он не читается — поле показывает только, сохранён ли он.",
                        "The key is kept in the device keychain: it stays out of iCloud, out of backups and out of the settings transfer. It is never read back into this screen, which only shows whether one is stored.")
        case 2:
            return text("Отправляется вместе с каждым запросом.", "Sent with every request.")
        default: return nil
        }
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .value1, reuseIdentifier: nil)
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.numberOfLines = 0

        switch (indexPath.section, indexPath.row) {
        case (0, _):
            cell.textLabel?.text = text("Включить ИИ", "Enable AI")
            let toggle = UISwitch()
            toggle.isOn = settings.enabled
            toggle.addTarget(self, action: #selector(toggleEnabled(_:)), for: .valueChanged)
            cell.accessoryView = toggle
            cell.selectionStyle = .none
        case (1, 0):
            cell.textLabel?.text = text("Провайдер", "Provider")
            cell.detailTextLabel?.text = provider.title
            cell.accessoryType = .disclosureIndicator
        case (1, 1) where provider == .custom:
            cell.textLabel?.text = text("Адрес API", "API address")
            let address = settings.customEndpoint
            cell.detailTextLabel?.text = address.isEmpty ? text("Не задан", "Not set") : address
            cell.detailTextLabel?.textColor = settings.endpoint(for: .custom) == nil ? .systemRed : .secondaryLabel
            cell.accessoryType = .disclosureIndicator
        case (1, let row) where row == (provider == .custom ? 2 : 1):
            cell.textLabel?.text = text("Модель", "Model")
            let model = settings.model(for: provider)
            cell.detailTextLabel?.text = model.isEmpty ? text("Не задана", "Not set") : model
            cell.accessoryType = .disclosureIndicator
        case (1, let row) where row == (provider == .custom ? 3 : 2):
            cell.textLabel?.text = text("API-ключ", "API key")
            cell.detailTextLabel?.text = secrets.hasKey(for: provider)
                ? text("Сохранён", "Stored") : text("Не задан", "Not set")
            cell.accessoryType = .disclosureIndicator
        case (1, _):
            cell.textLabel?.text = text("Удалить сохранённый ключ", "Remove stored key")
            cell.textLabel?.textColor = secrets.hasKey(for: provider) ? .systemRed : .tertiaryLabel
            cell.selectionStyle = secrets.hasKey(for: provider) ? .default : .none
        case (2, _):
            let instructions = settings.instructions
            cell.textLabel?.text = instructions.isEmpty
                ? text("Задать инструкции", "Set instructions") : instructions
            cell.textLabel?.textColor = instructions.isEmpty ? .label : .secondaryLabel
            cell.accessoryType = .disclosureIndicator
        default:
            let ready = settings.isConfigured(secrets: secrets)
            cell.textLabel?.text = ready
                ? text("Подключение настроено", "Connection is configured")
                : text("Укажите адрес, модель и ключ", "Set an address, a model and a key")
            cell.textLabel?.textColor = ready ? .systemGreen : .secondaryLabel
            cell.selectionStyle = .none
        }
        return cell
    }

    @objc private func toggleEnabled(_ toggle: UISwitch) {
        settings.enabled = toggle.isOn
        tableView.reloadData()
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let custom = provider == .custom
        switch (indexPath.section, indexPath.row) {
        case (1, 0): pickProvider()
        case (1, 1) where custom:
            edit(title: text("Адрес API", "API address"), value: settings.customEndpoint,
                 placeholder: "https://example.com/v1", secure: false) { [weak self] value in
                self?.settings.customEndpoint = value
            }
        case (1, let row) where row == (custom ? 2 : 1):
            edit(title: text("Модель", "Model"), value: settings.model(for: provider),
                 placeholder: text("Идентификатор модели", "Model id"), secure: false) { [weak self] value in
                guard let self = self else { return }
                self.settings.setModel(value, for: self.provider)
            }
        case (1, let row) where row == (custom ? 3 : 2):
            // Empty on purpose: the stored key is never shown, so saving an
            // untouched field would wipe it. An empty save removes it, which
            // is what the row below does explicitly.
            edit(title: text("API-ключ", "API key"), value: "",
                 placeholder: text("Вставьте ключ провайдера", "Paste a provider key"), secure: true) { [weak self] value in
                guard let self = self, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                self.store(value)
            }
        case (1, _):
            guard secrets.hasKey(for: provider) else { return }
            confirmRemoval()
        case (2, _):
            edit(title: text("Инструкции для ИИ", "AI instructions"), value: settings.instructions,
                 placeholder: text("Например: отвечай кратко", "For example: answer briefly"), secure: false) { [weak self] value in
                self?.settings.instructions = value
            }
        default: break
        }
    }

    private func pickProvider() {
        let alert = UIAlertController(title: text("Провайдер", "Provider"), message: nil, preferredStyle: .actionSheet)
        for option in NebulaAiProvider.allCases {
            let mark = option == provider ? "✓ " : ""
            alert.addAction(UIAlertAction(title: mark + option.title, style: .default) { [weak self] _ in
                guard let self = self else { return }
                self.provider = option
                self.settings.provider = option
                self.tableView.reloadData()
            })
        }
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.popoverPresentationController?.sourceView = tableView
        alert.popoverPresentationController?.sourceRect = tableView.rectForRow(at: IndexPath(row: 0, section: 1))
        present(alert, animated: true)
    }

    private func edit(title: String, value: String, placeholder: String, secure: Bool,
                      apply: @escaping (String) -> Void) {
        let alert = UIAlertController(title: title, message: nil, preferredStyle: .alert)
        alert.addTextField { field in
            field.text = value
            field.placeholder = placeholder
            field.isSecureTextEntry = secure
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
            field.clearButtonMode = .whileEditing
            if secure { field.textContentType = .password }
        }
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Сохранить", "Save"), style: .default) { [weak alert, weak self] _ in
            apply(alert?.textFields?.first?.text ?? "")
            self?.tableView.reloadData()
        })
        present(alert, animated: true)
    }

    private func store(_ value: String) {
        do {
            try secrets.setKey(value, for: provider)
        } catch {
            report(text("Связка ключей не приняла ключ. Он не сохранён.",
                        "The keychain refused the key. It was not saved."))
        }
    }

    private func confirmRemoval() {
        let alert = UIAlertController(title: text("Удалить сохранённый ключ?", "Remove the stored key?"),
                                      message: text("Ключ этого провайдера будет забыт. Остальные останутся.",
                                                    "This provider's key is forgotten. The others stay."),
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Удалить", "Remove"), style: .destructive) { [weak self] _ in
            guard let self = self else { return }
            do { try self.secrets.removeKey(for: self.provider) }
            catch { self.report(self.text("Не удалось удалить ключ.", "Could not remove the key.")) }
            self.tableView.reloadData()
        })
        present(alert, animated: true)
    }

    private func report(_ message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
