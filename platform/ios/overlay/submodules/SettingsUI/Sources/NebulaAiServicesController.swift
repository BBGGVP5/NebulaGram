import UIKit
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaAiServicesController: UITableViewController {
    private let ru: Bool
    private let theme: PresentationTheme?
    private let context: AccountContext?
    private let store = NebulaAiSettings.shared.services
    private lazy var hero = NebulaSettingsHero(symbol: "🌐", title: ru ? "Сервисы" : "Services",
        summary: ru ? "Ваши провайдеры, модели и подключения" : "Your providers, models and connections", context: context, theme: theme)
    init(russian: Bool, theme: PresentationTheme?, context: AccountContext?) {
        self.ru = russian; self.theme = theme; self.context = context; super.init(style: .insetGrouped)
        title = russian ? "Сервисы" : "Services"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(add))
        do { try store.migrateLegacy(secrets: .shared) } catch { report() }
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true); tableView.reloadData() }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    @objc private func add() { open(nil) }
    private func open(_ value: NebulaAiConnection?) { navigationController?.pushViewController(NebulaAiConnectionController(connection: value, russian: ru, theme: theme), animated: true) }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { store.connections.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        store.hasLoadError ? (ru ? "Не удалось прочитать подключения. Сохранённые данные не изменены." : "Could not read connections. Stored data was not changed.")
            : (ru ? "Нажмите, чтобы выбрать. Кнопка справа открывает настройки подключения." : "Tap to select. The button on the right opens connection settings.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let value = store.connections[indexPath.row]
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.textLabel?.text = value.name; cell.detailTextLabel?.text = value.provider.title + (value.model.isEmpty ? "" : " · " + value.model)
        cell.accessoryType = .detailButton; NebulaSettingsStyle.finish(cell, theme: theme)
        if value.id == store.active?.id { cell.backgroundColor = (theme?.list.itemAccentColor ?? .systemBlue).withAlphaComponent(0.16); cell.accessibilityTraits.insert(.selected) }
        return cell
    }
    override func tableView(_ tableView: UITableView, accessoryButtonTappedForRowWith indexPath: IndexPath) { open(store.connections[indexPath.row]) }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        do { try store.select(store.connections[indexPath.row].id); tableView.reloadData() } catch { report() }
    }
    private func report() {
        let alert = UIAlertController(title: ru ? "Не удалось сохранить подключение" : "Could not save the connection", message: ru ? "Проверьте доступ к связке ключей и повторите." : "Check Keychain access and try again.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
}

private final class NebulaAiConnectionController: UITableViewController {
    private let ru: Bool
    private let theme: PresentationTheme?
    private let isNew: Bool
    private var catalogTask: Task<Void, Never>?
    private var catalogRevision = 0
    private var value: NebulaAiConnection
    private let nameField = UITextField()
    private let modelField = UITextField()
    private let endpointField = UITextField()
    private let keyField = UITextField()
    init(connection: NebulaAiConnection?, russian: Bool, theme: PresentationTheme?) {
        value = connection ?? NebulaAiConnection(name: "", provider: .openAI)
        isNew = connection == nil; ru = russian; self.theme = theme
        super.init(style: .insetGrouped)
        title = russian ? (connection == nil ? "Новый сервис" : "Настройки сервиса") : (connection == nil ? "New service" : "Service settings")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.keyboardDismissMode = .interactive; tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 60
        nameField.text = value.name; modelField.text = value.model; endpointField.text = value.endpoint
        let labels = ru ? ["Название сервиса", "ID модели", "HTTPS-адрес API", "Новый API-ключ"] : ["Service name", "Model ID", "HTTPS API address", "New API key"]
        for (field, label) in zip([nameField, modelField, endpointField, keyField], labels) {
            field.placeholder = label; field.accessibilityLabel = label
            field.font = .preferredFont(forTextStyle: .body); field.adjustsFontForContentSizeCategory = true
            field.autocapitalizationType = .none; field.autocorrectionType = .no
            field.textColor = theme?.list.itemPrimaryTextColor ?? .label
            field.addTarget(self, action: #selector(cancelCatalog), for: .editingChanged)
        }
        keyField.isSecureTextEntry = true; endpointField.keyboardType = .URL
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: ru ? "Сохранить" : "Save", style: .done, target: self, action: #selector(save))
    }
    override func numberOfSections(in tableView: UITableView) -> Int { isNew ? 3 : 4 }
    private var fields: [UITextField] { value.provider == .appleIntelligence ? [nameField] : value.provider == .custom ? [nameField, endpointField, modelField, keyField] : [nameField, modelField, keyField] }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? NebulaAiProvider.allCases.count : section == 1 ? fields.count : section == 2 && value.provider == .appleIntelligence ? 0 : 1 }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 1 ? (ru ? "Пустое поле ключа сохраняет прежний ключ. Он хранится только в связке ключей." : "An empty key field keeps the existing key. Keys are stored only in Keychain.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme)
        if indexPath.section == 0 {
            let provider = NebulaAiProvider.allCases[indexPath.row]
            cell.textLabel?.text = provider == .custom ? (ru ? "Свой сервис" : "Custom service") : provider.title
            if provider == value.provider { cell.backgroundColor = (theme?.list.itemAccentColor ?? .systemBlue).withAlphaComponent(0.16); cell.accessibilityTraits.insert(.selected) }
        } else if indexPath.section == 1 {
            let field = fields[indexPath.row]; field.translatesAutoresizingMaskIntoConstraints = false
            cell.contentView.addSubview(field)
            NSLayoutConstraint.activate([field.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 16), field.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -16), field.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 8), field.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -8)])
            cell.contentView.heightAnchor.constraint(greaterThanOrEqualToConstant: 56).isActive = true
            cell.selectionStyle = .none
        } else if indexPath.section == 2 { cell.textLabel?.text = catalogTask == nil ? (ru ? "Выбрать модель из списка" : "Choose a model from the list") : (ru ? "Остановить загрузку моделей" : "Stop loading models"); cell.accessoryType = .disclosureIndicator
        } else { cell.textLabel?.text = ru ? "Удалить сервис" : "Delete service"; cell.textLabel?.textColor = .systemRed }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        if indexPath.section == 0 {
            cancelCatalog(); view.endEditing(true); value.provider = NebulaAiProvider.allCases[indexPath.row]; tableView.reloadData()
        } else if indexPath.section == 2 { chooseModel()
        } else if indexPath.section == 3 {
            let alert = UIAlertController(title: ru ? "Удалить сервис и его ключ?" : "Delete this service and its key?", message: nil, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: ru ? "Отмена" : "Cancel", style: .cancel))
            alert.addAction(UIAlertAction(title: ru ? "Удалить" : "Delete", style: .destructive) { [weak self] _ in
                guard let self else { return }
                do { try NebulaAiSettings.shared.services.remove(self.value.id, secrets: .shared); self.navigationController?.popViewController(animated: true) } catch { self.report() }
            }); present(alert, animated: true)
        }
    }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); cancelCatalog() }
    deinit { catalogTask?.cancel() }
    @objc private func cancelCatalog() {
        catalogRevision += 1; catalogTask?.cancel(); catalogTask = nil
        if isViewLoaded, value.provider != .appleIntelligence { tableView.reloadRows(at: [IndexPath(row: 0, section: 2)], with: .none) }
    }
    private func chooseModel() {
        if catalogTask != nil { cancelCatalog(); return }
        guard #available(iOS 15.0, *) else { reportCatalog(); return }
        view.endEditing(true)
        var connection = value; connection.endpoint = (endpointField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        var key = (keyField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            if key.isEmpty, let original = NebulaAiSettings.shared.services.connections.first(where: { $0.id == connection.id }), original.provider == connection.provider, original.url?.host == connection.url?.host {
                key = try NebulaAiSecrets.shared.serviceKey(id: connection.id) ?? ""
            }
        } catch { reportCatalog(); return }
        guard !key.isEmpty, connection.url != nil else { reportCatalog(); return }
        catalogRevision += 1; let revision = catalogRevision
        catalogTask = Task { @MainActor [weak self] in
            do {
                let models = try await NebulaAiModelCatalog.load(connection: connection, key: key)
                guard let self, !Task.isCancelled, revision == self.catalogRevision, self.view.window != nil else { return }
                self.catalogTask = nil; self.tableView.reloadRows(at: [IndexPath(row: 0, section: 2)], with: .none)
                NebulaChoiceController.show(from: self, title: self.ru ? "Модель" : "Model", choices: models, selected: models.firstIndex(of: self.modelField.text ?? ""), russian: self.ru, theme: self.theme, searchable: true, searchPlaceholder: self.ru ? "Поиск модели" : "Search models") { [weak self] index in self?.modelField.text = models[index] }
            } catch {
                guard let self, !Task.isCancelled, revision == self.catalogRevision else { return }
                self.catalogTask = nil; self.tableView.reloadRows(at: [IndexPath(row: 0, section: 2)], with: .none); self.reportCatalog()
            }
        }
        tableView.reloadRows(at: [IndexPath(row: 0, section: 2)], with: .none)
    }
    private func reportCatalog() {
        guard presentedViewController == nil else { return }
        let alert = UIAlertController(title: ru ? "Не удалось загрузить модели" : "Could not load models", message: ru ? "Проверьте ключ и адрес API. Сервис должен поддерживать список моделей; ID можно ввести вручную. Загрузка списка доступна с iOS 15." : "Check the API key and address. The service must support model listing; you can enter the ID manually. Loading the list requires iOS 15.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
    @objc private func save() {
        value.name = (nameField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        value.model = (modelField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        value.endpoint = (endpointField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let key = (keyField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            try NebulaAiSettings.shared.services.save(value, apiKey: key.isEmpty ? nil : key, secrets: .shared)
            navigationController?.popViewController(animated: true)
        } catch { report() }
    }
    private func report() {
        let alert = UIAlertController(title: ru ? "Проверьте данные сервиса" : "Check service details", message: ru ? "Нужны название, модель и корректный HTTPS-адрес. При смене провайдера или адреса введите новый ключ." : "Enter a name, model and valid HTTPS address. When changing provider or address, enter a new key.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
}
