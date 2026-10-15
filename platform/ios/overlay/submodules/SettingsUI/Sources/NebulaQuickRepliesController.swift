import UIKit
import TelegramPresentationData

final class NebulaQuickRepliesController: UITableViewController {
    private let store: NebulaQuickReplyStore
    private let russian: Bool
    private let theme: PresentationTheme
    private let source: String
    private let choose: (String) -> Void
    private var entries: [NebulaQuickReply] = []
    init(account: String, source: String, russian: Bool, theme: PresentationTheme, choose: @escaping (String) -> Void) {
        self.store = NebulaQuickReplyStore(account: account); self.source = source
        self.russian = russian; self.theme = theme; self.choose = choose
        super.init(style: .insetGrouped)
        title = text("Быстрые ответы", "Quick replies")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(add))
        reload()
    }
    private func reload() { entries = store.list(); tableView.reloadData() }
    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? (source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || source.utf16.count > NebulaQuickReplyStore.maximumText ? 0 : 1) : entries.count
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 1 ? text("Личные шаблоны на этом устройстве, отдельно для каждого аккаунта. Нажмите для предпросмотра, смахните для изменения. Текст без форматирования; автоматической отправки нет.",
            "Personal templates on this device, separate for each account. Tap to preview, swipe to edit. Plain text; messages are never sent automatically.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.textLabel?.numberOfLines = 0; cell.detailTextLabel?.numberOfLines = 2
        if indexPath.section == 0 { cell.textLabel?.text = text("Сохранить исходный текст", "Save source text") }
        else { cell.textLabel?.text = entries[indexPath.row].title; cell.detailTextLabel?.text = entries[indexPath.row].text }
        cell.accessoryType = .disclosureIndicator; NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 0 { edit(nil, initial: source) }
        else { choose(entries[indexPath.row].text); navigationController?.popViewController(animated: true) }
    }
    override func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        guard indexPath.section == 1 else { return nil }
        let entry = entries[indexPath.row]
        let editAction = UIContextualAction(style: .normal, title: text("Изменить", "Edit")) { [weak self] _, _, done in self?.edit(entry, initial: entry.text); done(true) }
        let delete = UIContextualAction(style: .destructive, title: text("Удалить", "Delete")) { [weak self] _, _, done in
            guard let self = self else { done(false); return }; let saved = self.store.delete(id: entry.id); self.reload(); done(saved)
        }
        let configuration = UISwipeActionsConfiguration(actions: [delete, editAction]); configuration.performsFirstActionWithFullSwipe = false; return configuration
    }
    @objc private func add() { edit(nil, initial: "") }
    private func edit(_ entry: NebulaQuickReply?, initial: String) {
        let editor = NebulaQuickReplyEditorController(entry: entry, initial: initial, russian: russian, theme: theme) { [weak self] title, body in
            guard let self = self else { return false }
            let result = self.store.save(id: entry?.id, title: title, text: body); if result { self.reload() }; return result
        }
        navigationController?.pushViewController(editor, animated: true)
    }
}

private final class NebulaQuickReplyEditorController: UIViewController {
    private let name = UITextField(), body = UITextView()
    private let russian: Bool
    private let theme: PresentationTheme
    private let save: (String, String) -> Bool
    init(entry: NebulaQuickReply?, initial: String, russian: Bool, theme: PresentationTheme, save: @escaping (String, String) -> Bool) {
        self.russian = russian; self.theme = theme; self.save = save
        super.init(nibName: nil, bundle: nil); name.text = entry?.title; body.text = initial
        title = russian ? "Быстрый ответ" : "Quick reply"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        name.placeholder = russian ? "Название" : "Title"; name.borderStyle = .roundedRect
        name.font = .preferredFont(forTextStyle: .body); name.textColor = theme.list.itemPrimaryTextColor
        name.backgroundColor = theme.list.itemBlocksBackgroundColor; name.accessibilityLabel = name.placeholder
        body.font = .preferredFont(forTextStyle: .body); body.adjustsFontForContentSizeCategory = true
        body.textColor = theme.list.itemPrimaryTextColor; body.backgroundColor = theme.list.itemBlocksBackgroundColor
        body.layer.cornerRadius = 16; body.textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        body.accessibilityLabel = russian ? "Текст ответа" : "Reply text"
        let stack = UIStackView(arrangedSubviews: [name, body]); stack.axis = .vertical; stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -16),
            name.heightAnchor.constraint(greaterThanOrEqualToConstant: 48)
        ])
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .save, target: self, action: #selector(commit))
    }
    @objc private func commit() {
        if save(name.text ?? "", body.text ?? "") { navigationController?.popViewController(animated: true); return }
        let alert = UIAlertController(title: russian ? "Не удалось сохранить" : "Could not save", message: russian ? "Заполните название (до 80 знаков) и текст (до 4096). Можно сохранить до 100 ответов." : "Enter a title (up to 80 characters) and text (up to 4096). You can save up to 100 replies.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
}
