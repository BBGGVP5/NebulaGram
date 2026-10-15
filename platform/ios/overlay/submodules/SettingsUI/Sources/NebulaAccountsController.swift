import UIKit
import NebulaSettingsContract
import AccountContext
import TelegramCore
import SwiftSignalKit
import TelegramPresentationData
import AvatarNode

private final class NebulaAccountCell: UITableViewCell {
    private let avatar = AvatarNode(font: .systemFont(ofSize: 20, weight: .medium))
    private let avatarHolder = UIView()
    private let hiddenAvatar = UIImageView(image: UIImage(systemName: "person.crop.circle.fill"))
    private let name = UILabel()
    private let detail = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        avatarHolder.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(avatarHolder)
        avatarHolder.addSubview(avatar.view)
        hiddenAvatar.contentMode = .scaleAspectFit
        hiddenAvatar.isAccessibilityElement = false
        avatarHolder.addSubview(hiddenAvatar)
        name.font = .preferredFont(forTextStyle: .headline)
        detail.font = .preferredFont(forTextStyle: .subheadline)
        for label in [name, detail] { label.numberOfLines = 0; label.adjustsFontForContentSizeCategory = true }
        let labels = UIStackView(arrangedSubviews: [name, detail])
        labels.axis = .vertical; labels.spacing = 5; labels.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(labels)
        NSLayoutConstraint.activate([
            contentView.heightAnchor.constraint(greaterThanOrEqualToConstant: 80),
            avatarHolder.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            avatarHolder.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            avatarHolder.widthAnchor.constraint(equalToConstant: 48), avatarHolder.heightAnchor.constraint(equalToConstant: 48),
            labels.leadingAnchor.constraint(equalTo: avatarHolder.trailingAnchor, constant: 16),
            labels.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            labels.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            labels.topAnchor.constraint(greaterThanOrEqualTo: contentView.topAnchor, constant: 16),
            labels.bottomAnchor.constraint(lessThanOrEqualTo: contentView.bottomAnchor, constant: -16)
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        avatar.frame = avatarHolder.bounds
        hiddenAvatar.frame = avatarHolder.bounds
    }
    override func prepareForReuse() {
        super.prepareForReuse()
        avatar.isHidden = true; hiddenAvatar.isHidden = true
        name.text = nil; detail.text = nil; accessibilityLabel = nil
    }
    func bind(context: AccountContext, entry: AccountWithInfo, theme: PresentationTheme, title: String, subtitle: String, current: Bool, masked: Bool) {
        name.text = title; name.textColor = theme.list.itemPrimaryTextColor
        detail.text = subtitle; detail.isHidden = subtitle.isEmpty
        detail.textColor = current ? theme.list.itemAccentColor : theme.list.itemSecondaryTextColor
        backgroundColor = theme.list.itemBlocksBackgroundColor
        tintColor = theme.list.itemAccentColor
        accessoryType = current ? .checkmark : .none
        avatar.isHidden = masked; hiddenAvatar.isHidden = !masked
        hiddenAvatar.tintColor = theme.list.itemSecondaryTextColor
        if !masked {
            avatar.setPeer(context: context, account: entry.account, theme: theme, peer: entry.peer,
                           displayDimensions: CGSize(width: 48, height: 48))
        }
        isAccessibilityElement = true
        accessibilityLabel = title + (subtitle.isEmpty ? "" : ", " + subtitle)
        accessibilityTraits = current ? [.button, .selected] : .button
    }
}

final class NebulaAccountsController: UITableViewController, UISearchResultsUpdating {
    private let context: AccountContext
    private var theme: PresentationTheme
    private var russian: Bool
    private let search = UISearchController(searchResultsController: nil)
    private var sortButton: UIBarButtonItem?
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
        search.searchBar.placeholder = text("Найти аккаунт", "Find account")
        navigationItem.searchController = search
        navigationItem.hidesSearchBarWhenScrolling = false
        definesPresentationContext = true
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        let sorting = UIBarButtonItem(image: UIImage(systemName: "arrow.up.arrow.down"), style: .plain, target: self, action: #selector(chooseOrder))
        sorting.accessibilityLabel = text("Порядок аккаунтов", "Account order"); sortButton = sorting
        navigationItem.rightBarButtonItems = [UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(addAccount)), sorting]
        tableView.register(NebulaAccountCell.self, forCellReuseIdentifier: "account")
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 80
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
        search.searchBar.placeholder = text("Найти аккаунт", "Find account")
        sortButton?.accessibilityLabel = text("Порядок аккаунтов", "Account order")
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
    override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { max(1, visible.count) }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        text("Аккаунты", "Accounts") + " · \(visible.count)"
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        text("Уведомления работают для всех аккаунтов. Добавить новый можно кнопкой + вверху. Больше подключений — больше расход памяти и заряда.", "All accounts receive notifications. Add another with + above. More connections use more memory and battery.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        if visible.indices.contains(path.row), let cell = tableView.dequeueReusableCell(withIdentifier: "account", for: path) as? NebulaAccountCell {
            let entry = visible[path.row]
            let masked = NebulaPresentationPrivacy.enabled
            let title = masked ? NebulaPresentationPrivacy.accountLabel(position: path.row, russian: russian) : entry.peer.compactDisplayTitle
            var details: [String] = []
            if entry.account.id == context.account.id { details.append(text("Текущий аккаунт", "Current account")) }
            if !NebulaPresentationPrivacy.enabled, let username = entry.peer.addressName, !username.isEmpty { details.append("@" + username) }
            cell.bind(context: context, entry: entry, theme: theme, title: title, subtitle: details.joined(separator: " · "), current: entry.account.id == context.account.id, masked: masked)
            return cell
        }
        let cell = tableView.dequeueReusableCell(withIdentifier: "empty") ?? UITableViewCell(style: .default, reuseIdentifier: "empty")
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
        cell.textLabel?.text = text("Аккаунты не найдены", "No accounts found")
        cell.selectionStyle = .none
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
        if visible.indices.contains(path.row) {
            let entry = visible[path.row]
            guard entry.account.id != context.account.id,
                  accounts.contains(where: { $0.account.id == entry.account.id }) else { return }
            let shared = context.sharedContext
            dismiss(animated: true) { shared.switchToAccount(id: entry.account.id, fromSettingsController: nil, withChatListController: nil) }
        }
    }
    @objc private func chooseOrder() {
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
            popover.barButtonItem = sortButton
        }
        present(picker, animated: true)
    }
}
