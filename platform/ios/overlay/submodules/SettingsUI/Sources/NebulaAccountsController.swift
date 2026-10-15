import UIKit
import NebulaSettingsContract
import AccountContext
import TelegramCore
import SwiftSignalKit
import TelegramPresentationData

final class NebulaAccountsController: UITableViewController, UISearchResultsUpdating {
    private let context: AccountContext
    private var theme: PresentationTheme
    private var russian: Bool
    private let search = UISearchController(searchResultsController: nil)
    private let accountsDisposable = MetaDisposable()
    private var privacyObservation: SettingsObservation?
    private var accounts: [AccountWithInfo] = []
    private var visible: [AccountWithInfo] = []
    // Presentation order stays device-local; no account/session data is exported.
    private var alphabetical = UserDefaults.standard.bool(forKey: "Nebula.Accounts.Alphabetical")

    init(context: AccountContext) {
        self.context = context
        let data = context.sharedContext.currentPresentationData.with { $0 }
        theme = data.theme
        russian = data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(style: .insetGrouped)
        title = text("Аккаунты", "Accounts")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { accountsDisposable.dispose(); privacyObservation?.cancel() }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }

    override func viewDidLoad() {
        super.viewDidLoad()
        search.searchResultsUpdater = self
        search.obscuresBackgroundDuringPresentation = false
        search.searchBar.placeholder = text("Имя или @username", "Name or @username")
        navigationItem.searchController = search
        navigationItem.hidesSearchBarWhenScrolling = false
        definesPresentationContext = true
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(addAccount))
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 64
        accountsDisposable.set((context.sharedContext.activeAccountsWithInfo |> deliverOnMainQueue).start(next: { [weak self] value in
            guard let self else { return }
            self.accounts = value.accounts
            self.rebuild()
        }))
        privacyObservation = NebulaSettingsStore.shared.observe { [weak self] in self?.rebuild() }
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let data = context.sharedContext.currentPresentationData.with { $0 }
        theme = data.theme
        russian = data.strings.baseLanguageCode.hasPrefix("ru")
        title = text("Аккаунты", "Accounts")
        search.searchBar.placeholder = text("Имя или @username", "Name or @username")
        NebulaSettingsStyle.apply(theme: theme, to: self)
        rebuild()
    }
    @objc private func close() { dismiss(animated: true) }
    @objc private func addAccount() {
        let shared = context.sharedContext
        let testing = context.account.testingEnvironment
        dismiss(animated: true) { shared.beginNewAuth(testingEnvironment: testing) }
    }
    func updateSearchResults(for searchController: UISearchController) { rebuild() }
    private func rebuild() {
        let masked = NebulaPresentationPrivacy.enabled
        search.searchBar.isUserInteractionEnabled = !masked
        if masked && !(search.searchBar.text ?? "").isEmpty { search.searchBar.text = nil; view.endEditing(true) }
        var query = (search.searchBar.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if query.hasPrefix("@") { query.removeFirst() }
        visible = accounts.filter { entry in
            query.isEmpty || entry.peer.compactDisplayTitle.localizedCaseInsensitiveContains(query)
                || (entry.peer.addressName ?? "").localizedCaseInsensitiveContains(query)
        }
        if alphabetical {
            visible = visible.enumerated().sorted { lhs, rhs in
                let comparison = lhs.element.peer.compactDisplayTitle.localizedCaseInsensitiveCompare(rhs.element.peer.compactDisplayTitle)
                return comparison == .orderedSame ? lhs.offset < rhs.offset : comparison == .orderedAscending
            }.map { $0.element }
        }
        tableView.reloadData()
    }
    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 1 : max(1, visible.count) }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? text("Порядок", "Order") : text("Аккаунты", "Accounts") + " · \(visible.count)"
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 1 ? text("Лимита числа аккаунтов в NebulaGram нет. Уведомления остаются включёнными для всех активных аккаунтов; больше подключений использует больше памяти и заряда.", "NebulaGram has no account-count limit. Notifications remain enabled for all active accounts; more connections use more memory and battery.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "account") ?? UITableViewCell(style: .subtitle, reuseIdentifier: "account")
        cell.textLabel?.textColor = theme.list.itemPrimaryTextColor
        cell.detailTextLabel?.textColor = theme.list.itemSecondaryTextColor
        cell.backgroundColor = theme.list.itemBlocksBackgroundColor
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.numberOfLines = 0
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.accessoryType = .none
        cell.detailTextLabel?.text = nil
        cell.selectionStyle = .default
        if path.section == 0 {
            cell.textLabel?.text = alphabetical ? text("По имени", "By name") : text("Порядок Telegram", "Telegram order")
            cell.accessoryType = .disclosureIndicator
        } else if visible.isEmpty {
            cell.textLabel?.text = text("Аккаунты не найдены", "No accounts found")
            cell.selectionStyle = .none
        } else {
            let entry = visible[path.row]
            cell.textLabel?.text = NebulaPresentationPrivacy.enabled ? NebulaPresentationPrivacy.accountLabel(position: path.row, russian: russian) : entry.peer.compactDisplayTitle
            var details: [String] = []
            if entry.account.id == context.account.id { details.append(text("Текущий аккаунт", "Current account")); cell.accessoryType = .checkmark }
            if !NebulaPresentationPrivacy.enabled, let username = entry.peer.addressName, !username.isEmpty { details.append("@" + username) }
            cell.detailTextLabel?.text = details.joined(separator: " · ")
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
        if path.section == 0 {
            let picker = UIAlertController(title: text("Порядок аккаунтов", "Account order"), message: nil, preferredStyle: .actionSheet)
            for value in [false, true] {
                picker.addAction(UIAlertAction(title: value ? text("По имени", "By name") : text("Порядок Telegram", "Telegram order"), style: .default) { [weak self] _ in
                    guard let self else { return }
                    self.alphabetical = value
                    UserDefaults.standard.set(value, forKey: "Nebula.Accounts.Alphabetical")
                    self.rebuild()
                })
            }
            picker.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
            if let popover = picker.popoverPresentationController {
                popover.sourceView = tableView
                popover.sourceRect = tableView.rectForRow(at: path)
            }
            present(picker, animated: true)
        } else if visible.indices.contains(path.row) {
            let entry = visible[path.row]
            guard entry.account.id != context.account.id,
                  accounts.contains(where: { $0.account.id == entry.account.id }) else { return }
            let shared = context.sharedContext
            dismiss(animated: true) { shared.switchToAccount(id: entry.account.id, fromSettingsController: nil, withChatListController: nil) }
        }
    }
}
