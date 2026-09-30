import UIKit

/// One native choice sheet: wrapping labels, a trailing selection mark and iPad-safe presentation.
final class NebulaChoiceController: UITableViewController {
    private let choices: [String]
    private let selected: Int?
    private let detail: String?
    private let choose: (Int) -> Void
    private let ru: Bool
    private var selectionIndicatorVisible = true

    init(title: String, choices: [String], selected: Int?, detail: String?, russian: Bool, choose: @escaping (Int) -> Void) {
        self.choices = choices; self.selected = selected; self.detail = detail; self.choose = choose; self.ru = russian
        super.init(style: .insetGrouped)
        self.title = title
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: ru ? "Отмена" : "Cancel", style: .plain, target: self, action: #selector(close))
    }
    @objc private func close() { dismiss(animated: true) }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { choices.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? { detail }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        cell.textLabel?.text = choices[indexPath.row]
        cell.accessoryType = selectionIndicatorVisible && indexPath.row == selected ? .checkmark : .none
        if indexPath.row == selected { cell.accessibilityTraits.insert(.selected) }
        NebulaSettingsStyle.finish(cell)
        if !selectionIndicatorVisible && indexPath.row == selected {
            cell.backgroundColor = view.tintColor.withAlphaComponent(0.18)
            cell.textLabel?.textColor = view.tintColor
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let action = choose
        dismiss(animated: true) { action(indexPath.row) }
    }
    static func show(from host: UIViewController, title: String, choices: [String], selected: Int? = nil,
                     detail: String? = nil, russian: Bool, selectionIndicatorVisible: Bool = true, choose: @escaping (Int) -> Void) {
        guard host.presentedViewController == nil else { return }
        let controller = NebulaChoiceController(title: title, choices: choices, selected: selected, detail: detail, russian: russian, choose: choose)
        controller.selectionIndicatorVisible = selectionIndicatorVisible
        let navigation = UINavigationController(rootViewController: controller)
        navigation.modalPresentationStyle = host.traitCollection.userInterfaceIdiom == .pad ? .formSheet : .pageSheet
        navigation.preferredContentSize = CGSize(width: 480, height: min(600, CGFloat(choices.count) * 64 + 140))
        if #available(iOS 15.0, *) {
            navigation.sheetPresentationController?.detents = [.medium(), .large()]
            navigation.sheetPresentationController?.prefersGrabberVisible = true
            navigation.sheetPresentationController?.preferredCornerRadius = 28
        }
        host.present(navigation, animated: true)
    }
}
