import UIKit
import TelegramPresentationData

/// Support links and badge instructions use the same presentation theme as Telegram.
final class NebulaSupportController: UITableViewController {
    private let russian: Bool
    private let theme: PresentationTheme
    private let telegramId: Int64

    init(russian: Bool, theme: PresentationTheme, telegramId: Int64) {
        self.russian = russian
        self.theme = theme
        self.telegramId = telegramId
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = text("Поддержать NebulaGram", "Support NebulaGram")
        view.backgroundColor = theme.list.blocksBackgroundColor
        tableView.backgroundColor = theme.list.blocksBackgroundColor
        tableView.separatorColor = theme.list.itemBlocksSeparatorColor.withAlphaComponent(0.35)
        tableView.tintColor = theme.list.itemAccentColor
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 70
        navigationController?.navigationBar.tintColor = theme.list.itemAccentColor
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = theme.list.blocksBackgroundColor
        appearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 3 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? text("Поддержка за донат", "Donation support") : text("Поддержка без доната", "Support without donating")
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 0 ? text("Для выдачи значка передайте команде подтверждение платежа и ваш Telegram ID.",
                            "Send the team your payment confirmation and Telegram ID to receive a badge.") : nil
    }
    override func tableView(_ tableView: UITableView, willDisplayHeaderView view: UIView, forSection section: Int) {
        (view as? UITableViewHeaderFooterView)?.textLabel?.textColor = theme.list.itemAccentColor
    }
    override func tableView(_ tableView: UITableView, willDisplayFooterView view: UIView, forSection section: Int) {
        (view as? UITableViewHeaderFooterView)?.textLabel?.textColor = theme.list.itemSecondaryTextColor
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        let symbols = indexPath.section == 0 ? ["heart", "gift", "person.crop.circle"] : ["bubble.left", "questionmark.bubble", "link"]
        cell.imageView?.image = NebulaSettingsStyle.icon(symbol: symbols[indexPath.row])
        if indexPath.section == 0 {
            switch indexPath.row {
            case 0:
                cell.textLabel?.text = text("Значок поддержки", "Supporter badge")
                cell.detailTextLabel?.text = text("После подтверждения доната команда выдаст значок. Он появится после премиум-эмодзи рядом с именем и будет виден другим пользователям NebulaGram.",
                    "After confirming your donation, the team grants a badge. It appears after your premium emoji beside your name and is visible to other NebulaGram users.")
                cell.selectionStyle = .none
            case 1:
                cell.textLabel?.text = text("Узнать варианты поддержки", "Ask about support options")
                cell.detailTextLabel?.text = text("Официальный канал NebulaGram", "Official NebulaGram channel")
                cell.accessoryType = .disclosureIndicator
            default:
                cell.textLabel?.text = text("Мой Telegram ID", "My Telegram ID")
                cell.detailTextLabel?.text = String(telegramId)
            }
        } else {
            cell.textLabel?.text = [text("Канал проекта", "Project channel"), text("Сообщить об ошибке или предложить идею", "Report a bug or suggest an idea"), text("Участвовать в разработке", "Contribute to development")][indexPath.row]
            cell.accessoryType = .disclosureIndicator
        }
        NebulaSettingsStyle.finish(cell, theme: theme)
        if indexPath.section == 0 && indexPath.row == 0 { cell.selectionStyle = .none }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 0 && indexPath.row == 0 { return }
        if indexPath.section == 0 && indexPath.row == 2 {
            UIPasteboard.general.string = String(telegramId)
            return
        }
        let address: String
        if indexPath.section == 0 || indexPath.row == 0 { address = "https://t.me/ngram_official" }
        else if indexPath.row == 1 { address = "https://github.com/BBGGVP5/NebulaGram/issues/new" }
        else { address = "https://github.com/BBGGVP5/NebulaGram" }
        if let url = URL(string: address) { UIApplication.shared.open(url) }
    }
}
