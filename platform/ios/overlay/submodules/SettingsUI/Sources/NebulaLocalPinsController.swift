import UIKit
import TelegramCore
import AccountContext
import TelegramPresentationData
import Display

final class NebulaLocalPinsController: UITableViewController {
    private let context: AccountContext
    private let russian: Bool
    private var theme: PresentationTheme { context.sharedContext.currentPresentationData.with { $0 }.theme }
    init(context: AccountContext, russian: Bool) {
        self.context = context; self.russian = russian; super.init(style: .insetGrouped)
        title = text("Локальные закрепления", "Local pins")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }
    override func viewDidLoad() { super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self); tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 68 }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); NebulaSettingsStyle.apply(theme: theme, to: self); tableView.reloadData() }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 2 }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        text("Удерживайте чат → «Закрепить на устройстве». Сначала идут закрепления Telegram, затем локальные, затем остальные чаты. Порядок главного списка, архива и каждой папки независим. До 1000 закреплений на аккаунт; секретные чаты не поддерживаются. Порядок не отправляется на сервер. Выключение сохраняет список.", "Hold a chat → Pin on this device. Telegram pins come first, then local pins, then remaining chats. The main list, archive and each folder have independent orders. Up to 1000 pins per account; secret chats are unsupported. The order is not sent to the server. Turning this off keeps the list.")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        let state = NebulaLocalPins.snapshot(account: context.account)
        cell.textLabel?.font = .preferredFont(forTextStyle: .body); cell.textLabel?.adjustsFontForContentSizeCategory = true; cell.textLabel?.numberOfLines = 0
        cell.backgroundColor = theme.list.itemBlocksBackgroundColor; cell.textLabel?.textColor = theme.list.itemPrimaryTextColor; cell.detailTextLabel?.textColor = theme.list.itemSecondaryTextColor
        if path.row == 0 {
            cell.textLabel?.text = text("Показывать локальные закрепления", "Show local pins")
            let toggle = NebulaSwitchControl(); toggle.isOn = state.enabled; toggle.addTarget(self, action: #selector(changed(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
        } else { cell.textLabel?.text = text("Очистить локальные закрепления", "Clear local pins"); cell.detailTextLabel?.text = String(state.count) }
        return cell
    }
    @objc private func changed(_ toggle: NebulaSwitchControl) { NebulaLocalPins.setEnabled(account: context.account, value: toggle.isOn) }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true); guard path.row == 1 else { return }
        let alert = UIAlertController(title: text("Очистить закрепления на устройстве?", "Clear pins on this device?"), message: text("Закрепления Telegram и сами чаты сохранятся.", "Telegram pins and the chats remain."), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Очистить", "Clear"), style: .destructive) { [weak self] _ in guard let self else { return }; NebulaLocalPins.clear(account: self.context.account); self.tableView.reloadData() })
        present(alert, animated: true)
    }
}
