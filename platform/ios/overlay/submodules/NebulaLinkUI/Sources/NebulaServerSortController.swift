import UIKit

/// Two native choices with the selection mark in the trailing accessory.
final class NebulaServerSortController: UITableViewController {
    private let russian: Bool
    private let selected: String
    private let onSelect: (String) -> Void

    init(russian: Bool, selected: String, onSelect: @escaping (String) -> Void) {
        self.russian = russian
        self.selected = selected
        self.onSelect = onSelect
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = russian ? "Сортировка серверов" : "Server sorting"
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done,
                target: self, action: #selector(close))
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 2 }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        let value = indexPath.row == 0 ? "default" : "latency"
        cell.textLabel?.text = indexPath.row == 0
            ? (russian ? "По умолчанию (порядок подписки)" : "Default (subscription order)")
            : (russian ? "По задержке" : "By latency")
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.accessoryType = value == selected ? .checkmark : .none
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let value = indexPath.row == 0 ? "default" : "latency"
        let action = onSelect
        dismiss(animated: true) { action(value) }
    }
}
