import Foundation
import UIKit
import NebulaSettingsContract

final class NebulaAiHistoryController: UITableViewController {
    private let ru: Bool
    private let history = NebulaAiHistory.shared
    private var records: [NebulaAiHistoryEntry] = []

    init(russian: Bool) {
        self.ru = russian
        super.init(style: .insetGrouped)
        title = russian ? "История ИИ" : "AI history"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }

    override func viewDidLoad() {
        super.viewDidLoad()
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: text("Очистить", "Clear"), style: .plain, target: self, action: #selector(confirmClear))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 120
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        records = history.entries()
        let hasChats = NebulaAiChats.shared.list().contains { !$0.turns.isEmpty }
        navigationItem.rightBarButtonItem?.isEnabled = !records.isEmpty || hasChats
        tableView.reloadData()
    }

    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { records.count + 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        text("Только на этом устройстве", "Only on this device")
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.selectionStyle = .none
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.numberOfLines = 0
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.detailTextLabel?.textColor = .secondaryLabel
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        if indexPath.row == 0 {
            cell.textLabel?.text = text("Сохранять историю ИИ", "Save AI history")
            cell.detailTextLabel?.text = text("Запросы и чаты сохраняются только после включения этого переключателя.",
                                              "Requests and chats are saved only after this switch is enabled.")
            let toggle = UISwitch()
            toggle.isOn = history.isEnabled
            toggle.addTarget(self, action: #selector(toggleHistory(_:)), for: .valueChanged)
            cell.accessoryView = toggle
        } else {
            let entry = records[indexPath.row - 1]
            cell.textLabel?.text = entry.input
            cell.detailTextLabel?.text = "\(entry.provider) · \(DateFormatter.localizedString(from: entry.timestamp, dateStyle: .short, timeStyle: .short))\n\(entry.output)"
        }
        return cell
    }

    @objc private func toggleHistory(_ toggle: UISwitch) { history.isEnabled = toggle.isOn }

    @objc private func confirmClear() {
        let alert = UIAlertController(title: text("Очистить историю ИИ?", "Clear AI history?"),
            message: text("Все сохранённые чаты, запросы и ответы на этом устройстве будут удалены.",
                          "All saved chats, requests and answers on this device will be removed."), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Очистить", "Clear"), style: .destructive) { [weak self] _ in
            guard let self = self else { return }
            self.history.clear()
            NebulaAiChats.shared.clear()
            self.records.removeAll()
            self.navigationItem.rightBarButtonItem?.isEnabled = false
            self.tableView.reloadData()
        })
        present(alert, animated: true)
    }
}
