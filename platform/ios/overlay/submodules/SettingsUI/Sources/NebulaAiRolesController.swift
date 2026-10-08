import UIKit
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaAiRolesController: UITableViewController {
    private let ru: Bool
    private let theme: PresentationTheme?
    private let context: AccountContext?
    private let store = NebulaAiSettings.shared.roles
    private lazy var hero = NebulaSettingsHero(symbol: "🎭", title: ru ? "Роли" : "Roles",
        summary: ru ? "Создавайте инструкции для своих задач" : "Create instructions for your tasks", context: context, theme: theme)
    private var values: [NebulaAiRole] { NebulaAiRole.builtins(russian: ru) + store.custom }
    init(russian: Bool, theme: PresentationTheme?, context: AccountContext?) {
        ru = russian; self.theme = theme; self.context = context; super.init(style: .insetGrouped); title = russian ? "Роли" : "Roles"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 88
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(add))
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true); tableView.reloadData() }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { values.count + 1 }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        let id: String
        if indexPath.row == 0 {
            cell.textLabel?.text = ru ? "Свои общие инструкции" : "Existing general instructions"
            cell.detailTextLabel?.text = NebulaAiSettings.shared.instructions
            cell.accessoryType = .detailButton; id = ""
        } else {
            let role = values[indexPath.row - 1]; id = role.id
            cell.textLabel?.text = role.emoji + "  " + role.name; cell.detailTextLabel?.text = role.instruction
            if indexPath.row > 3 { cell.accessoryType = .detailButton }
        }
        NebulaSettingsStyle.finish(cell, theme: theme)
        if id == store.selectedId { cell.backgroundColor = (theme?.list.itemAccentColor ?? .systemBlue).withAlphaComponent(0.16); cell.accessibilityTraits.insert(.selected) }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        store.selectedId = indexPath.row == 0 ? "" : values[indexPath.row - 1].id; tableView.reloadData()
    }
    override func tableView(_ tableView: UITableView, accessoryButtonTappedForRowWith indexPath: IndexPath) {
        if indexPath.row == 0 {
            navigationController?.pushViewController(NebulaAiRoleEditor(role: nil, general: true, russian: ru, theme: theme), animated: true)
        } else { edit(values[indexPath.row - 1]) }
    }
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool { indexPath.row > 3 }
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        guard editingStyle == .delete else { return }
        do { try store.remove(values[indexPath.row - 1].id); tableView.reloadData() } catch { report() }
    }
    @objc private func add() { edit(nil) }
    private func edit(_ original: NebulaAiRole?) {
        navigationController?.pushViewController(NebulaAiRoleEditor(role: original, general: false, russian: ru, theme: theme), animated: true)
    }
    private func report() {
        let alert = UIAlertController(title: ru ? "Проверьте название и инструкцию" : "Check the name and instruction", message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
    }
}

private final class NebulaAiRoleEditor: UITableViewController {
    private let role: NebulaAiRole?
    private let general: Bool
    private let ru: Bool
    private let theme: PresentationTheme?
    private let nameField = UITextField()
    private let emojiField = UITextField()
    private let instruction = UITextView()
    init(role: NebulaAiRole?, general: Bool, russian: Bool, theme: PresentationTheme?) {
        self.role = role; self.general = general; ru = russian; self.theme = theme
        super.init(style: .insetGrouped)
        title = russian ? (general ? "Общие инструкции" : "Своя роль") : (general ? "General instructions" : "Custom role")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.keyboardDismissMode = .interactive
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        nameField.text = role?.name; emojiField.text = role?.emoji ?? "🤖"
        instruction.text = general ? NebulaAiSettings.shared.instructions : role?.instruction ?? ""
        for field in [nameField, emojiField] {
            field.font = .preferredFont(forTextStyle: .body); field.adjustsFontForContentSizeCategory = true
            field.textColor = theme?.list.itemPrimaryTextColor ?? .label
        }
        nameField.placeholder = ru ? "Название роли" : "Role name"; nameField.accessibilityLabel = nameField.placeholder
        emojiField.accessibilityLabel = ru ? "Эмодзи" : "Emoji"
        instruction.font = .preferredFont(forTextStyle: .body); instruction.adjustsFontForContentSizeCategory = true
        instruction.textColor = theme?.list.itemPrimaryTextColor ?? .label; instruction.backgroundColor = .clear
        instruction.accessibilityLabel = ru ? "Инструкция" : "Instruction"
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: ru ? "Сохранить" : "Save", style: .done, target: self, action: #selector(save))
    }
    override func numberOfSections(in tableView: UITableView) -> Int { general ? 1 : 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { !general && section == 0 ? 2 : 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        general || section == 1 ? (ru ? "Инструкция" : "Instruction") : (ru ? "Название и значок" : "Name and icon")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme); cell.selectionStyle = .none
        let editor: UIView = general || indexPath.section == 1 ? instruction : (indexPath.row == 0 ? nameField : emojiField)
        editor.translatesAutoresizingMaskIntoConstraints = false; cell.contentView.addSubview(editor)
        NSLayoutConstraint.activate([editor.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 16), editor.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -16), editor.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 14), editor.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -14), editor.heightAnchor.constraint(greaterThanOrEqualToConstant: editor === instruction ? 240 : 32)])
        return cell
    }
    @objc private func save() {
        let text = instruction.text ?? ""
        do {
            guard text.count <= 8192 else { throw NebulaAiServices.Failure.invalidConnection }
            if general { NebulaAiSettings.shared.instructions = text }
            else { try NebulaAiSettings.shared.roles.save(NebulaAiRole(id: role?.id ?? UUID().uuidString, name: nameField.text ?? "", emoji: emojiField.text ?? "", instruction: text)) }
            navigationController?.popViewController(animated: true)
        } catch {
            let alert = UIAlertController(title: ru ? "Не удалось сохранить роль" : "Could not save the role", message: ru ? "Проверьте название и инструкцию (до 8192 символов)." : "Check the name and instruction (up to 8192 characters).", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true)
        }
    }
}
