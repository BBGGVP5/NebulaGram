import UIKit

/// Saved subscription sources. The URL and raw core errors can contain credentials.
final class NebulaSubscriptionsController: UITableViewController {
    private let russian: Bool
    private let service = NebulaLinkService.shared
    private var subscriptions: [[String: Any]] = []
    private var busy = false
    private var message: String?
    var onChange: (() -> Void)?

    init(russian: Bool) {
        self.russian = russian
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = text("Подписки", "Subscriptions")
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add,
                target: self, action: #selector(addSubscription))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 68
        reloadSubscriptions()
    }

    private func reloadSubscriptions() {
        service.call("subscription.list") { [weak self] result in
            guard let self = self else { return }
            switch result {
            case let .success(data):
                self.subscriptions = data as? [[String: Any]] ?? []
                self.message = nil
            case .failure:
                self.message = self.text("Не удалось загрузить подписки", "Could not load subscriptions")
            }
            self.tableView.reloadData()
        }
    }

    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        max(1, subscriptions.count)
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        message
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        guard !subscriptions.isEmpty else {
            cell.textLabel?.text = text("Сохранённых подписок нет", "No saved subscriptions")
            cell.selectionStyle = .none
            return cell
        }
        let source = subscriptions[indexPath.row]
        let name = (source["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        cell.textLabel?.text = name.isEmpty ? text("Подписка", "Subscription") : name
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.text = detail(for: source)
        cell.detailTextLabel?.numberOfLines = 0
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.imageView?.image = UIImage(systemName: "link")
        cell.accessoryType = .disclosureIndicator
        cell.selectionStyle = busy ? .none : .default
        return cell
    }

    private func detail(for source: [String: Any]) -> String {
        let count = (source["server_count"] as? NSNumber)?.intValue ?? 0
        var parts = [text("Серверов: \(count)", "Servers: \(count)")]
        if let updated = source["updated_at"] as? NSNumber, updated.doubleValue > 0 {
            let date = Date(timeIntervalSince1970: updated.doubleValue)
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            formatter.timeStyle = .short
            parts.append(formatter.string(from: date))
        }
        if let error = source["last_error"] as? String, !error.isEmpty {
            parts.append(text("Ошибка обновления", "Refresh failed"))
        }
        return parts.joined(separator: " · ")
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !busy, !subscriptions.isEmpty else { return }
        let source = subscriptions[indexPath.row]
        guard let id = source["id"] as? String, !id.isEmpty else { return }
        let title = (source["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let sheet = UIAlertController(title: title, message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: text("Обновить", "Refresh"), style: .default) { [weak self] _ in
            self?.run("subscription.refresh", id: id)
        })
        sheet.addAction(UIAlertAction(title: text("Удалить подписку", "Remove subscription"), style: .destructive) { [weak self] _ in
            self?.confirmRemoval(id: id, title: title)
        })
        sheet.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        if let popover = sheet.popoverPresentationController {
            let anchor = tableView.cellForRow(at: indexPath) ?? tableView
            popover.sourceView = anchor
            popover.sourceRect = anchor.bounds
        }
        present(sheet, animated: true)
    }

    private func confirmRemoval(id: String, title: String?) {
        let alert = UIAlertController(title: text("Удалить подписку?", "Remove subscription?"),
                                      message: title, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Удалить", "Remove"), style: .destructive) { [weak self] _ in
            self?.run("subscription.remove", id: id)
        })
        present(alert, animated: true)
    }

    @objc private func addSubscription() {
        guard !busy else { return }
        let alert = UIAlertController(title: text("Добавить подписку", "Add subscription"),
                                      message: nil, preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = self.text("Ссылка подписки", "Subscription URL")
            field.keyboardType = .URL
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
            field.textContentType = .URL
        }
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Добавить", "Add"), style: .default) { [weak self, weak alert] _ in
            guard let self = self,
                  let url = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !url.isEmpty else { return }
            self.runAdd(url: url)
        })
        present(alert, animated: true)
    }

    private func runAdd(url: String) {
        guard !busy else { return }
        busy = true
        tableView.reloadData()
        service.call("subscription.add", payload: ["url": url]) { [weak self] result in
            guard let self = self else { return }
            self.busy = false
            switch result {
            case .success:
                self.message = nil
                self.onChange?()
                self.reloadSubscriptions()
            case .failure:
                self.message = self.text("Не удалось добавить подписку", "Could not add subscription")
                self.tableView.reloadData()
            }
        }
    }

    private func run(_ method: String, id: String) {
        guard !busy else { return }
        busy = true
        tableView.reloadData()
        service.call(method, payload: ["id": id]) { [weak self] result in
            guard let self = self else { return }
            self.busy = false
            switch result {
            case .success:
                self.message = nil
                self.onChange?()
                self.reloadSubscriptions()
            case .failure:
                self.message = self.text("Не удалось изменить подписку", "Could not update subscription")
                self.tableView.reloadData()
            }
        }
    }
}
