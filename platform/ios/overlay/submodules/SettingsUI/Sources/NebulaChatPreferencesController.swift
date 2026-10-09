import UIKit
import Display
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaChatPreferencesController: UITableViewController {
    private let context: AccountContext
    private let store = NebulaSettingsStore.shared
    private let theme: PresentationTheme
    private let ru: Bool
    private var order: [NebulaSwipeAction] { store.swipeActions }
    private let keys = ["inline_math", "channel_forward_count", "folder_unmuted_only", "sticker_time_style"]
    private lazy var hero = NebulaSettingsHero(symbol: "💬", title: title ?? "", summary: t("Детали сообщений и действия жестов", "Message details and gesture actions"), context: context, theme: theme)
    init(context: AccountContext) {
        self.context = context
        let data = context.sharedContext.currentPresentationData.with { $0 }; theme = data.theme; ru = data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(style: .insetGrouped); title = t("Сообщения и жесты", "Messages and gestures")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func t(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    private func name(_ action: NebulaSwipeAction) -> String { (ru ? ["Ответить", "Копировать", "Инструменты Nebula", "Перевести"] : ["Reply", "Copy", "Nebula tools", "Translate"])[action.rawValue] }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        navigationItem.rightBarButtonItem = editButtonItem
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? keys.count : section == 1 ? order.count : NebulaSwipeAction.allCases.filter { !order.contains($0) }.count }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { [t("Отображение", "Presentation"), t("Порядок действий свайпа", "Swipe action order"), t("Добавить действие", "Add action")][section] }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 1 { return t("Потяните сообщение влево, затем ведите палец вниз для выбора действия. Отпустите для выполнения. Порядок меняется кнопкой «Править». Доступность зависит от сообщения и прав в чате.", "Swipe a message left, then move down to choose an action. Release to perform it. Use Edit to reorder. Availability depends on the message and chat permissions.") }
        return section == 0 ? t("Вычисления не меняют текст. Фильтр счётчиков не меняет состав папок. Оформление сообщений обновится при открытии чата.", "Arithmetic keeps your draft unchanged. Badge filtering keeps folder membership unchanged. Message appearance updates when opening a chat.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        if indexPath.section == 0 {
            let key = keys[indexPath.row]
            cell.textLabel?.text = (ru ? ["Вычисления при наборе", "Количество пересылок", "Счётчики только со звуком", "Время у стикеров"] : ["Inline arithmetic", "Forward count", "Unmuted folder counters", "Sticker time"])[indexPath.row]
            if key == "sticker_time_style" {
                cell.detailTextLabel?.text = timeNames[store.stickerTimeStyle]; cell.accessoryType = .disclosureIndicator
            } else {
                let toggle = NebulaSwitchControl(); toggle.isOn = [store.inlineArithmetic, store.channelForwardCount, store.folderUnmutedOnly][indexPath.row]
                toggle.accessibilityIdentifier = key; toggle.accessibilityLabel = cell.textLabel?.text; toggle.isEnabled = !store.hasLoadError
                toggle.addTarget(self, action: #selector(change(_:)), for: .valueChanged); cell.accessoryView = toggle; cell.selectionStyle = .none
            }
        } else {
            let action = indexPath.section == 1 ? order[indexPath.row] : NebulaSwipeAction.allCases.filter { !order.contains($0) }[indexPath.row]
            cell.textLabel?.text = name(action)
            cell.imageView?.image = UIImage(systemName: ["arrowshape.turn.up.left", "doc.on.doc", "sparkles", "character.bubble"][action.rawValue])
            cell.showsReorderControl = indexPath.section == 1
            if indexPath.section == 2 { cell.accessoryView = UIImageView(image: UIImage(systemName: "plus")) }
        }
        NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    private var timeNames: [String] { ru ? ["Обычно", "Сбоку", "Без времени"] : ["Default", "Beside sticker", "Hide time"] }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 0 && indexPath.row == 3 {
            NebulaChoiceController.show(from: self, title: t("Время у стикеров", "Sticker time"), choices: timeNames, selected: store.stickerTimeStyle, russian: ru, theme: theme) { [weak self] value in self?.save(.integer(value), key: "sticker_time_style") }
        } else if indexPath.section == 2 {
            let action = NebulaSwipeAction.allCases.filter { !order.contains($0) }[indexPath.row]; saveOrder(order + [action])
        }
    }
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool { indexPath.section == 1 && !store.hasLoadError }
    override func tableView(_ tableView: UITableView, editingStyleForRowAt indexPath: IndexPath) -> UITableViewCell.EditingStyle { indexPath.section == 1 && order.count > 1 ? .delete : .none }
    override func tableView(_ tableView: UITableView, canMoveRowAt indexPath: IndexPath) -> Bool { indexPath.section == 1 && !store.hasLoadError }
    override func tableView(_ tableView: UITableView, targetIndexPathForMoveFromRowAt sourceIndexPath: IndexPath, toProposedIndexPath proposedDestinationIndexPath: IndexPath) -> IndexPath { IndexPath(row: max(0, min(order.count - 1, proposedDestinationIndexPath.section < 1 ? 0 : proposedDestinationIndexPath.section > 1 ? order.count - 1 : proposedDestinationIndexPath.row)), section: 1) }
    override func tableView(_ tableView: UITableView, moveRowAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) { var actions = order; let action = actions.remove(at: sourceIndexPath.row); actions.insert(action, at: destinationIndexPath.row); saveOrder(actions) }
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) { if editingStyle == .delete && order.count > 1 { var actions = order; actions.remove(at: indexPath.row); saveOrder(actions) } }
    @objc private func change(_ sender: NebulaSwitchControl) { if let key = sender.accessibilityIdentifier { save(.boolean(sender.isOn), key: key) } }
    private func saveOrder(_ actions: [NebulaSwipeAction]) { save(.string(actions.map { String($0.rawValue) }.joined(separator: ",")), key: "swipe_actions") }
    private func save(_ value: SettingValue, key: String) { do { try store.set(value, for: key) } catch { /* Keep the saved state if storage is unavailable. */ }; tableView.reloadData() }
}
