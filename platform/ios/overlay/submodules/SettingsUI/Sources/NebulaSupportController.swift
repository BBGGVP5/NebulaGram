import UIKit
import TelegramPresentationData
import AccountContext
import TelegramCore
import SwiftSignalKit

/// Support links and badge instructions use the same presentation theme as Telegram.
final class NebulaSupportController: UITableViewController {
    private let russian: Bool
    private let theme: PresentationTheme
    private let telegramId: Int64
    private let context: AccountContext
    private let openCommunity: (EnginePeer.Id) -> Void
    private var community = NebulaCommunityState(peer: nil, count: nil)
    private let communityDisposable = MetaDisposable()

    init(russian: Bool, theme: PresentationTheme, context: AccountContext, openCommunity: @escaping (EnginePeer.Id) -> Void) {
        self.russian = russian
        self.theme = theme
        self.telegramId = context.account.peerId.id._internalGetInt64Value()
        self.context = context
        self.openCommunity = openCommunity
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }

    override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        title = text("Поддержать NebulaGram", "Support NebulaGram")
        view.backgroundColor = theme.list.blocksBackgroundColor
        tableView.backgroundColor = theme.list.blocksBackgroundColor
        tableView.separatorColor = theme.list.itemBlocksSeparatorColor.withAlphaComponent(0.35)
        tableView.tintColor = theme.list.itemAccentColor
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 70
        loadCommunity()
        navigationController?.navigationBar.tintColor = theme.list.itemAccentColor
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = theme.list.blocksBackgroundColor
        appearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        if navigationController?.viewControllers.first === self {
            navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        }
    }
    private func loadCommunity() {
        communityDisposable.set(nebulaCommunity(context: context).start(next: { [weak self] state in
            guard let self else { return }
            self.community = state
            if let cell = self.tableView.cellForRow(at: IndexPath(row: 0, section: 1)) as? NebulaCommunityCell {
                cell.update(context: self.context, theme: self.theme, state: state, russian: self.russian)
            }
        }))
    }
    deinit { communityDisposable.dispose() }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? 3 : 1
    }
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
        if indexPath.section == 1 {
            let cell = NebulaCommunityCell(style: .subtitle, reuseIdentifier: nil)
            cell.update(context: context, theme: theme, state: community, russian: russian)
            return cell
        }
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.imageView?.image = NebulaSettingsStyle.icon(symbol: ["heart", "gift", "person.crop.circle"][indexPath.row])
        if indexPath.section == 0 {
            switch indexPath.row {
            case 0:
                cell.textLabel?.text = text("Значок поддержки", "Supporter badge")
                cell.detailTextLabel?.text = text("После подтверждения доната — значок рядом с именем, видимый пользователям NebulaGram.",
                    "After confirmation, a badge beside your name is visible to other NebulaGram users.")
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
            cell.textLabel?.text = text("Сообщество", "Community")
            cell.accessoryType = .disclosureIndicator
        }
        NebulaSettingsStyle.finish(cell, theme: theme)
        if indexPath.section == 0 && indexPath.row == 0 { cell.selectionStyle = .none }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 1 {
            if let peer = community.peer { openCommunity(peer.id) } else { loadCommunity() }
            return
        }
        if indexPath.section == 0 && indexPath.row == 0 { return }
        if indexPath.section == 0 && indexPath.row == 2 {
            UIPasteboard.general.string = String(telegramId)
            return
        }
        if let url = URL(string: "https://t.me/ngram_official") { UIApplication.shared.open(url) }
    }
}
