import UIKit
import TelegramPresentationData
import UserNotifications

/// Local tasks are isolated by account and never included in settings transfer.
final class NebulaTasksController: UITableViewController {
    private struct Entry: Codable { var id: String; var text: String; var done: Bool }
    private let storage: String
    private let russian: Bool
    private let theme: PresentationTheme
    private var draft: String
    private var entries: [Entry] = []
    init(accountId: String, russian: Bool, theme: PresentationTheme, draft: String = "") {
        self.storage = "app.nebulagram.tasks." + accountId
        self.russian = russian
        self.theme = theme
        self.draft = draft
        super.init(style: .insetGrouped)
        if let data = UserDefaults.standard.data(forKey: storage), let saved = try? JSONDecoder().decode([Entry].self, from: data) { entries = saved }
        title = russian ? "Задачи" : "Tasks"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.backgroundColor = theme.list.blocksBackgroundColor
        tableView.tintColor = theme.list.itemAccentColor
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        if navigationController?.viewControllers.first === self { navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close)) }
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .add, target: self, action: #selector(add))
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if !draft.isEmpty { add() }
    }
    @objc private func close() { dismiss(animated: true) }
    @objc private func add() {
        let value = draft
        draft = ""
        let alert = UIAlertController(title: russian ? "Новая задача" : "New task", message: nil, preferredStyle: .alert)
        alert.view.tintColor = theme.list.itemAccentColor
        alert.addTextField { field in field.text = value; field.placeholder = self.russian ? "Текст задачи" : "Task text" }
        alert.addAction(UIAlertAction(title: russian ? "Отмена" : "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: russian ? "Сохранить" : "Save", style: .default) { [weak self, weak alert] _ in
            guard let self, let value = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return }
            self.entries.insert(Entry(id: UUID().uuidString, text: String(value.prefix(10_000)), done: false), at: 0)
            self.save()
        })
        present(alert, animated: true)
    }
    private func save() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        UserDefaults.standard.set(data, forKey: storage)
        tableView.reloadData()
    }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { entries.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        russian ? "Нажмите, чтобы завершить. Удерживайте для напоминания." : "Tap to complete. Touch and hold to set a reminder."
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        cell.textLabel?.text = entries[indexPath.row].text
        cell.accessoryType = entries[indexPath.row].done ? .checkmark : .none
        NebulaSettingsStyle.finish(cell, theme: theme)
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        entries[indexPath.row].done.toggle()
        if entries[indexPath.row].done { UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [storage + entries[indexPath.row].id]) }
        save()
    }
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        guard editingStyle == .delete else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [storage + entries[indexPath.row].id])
        entries.remove(at: indexPath.row)
        save()
    }
    override func tableView(_ tableView: UITableView, contextMenuConfigurationForRowAt indexPath: IndexPath, point: CGPoint) -> UIContextMenuConfiguration? {
        let entry = entries[indexPath.row]
        guard !entry.done else { return nil }
        return UIContextMenuConfiguration(identifier: nil, previewProvider: nil) { [weak self] _ in
            guard let self else { return nil }
            return UIMenu(title: self.russian ? "Напомнить" : "Remind me", children: [
                UIAction(title: self.russian ? "Через час" : "In an hour") { [weak self] _ in self?.remind(entry, after: 3600) },
                UIAction(title: self.russian ? "Завтра" : "Tomorrow") { [weak self] _ in self?.remind(entry, after: 86400) }
            ])
        }
    }
    private func remind(_ entry: Entry, after interval: TimeInterval) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { [weak self] allowed, error in
            DispatchQueue.main.async {
                guard let self else { return }
                guard allowed, error == nil else { self.notice(self.russian ? "Разрешите уведомления в настройках iOS." : "Allow notifications in iOS Settings."); return }
                guard self.entries.contains(where: { $0.id == entry.id && !$0.done }) else { return }
                let content = UNMutableNotificationContent()
                content.title = self.russian ? "Напоминание NebulaGram" : "NebulaGram reminder"
                // Do not expose a private message in a lock-screen notification.
                content.body = self.russian ? "Откройте список задач." : "Open your task list."
                content.sound = .default
                center.add(UNNotificationRequest(identifier: self.storage + entry.id, content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false))) { [weak self] error in
                    DispatchQueue.main.async { self?.notice(error == nil ? (self?.russian == true ? "Напоминание установлено" : "Reminder set") : (self?.russian == true ? "Не удалось установить напоминание" : "Could not set reminder")) }
                }
            }
        }
    }
    private func notice(_ value: String) {
        let alert = UIAlertController(title: value, message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
