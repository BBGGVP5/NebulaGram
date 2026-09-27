import UIKit

/// The tour has its own Russian and English copy. Telegram's full language
/// catalog remains available through its regular language settings after login.
final class NebulaWelcomeLanguageController: UITableViewController {
    private let russian: Bool
    private let selected: String
    var onSelection: ((String) -> Void)?

    init(selected: String) {
        self.selected = selected
        self.russian = selected == "ru"
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = russian ? "Язык приложения" : "App language"
        view.backgroundColor = .systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        tableView.rowHeight = 64
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 2 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        russian ? "Язык обзора" : "Tour language"
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        russian ? "После входа другие языки доступны в настройках Telegram." : "More languages are available in Telegram settings after sign-in."
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "language") ?? UITableViewCell(style: .subtitle, reuseIdentifier: "language")
        let code = indexPath.row == 0 ? "ru" : "en"
        cell.textLabel?.text = code == "ru" ? "Русский" : "English"
        cell.detailTextLabel?.text = code == "ru" ? "Russian" : "English"
        cell.accessoryType = selected == code ? .checkmark : .none
        cell.tintColor = .systemBlue
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        onSelection?(indexPath.row == 0 ? "ru" : "en")
        dismiss(animated: true)
    }
}
