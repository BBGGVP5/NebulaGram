import UIKit

/// Only icon names packaged in Telegram/BUILD and AlternateIcons.plist appear here.
public final class NebulaIconController: UITableViewController {
    private let russian: Bool
    private let names = ["Blue", "Ocean", "Aurora", "Sunset", "Graphite", "Pearl", "Ink", "Paper", "Mint", "Lavender", "Tangerine", "Rose", "Orbit", "Blueprint", "Nova", "Monogram"]

    public init(russian: Bool) {
        self.russian = russian
        super.init(style: .insetGrouped)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    public override func viewDidLoad() {
        super.viewDidLoad()
        title = russian ? "Иконка приложения" : "App Icon"
        view.backgroundColor = UIColor.systemBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        tableView.rowHeight = 72
    }

    @objc private func close() { dismiss(animated: true) }

    public override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? 1 : names.count
    }
    public override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? nil : (russian ? "Иконки NebulaGram" : "NebulaGram Icons")
    }
    public override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "icon") ?? UITableViewCell(style: .default, reuseIdentifier: "icon")
        let name = indexPath.section == 0 ? nil : "Nebula\(names[indexPath.row])Icon"
        cell.textLabel?.text = name == nil ? (russian ? "Стандартная" : "Default") : names[indexPath.row]
        cell.textLabel?.font = UIFont.systemFont(ofSize: 17)
        cell.accessoryType = UIApplication.shared.alternateIconName == name ? .checkmark : .none
        cell.selectionStyle = .default
        cell.imageView?.image = nil
        if let name = name, let path = Bundle.main.path(forResource: name + "@3x", ofType: "png"), let image = UIImage(contentsOfFile: path) {
            let format = UIGraphicsImageRendererFormat.default()
            format.scale = UIScreen.main.scale
            cell.imageView?.image = UIGraphicsImageRenderer(size: CGSize(width: 48, height: 48), format: format).image { _ in
                UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 48, height: 48), cornerRadius: 11).addClip()
                image.draw(in: CGRect(x: 0, y: 0, width: 48, height: 48))
            }
        }
        return cell
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard UIApplication.shared.supportsAlternateIcons else {
            showError(russian ? "На этом устройстве смена иконки недоступна." : "Changing the icon is unavailable on this device.")
            return
        }
        let name = indexPath.section == 0 ? nil : "Nebula\(names[indexPath.row])Icon"
        UIApplication.shared.setAlternateIconName(name) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error { self?.showError(error.localizedDescription) }
                else { self?.tableView.reloadData() }
            }
        }
    }
    private func showError(_ message: String) {
        let alert = UIAlertController(title: russian ? "Не удалось сменить иконку" : "Could not change icon", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
