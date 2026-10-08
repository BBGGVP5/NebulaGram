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
    private var keys: [String] { profile ? ["hide_profile_phone", "profile_photo_dc"] : ["edited_pencil", "forward_date", "direct_share", "voice_autoplay"] }
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
        let titles = ru ? ["edited_pencil": "Карандаш вместо «изменено»", "forward_date": "Дата исходного сообщения", "direct_share": "Кнопка быстрой пересылки", "voice_autoplay": "Следующее голосовое автоматически", "hide_profile_phone": "Скрывать номер в профиле", "profile_photo_dc": "DC фотографии профиля"] : ["edited_pencil": "Pencil for edited messages", "forward_date": "Original forwarded date", "direct_share": "Quick forward button", "voice_autoplay": "Autoplay the next voice message", "hide_profile_phone": "Hide profile phone number", "profile_photo_dc": "Profile photo DC"]
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); cell.textLabel?.text = titles[key]
        if key == "profile_photo_dc" { cell.detailTextLabel?.text = ru ? "Центр данных, где хранится фото пользователя" : "Data center storing the user's profile photo" }
        if key == "forward_date" { cell.detailTextLabel?.text = ru ? "Показывать полную исходную дату пересланных сообщений" : "Show the full original date of forwarded messages" }
        let toggle = NebulaSwitchControl(); toggle.isOn = prefs.enabled(key); toggle.accessibilityIdentifier = key; toggle.accessibilityLabel = titles[key]
        toggle.addTarget(self, action: #selector(change(_:)), for: .valueChanged); cell.accessoryView = toggle; cell.selectionStyle = .none
        NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    @objc private func change(_ sender: NebulaSwitchControl) { if let key = sender.accessibilityIdentifier { prefs.set(key, sender.isOn) } }
    @objc private func close() { dismiss(animated: true) }
}
