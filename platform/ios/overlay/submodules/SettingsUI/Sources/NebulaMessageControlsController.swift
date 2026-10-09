import UIKit
import Display
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaMessageControlsController: UITableViewController {
    private let context: AccountContext
    private let theme: PresentationTheme
    private let ru: Bool
    private let profile: Bool
    private let prefs = NebulaMessagePreferences.shared
    private var keys: [String] { profile ? ["hide_profile_phone", "profile_photo_dc"] : ["edited_pencil", "forward_date", "direct_share", "voice_autoplay", "seek_interval", "pause_background_video", "disable_message_effects", "premium_effects", "reaction_effects", "instant_view", "delete_for_all", "selection_without_author"] }
    private lazy var hero = NebulaSettingsHero(symbol: profile ? "👤" : "💬", title: title ?? "", summary: ru ? (profile ? "Локальное отображение данных профиля" : "Отметки сообщений и воспроизведение") : (profile ? "Local profile presentation" : "Message labels and playback"), context: context, theme: theme)
    init(context: AccountContext, profile: Bool) {
        self.context = context; self.profile = profile
        let data = context.sharedContext.currentPresentationData.with { $0 }; theme = data.theme; ru = data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(style: .insetGrouped); title = ru ? (profile ? "Данные профиля" : "Сообщения и медиа") : (profile ? "Profile information" : "Messages and media")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 76
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { keys.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if !profile { return ru ? "Отметки и кнопки обновятся при следующем открытии чата. Отключение автовоспроизведения оставляет ручной переход к следующему голосовому." : "Labels and buttons update when you reopen the chat. With autoplay off, you can still advance manually." }
        return ru ? "Изменения оформления видны при следующем открытии чата или профиля. Номер скрывается только в вашем интерфейсе; настройки приватности Telegram не меняются." : "Presentation updates when you reopen the chat or profile. Phone hiding affects your interface only; Telegram privacy settings stay unchanged."
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let key = keys[indexPath.row]
        let titles = ru ? ["selection_without_author": "Кнопка «Без авторства»", "edited_pencil": "Карандаш вместо «изменено»", "forward_date": "Дата исходного сообщения", "direct_share": "Кнопка быстрой пересылки", "voice_autoplay": "Следующее голосовое автоматически", "hide_profile_phone": "Скрывать номер в профиле", "profile_photo_dc": "DC фотографии профиля", "seek_interval": "Шаг перемотки видео", "pause_background_video": "Пауза видео в фоне", "disable_message_effects": "Отключить эффекты сообщений", "premium_effects": "Эффекты Premium-стикеров", "reaction_effects": "Анимация реакций", "instant_view": "Открывать Instant View", "delete_for_all": "Удалять для всех по умолчанию"] : ["selection_without_author": "Without author button", "edited_pencil": "Pencil for edited messages", "forward_date": "Original forwarded date", "direct_share": "Quick forward button", "voice_autoplay": "Autoplay the next voice message", "hide_profile_phone": "Hide profile phone number", "profile_photo_dc": "Profile photo DC", "seek_interval": "Video seek interval", "pause_background_video": "Pause background video", "disable_message_effects": "Disable message effects", "premium_effects": "Premium sticker effects", "reaction_effects": "Reaction animations", "instant_view": "Open Instant View", "delete_for_all": "Select delete for everyone by default"]
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); cell.textLabel?.text = titles[key]
        if key == "selection_without_author" { cell.detailTextLabel?.text = ru ? "Добавить кнопку в нижнюю панель выделения. По умолчанию — стандартные кнопки Telegram." : "Add a button to the bottom selection panel. Standard Telegram controls remain the default." }
        if key == "profile_photo_dc" { cell.detailTextLabel?.text = ru ? "Центр данных, где хранится фото пользователя" : "Data center storing the user's profile photo" }
        if key == "instant_view" { cell.detailTextLabel?.text = ru ? "Если выключено, ссылки открываются в браузере" : "Open links in the browser when disabled" }
        if key == "delete_for_all" { cell.detailTextLabel?.text = ru ? "Выбирать переключатель удаления своих сообщений у собеседника, когда Telegram его предлагает. Подтверждение остаётся." : "Preselect the switch to remove your messages for the recipient when Telegram offers it. Confirmation is still required." }
        if key == "forward_date" { cell.detailTextLabel?.text = ru ? "Показывать полную исходную дату пересланных сообщений" : "Show the full original date of forwarded messages" }
        if key == "seek_interval" {
            cell.detailTextLabel?.text = String(prefs.seekInterval) + (ru ? " секунд" : " seconds")
            cell.accessoryType = .disclosureIndicator; NebulaSettingsStyle.finish(cell, theme: theme); return cell
        }
        if key == "pause_background_video" { cell.detailTextLabel?.text = ru ? "Видео в просмотрщике без активного окна Picture in Picture" : "Gallery video without an active Picture in Picture window" }
        let toggle = NebulaSwitchControl(); toggle.isOn = prefs.enabled(key); toggle.accessibilityIdentifier = key; toggle.accessibilityLabel = titles[key]
        toggle.addTarget(self, action: #selector(change(_:)), for: .valueChanged); cell.accessoryView = toggle; cell.selectionStyle = .none
        NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard keys[indexPath.row] == "seek_interval" else { return }
        let values = [5, 10, 15, 20, 30]
        NebulaChoiceController.show(from: self, title: ru ? "Шаг перемотки" : "Seek interval", choices: values.map { String($0) + (ru ? " секунд" : " seconds") }, selected: values.firstIndex(of: prefs.seekInterval), russian: ru, theme: theme) { [weak self] index in
            self?.prefs.setSeekInterval(values[index]); self?.tableView.reloadData()
        }
    }
    @objc private func change(_ sender: NebulaSwitchControl) { if let key = sender.accessibilityIdentifier { prefs.set(key, sender.isOn) } }
    @objc private func close() { dismiss(animated: true) }
}
