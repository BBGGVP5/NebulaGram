import Display
import UIKit
import NebulaSettingsContract
import TelegramPresentationData

final class NebulaAvatarSettingsController: UITableViewController {
    private let ru: Bool
    private let theme: PresentationTheme
    private let store = NebulaSettingsStore.shared
    private var observation: SettingsObservation?

    init(russian: Bool, theme: PresentationTheme) {
        self.ru = russian; self.theme = theme
        super.init(style: .insetGrouped)
        title = ru ? "Форма" : "Shape"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 56
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        observation = store.observe { [weak self] in self?.updateEnabledState() }
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 4 : 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? (ru ? "Аватары" : "Avatars") : section == 1 ? (ru ? "Сообщения" : "Messages") : (ru ? "Список чатов" : "Chat list")
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 2 { return ru ? "Каждый чат на своей карточке; разделители скрываются. Архив остаётся как есть." : "Every chat on its own card; dividers are hidden. The archive row stays as it is." }
        guard section == 1 else { return nil }
        return ru ? "Без хвостов у всех пузырей одинаковые скругления. Применяется после перезапуска приложения." : "Without tails every bubble has the same corners. Applies after the app restarts."
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath == IndexPath(row: 3, section: 0), !store.hasLoadError else { return }
        let shapes = NebulaShapePreferences.AvatarShape.allCases
        NebulaChoiceController.show(from: self, title: ru ? "Форма аватаров" : "Avatar shape", choices: shapes.map { $0.title(russian: ru) }, selected: NebulaShapePreferences.AvatarShape.matching(roundness: store.avatarRound).flatMap { shapes.firstIndex(of: $0) }, russian: ru, theme: theme) { [weak self] index in
            guard let self else { return }
            do {
                try self.store.set(.boolean(true), for: "custom_avatar_corners")
                try self.store.set(.integer(shapes[index].roundness), for: "avatar_round")
            } catch { }
            self.tableView.reloadData()
        }
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        defer { NebulaSettingsStyle.finish(cell, theme: theme) }
        cell.selectionStyle = .none
        if indexPath.section == 2 {
            cell.textLabel?.text = ru ? "Чаты карточками" : "Chats as cards"
            let toggle = NebulaSwitchControl(); toggle.tag = 11
            toggle.isOn = NebulaShapePreferences.chatCards()
            toggle.accessibilityLabel = cell.textLabel?.text
            toggle.addTarget(self, action: #selector(cardsChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle
            return cell
        }
        if indexPath.section == 1 {
            cell.textLabel?.text = ru ? "Хвосты у сообщений" : "Message tails"
            let toggle = NebulaSwitchControl(); toggle.tag = 10
            toggle.isOn = NebulaShapePreferences.messageTails()
            toggle.accessibilityLabel = cell.textLabel?.text
            toggle.addTarget(self, action: #selector(tailsChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle
            return cell
        }
        if indexPath.row == 3 {
            cell.textLabel?.text = ru ? "Готовая форма" : "Preset shape"
            cell.detailTextLabel?.text = store.customAvatarCorners ? (NebulaShapePreferences.AvatarShape.matching(roundness: store.avatarRound)?.title(russian: ru) ?? (ru ? "Своя: \(store.avatarRound) %" : "Custom: \(store.avatarRound)%")) : (ru ? "Как в Telegram" : "Telegram default")
            cell.accessoryType = .disclosureIndicator
            cell.selectionStyle = .default
            return cell
        }
        if indexPath.row < 2 {
            cell.textLabel?.text = indexPath.row == 0 ? (ru ? "Своя форма" : "Custom shape")
                : (ru ? "Одинаковая форма у всех" : "Same shape for everyone")
            if indexPath.row == 1 {
                cell.detailTextLabel?.text = ru ? "Пользователи, группы и каналы" : "People, groups and channels"
            }
            let toggle = NebulaSwitchControl(); toggle.tag = indexPath.row
            toggle.isOn = indexPath.row == 0 ? store.customAvatarCorners : store.uniformAvatars
            toggle.isEnabled = !store.hasLoadError
            toggle.addTarget(self, action: #selector(toggleChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle
        } else {
            cell.textLabel?.text = ru ? "Скругление" : "Corner radius"
            let slider = UISlider(frame: CGRect(x: 0, y: 0, width: 145, height: 44))
            slider.minimumValue = 0; slider.maximumValue = 100
            slider.value = Float(store.avatarRound)
            slider.isEnabled = store.customAvatarCorners && !store.hasLoadError
            slider.accessibilityLabel = cell.textLabel?.text
            slider.accessibilityValue = "\(store.avatarRound)%"
            slider.addTarget(self, action: #selector(radiusChanged(_:)), for: .valueChanged)
            cell.accessoryView = slider
        }
        return cell
    }
    private func updateEnabledState() {
        guard let cell = tableView.cellForRow(at: IndexPath(row: 2, section: 0)), let slider = cell.accessoryView as? UISlider else { return }
        slider.isEnabled = store.customAvatarCorners && !store.hasLoadError
        if !slider.isTracking { slider.value = Float(store.avatarRound) }
        slider.accessibilityValue = "\(store.avatarRound)%"
    }
    @objc private func toggleChanged(_ toggle: NebulaSwitchControl) {
        do { try store.set(.boolean(toggle.isOn), for: toggle.tag == 0 ? "custom_avatar_corners" : "uniform_avatars") }
        catch { toggle.isOn = toggle.tag == 0 ? store.customAvatarCorners : store.uniformAvatars }
        updateEnabledState()
    }
    @objc private func cardsChanged(_ toggle: NebulaSwitchControl) {
        NebulaShapePreferences.setChatCards(toggle.isOn)
    }
    @objc private func tailsChanged(_ toggle: NebulaSwitchControl) {
        NebulaShapePreferences.setMessageTails(toggle.isOn)
    }
    @objc private func radiusChanged(_ slider: UISlider) {
        let radius = Int(slider.value.rounded())
        guard radius != store.avatarRound else { return }
        do { try store.set(.integer(radius), for: "avatar_round") }
        catch { slider.value = Float(store.avatarRound) }
        slider.accessibilityValue = "\(store.avatarRound)%"
    }
}
