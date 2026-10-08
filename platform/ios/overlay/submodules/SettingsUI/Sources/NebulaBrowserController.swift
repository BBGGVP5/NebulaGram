import UIKit
import Display
import AccountContext
import NebulaSettingsContract
import NebulaBrowserCore
import TelegramPresentationData

final class NebulaBrowserController: UITableViewController {
    private let context: AccountContext
    private let theme: PresentationTheme
    private let ru: Bool
    private let settings = NebulaBrowserPreferences.shared
    private var updating = false
    private var download: Task<Void, Never>?
    private lazy var hero = NebulaSettingsHero(symbol: "🛡️", title: ru ? "Блокировка рекламы" : "Ad blocking", summary: ru ? "Меньше рекламы во встроенном браузере" : "Fewer ads in the built-in browser", context: context, theme: theme)
    init(context: AccountContext) {
        self.context = context
        let data = context.sharedContext.currentPresentationData.with { $0 }; theme = data.theme; ru = data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(style: .insetGrouped); title = ru ? "Браузер и реклама" : "Browser and ads"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 72
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false); if isBeingDismissed || navigationController?.isBeingDismissed == true { download?.cancel() } }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 3 }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        let count = (try? settings.rules().count) ?? 0
        return ru ? "Поддерживаемых правил: \(count). Базовый список работает без загрузки. EasyList расширяет его поддерживаемыми правилами доменов и простых селекторов. Перезагрузите открытые страницы после изменений. Mini Apps не затрагиваются." : "Supported rules: \(count). The built-in list works offline. EasyList adds supported domain and simple selector rules. Reload open pages after changing settings. Mini Apps are unaffected."
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme)
        switch indexPath.row {
        case 0:
            cell.textLabel?.text = ru ? "Блокировать рекламу" : "Block ads"
            cell.detailTextLabel?.text = ru ? "Сетевые правила и скрытие баннеров" : "Network rules and banner hiding"
            let toggle = NebulaSwitchControl(); toggle.isOn = settings.enabled; toggle.onTintColor = theme.list.itemAccentColor
            toggle.addTarget(self, action: #selector(toggle(_:)), for: .valueChanged); cell.accessoryView = toggle; cell.selectionStyle = .none
        case 1:
            cell.textLabel?.text = ru ? "Исключения сайтов" : "Excluded sites"; cell.detailTextLabel?.text = settings.exclusions.joined(separator: ", "); cell.accessoryType = .disclosureIndicator
        default:
            cell.textLabel?.text = updating ? (ru ? "Обновление…" : "Updating…") : (ru ? "Обновить фильтры EasyList" : "Update EasyList filters")
            if let date = settings.updatedAt { cell.detailTextLabel?.text = DateFormatter.localizedString(from: date, dateStyle: .medium, timeStyle: .short) }
            cell.accessoryType = .disclosureIndicator
            if #unavailable(iOS 15.0) { cell.detailTextLabel?.text = ru ? "Загрузка обновлений доступна с iOS 15" : "Filter downloads require iOS 15" }
        }
        return cell
    }
    @objc private func toggle(_ sender: NebulaSwitchControl) { settings.enabled = sender.isOn }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.row == 1 { editExclusions() }
        else if indexPath.row == 2, !updating, #available(iOS 15.0, *) { updateFilters() }
    }
    private func editExclusions() {
        let alert = UIAlertController(title: ru ? "Исключения сайтов" : "Excluded sites", message: ru ? "Домены через пробел или запятую, например example.com" : "Domains separated by spaces or commas, for example example.com", preferredStyle: .alert)
        alert.addTextField { $0.text = self.settings.exclusions.joined(separator: ", "); $0.autocapitalizationType = .none; $0.autocorrectionType = .no }
        alert.addAction(UIAlertAction(title: ru ? "Отмена" : "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: ru ? "Сохранить" : "Save", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            do { try self.settings.saveExclusions(alert?.textFields?.first?.text ?? ""); self.tableView.reloadData() } catch { self.report() }
        }); present(alert, animated: true)
    }
    @available(iOS 15.0, *) private func updateFilters() {
        updating = true; tableView.reloadData()
        download = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let config = URLSessionConfiguration.ephemeral; config.httpCookieStorage = nil; config.urlCache = nil
                config.timeoutIntervalForRequest = 30; config.timeoutIntervalForResource = 90
                let session = URLSession(configuration: config); defer { session.invalidateAndCancel() }
                let (bytes, response) = try await session.bytes(from: URL(string: "https://easylist.to/easylist/easylist.txt")!)
                guard let response = response as? HTTPURLResponse, response.statusCode == 200, response.expectedContentLength <= 5_000_000 else { throw NebulaBrowserRules.Failure.tooLarge }
                var data = Data()
                for try await byte in bytes { try Task.checkCancellation(); guard data.count < 5_000_000 else { throw NebulaBrowserRules.Failure.tooLarge }; data.append(byte) }
                guard let text = String(data: data, encoding: .utf8), text.hasPrefix("[Adblock") else { throw NebulaBrowserRules.Failure.noSupportedRules }
                let rules = try self.settings.rules(filter: text)
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    NebulaBrowserContentRules.compile(rules) { result in continuation.resume(with: result.map { _ in () }) }
                }
                try Task.checkCancellation(); try self.settings.commitValidatedFilter(text)
                self.updating = false; self.tableView.reloadData()
            } catch {
                self.updating = false; self.tableView.reloadData()
                if !Task.isCancelled { self.report() }
            }
        }
    }
    private func report() {
        let alert = UIAlertController(title: ru ? "Не удалось сохранить изменения" : "Could not save changes", message: ru ? "Проверьте домены или подключение. Прежние фильтры сохранены." : "Check domains or your connection. Previous filters are retained.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
    @objc private func close() { download?.cancel(); dismiss(animated: true) }
    deinit { download?.cancel() }
}
