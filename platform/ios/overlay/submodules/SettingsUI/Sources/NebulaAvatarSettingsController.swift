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
        title = ru ? "Аватары" : "Avatars"
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
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 3 }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        defer { NebulaSettingsStyle.finish(cell, theme: theme) }
        cell.selectionStyle = .none
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
    @objc private func radiusChanged(_ slider: UISlider) {
        let radius = Int(slider.value.rounded())
        guard radius != store.avatarRound else { return }
        do { try store.set(.integer(radius), for: "avatar_round") }
        catch { slider.value = Float(store.avatarRound) }
        slider.accessibilityValue = "\(store.avatarRound)%"
    }
}
