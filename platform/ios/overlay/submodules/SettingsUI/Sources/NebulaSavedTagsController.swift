import UIKit
import AccountContext
import TelegramCore
import Postbox
import NebulaSettingsContract
import TelegramPresentationData

public final class NebulaSavedTagsController: UITableViewController, UISearchResultsUpdating {
    private let context: AccountContext, ru: Bool, theme: PresentationTheme
    private let messageId: Int32?, labelId: String?
    private var store: NebulaSavedTags?, labels: [NebulaSavedTags.Label] = [], messages: [Int32] = [], failure = false, query = ""
    private lazy var hero = NebulaSettingsHero(symbol: "🏷️", title: text("Метки", "Labels"), summary: messageId == nil ? text("Находите сохранённые сообщения по своим меткам", "Find saved messages using your own labels") : text("Нажмите, чтобы назначить или снять метку", "Tap to assign or remove a label"), context: context, theme: theme)
    public init(context: AccountContext, messageId: Int32? = nil, labelId: String? = nil) {
        self.context = context; self.messageId = messageId; self.labelId = labelId
        let presentation = context.sharedContext.currentPresentationData.with { $0 }; ru = presentation.strings.baseLanguageCode.hasPrefix("ru"); theme = presentation.theme
        super.init(style: .insetGrouped); title = text("Метки в Избранном", "Saved Messages labels"); store = try? NebulaSavedTags(accountId: String(context.account.peerId.toInt64()))
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    public override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self); tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 56
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        if labelId == nil { navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(create)); let search = UISearchController(searchResultsController: nil); search.searchResultsUpdater = self; search.obscuresBackgroundDuringPresentation = false; navigationItem.searchController = search; definesPresentationContext = true }
    }
    public override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); reload(); hero.setPageVisible(true) }
    public override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    public override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    public func updateSearchResults(for searchController: UISearchController) { query = searchController.searchBar.text ?? ""; tableView.reloadData() }
    private var visibleLabels: [NebulaSavedTags.Label] { labels.filter { query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) } }
    private func reload() { do { guard let store else { throw NebulaSavedTags.Failure.invalidAccount }; labels = try store.labels; if let labelId { messages = try store.messages(label: labelId) }; failure = false } catch { failure = true }; tableView.reloadData() }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { failure ? 1 : max(1, labelId == nil ? visibleLabels.count : messages.count) }
    public override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? { text("Метки остаются на этом устройстве и разделены по аккаунтам. Хранятся только названия и номера сообщений. Сообщения и медиа не копируются. Удерживайте метку для переименования или удаления; смахните строку сообщения, чтобы снять связь.", "Labels stay on this device and are separate for each account. Only names and message IDs are stored; text and media are not copied. Hold a label to rename/delete it; swipe a message row to remove a link.") }
    public override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme); cell.textLabel?.numberOfLines = 0
        if failure { cell.textLabel?.text = text("Не удалось прочитать метки. Данные не перезаписаны.", "Could not read labels. Existing data has not been overwritten."); return cell }
        if let _ = labelId { cell.textLabel?.text = messages.isEmpty ? text("Пока нет сообщений с метками", "No labelled messages yet") : text("Сообщение № ", "Message #") + String(messages[path.row]); cell.accessoryType = messages.isEmpty ? .none : .disclosureIndicator; return cell }
        guard !visibleLabels.isEmpty else { cell.textLabel?.text = text("Создайте первую метку кнопкой +", "Create your first label with +"); return cell }
        let label = visibleLabels[path.row]; cell.textLabel?.text = label.name
        if let messageId {
            let assigned = (try? store?.assigned(message: messageId, label: label.id)) == true; cell.detailTextLabel?.text = assigned ? text("Назначена", "Assigned") : text("Нажмите, чтобы назначить", "Tap to assign"); if assigned { cell.backgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.15) }
        } else { cell.detailTextLabel?.text = String((try? store?.messages(label: label.id))?.count ?? 0) + text(" сообщений", " messages"); cell.accessoryType = .disclosureIndicator }
        return cell
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true); guard !failure, let store else { return }
        if labelId != nil {
            guard messages.indices.contains(path.row) else { return }; let id = MessageId(peerId: context.account.peerId, namespace: Namespaces.Message.Cloud, id: messages[path.row]); dismiss(animated: true) { [context] in context.sharedContext.navigateToChat(accountId: context.account.id, peerId: context.account.peerId, messageId: id) }; return
        }
        guard visibleLabels.indices.contains(path.row) else { return }; let label = visibleLabels[path.row]
        if let messageId { do { try store.toggle(message: messageId, label: label.id); reload() } catch { report() } }
        else { navigationController?.pushViewController(NebulaSavedTagsController(context: context, labelId: label.id), animated: true) }
    }
    public override func tableView(_ tableView: UITableView, contextMenuConfigurationForRowAt path: IndexPath, point: CGPoint) -> UIContextMenuConfiguration? {
        guard labelId == nil, !failure, visibleLabels.indices.contains(path.row) else { return nil }; let label = visibleLabels[path.row]
        return UIContextMenuConfiguration(identifier: nil, previewProvider: nil) { [weak self] _ in
            guard let self else { return nil }; return UIMenu(children: [UIAction(title: self.text("Переименовать", "Rename")) { [weak self] _ in self?.edit(label) }, UIAction(title: self.text("Удалить метку", "Delete label"), attributes: .destructive) { [weak self] _ in self?.remove(label) }])
        }
    }
    public override func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt path: IndexPath) -> UISwipeActionsConfiguration? {
        guard let labelId, !failure, messages.indices.contains(path.row) else { return nil }; let id = messages[path.row]
        return UISwipeActionsConfiguration(actions: [UIContextualAction(style: .destructive, title: text("Снять метку", "Remove label")) { [weak self] _, _, finish in do { try self?.store?.toggle(message: id, label: labelId); self?.reload(); finish(true) } catch { self?.report(); finish(false) } }])
    }
    @objc private func create() { edit(nil) }
    private func edit(_ label: NebulaSavedTags.Label?) {
        let alert = UIAlertController(title: label == nil ? text("Новая метка", "New label") : text("Переименовать", "Rename"), message: text("До 32 символов", "Up to 32 characters"), preferredStyle: .alert); alert.addTextField { $0.text = label?.name }
        alert.addAction(UIAlertAction(title: text("Сохранить", "Save"), style: .default) { [weak self] _ in
            guard let self, let store = self.store else { return }; do { let name = alert.textFields?.first?.text ?? ""; if let label { try store.rename(id: label.id, name: name) } else { let id = try store.create(name); if let messageId = self.messageId { try store.toggle(message: messageId, label: id) } }; self.reload() } catch { self.report() }
        }); alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel)); present(alert, animated: true)
    }
    private func remove(_ label: NebulaSavedTags.Label) {
        let alert = UIAlertController(title: text("Удалить метку?", "Delete label?"), message: text("Сообщения останутся в Избранном.", "Messages stay in Saved Messages."), preferredStyle: .alert); alert.addAction(UIAlertAction(title: text("Удалить", "Delete"), style: .destructive) { [weak self] _ in do { try self?.store?.remove(id: label.id); self?.reload() } catch { self?.report() } }); alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel)); present(alert, animated: true)
    }
    private func report() { let alert = UIAlertController(title: text("Не удалось сохранить метку", "Could not save label"), message: text("Проверьте название, отсутствие дубликатов и лимит: 64 метки, 8 на сообщение.", "Check the name, duplicates and limits: 64 labels, 8 per message."), preferredStyle: .alert); alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true) }
    @objc private func close() { dismiss(animated: true) }
}
