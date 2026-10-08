import UIKit
import Display
import ContextUI
import GlassBackgroundComponent
import AccountContext
import TelegramPresentationData
import SwiftSignalKit
import NebulaSettingsContract

/// Uses Telegram's actual extracted-content presenter: only one bubble exists
/// throughout opening and closing, including interactive cancellation.
final class NebulaPresentationPreviewController: UITableViewController {
    private let context: AccountContext
    private let profile: Bool
    private let theme: PresentationTheme
    private let ru: Bool
    private let store = NebulaSettingsStore.shared
    private let extracted = ContextExtractedContentContainingView(frame: .zero)
    private let sample = UIStackView()
    private let material = GlassBackgroundView(frame: .zero)
    private var keys: [String] { profile ? ["profile_style", "profile_channel", "profile_birthday", "profile_business", "profile_background", "profile_emoji", "profile_photo_banner"] : ["message_menu_blur", "menu_search", "menu_mute", "menu_call", "menu_video"] }
    private lazy var hero = NebulaSettingsHero(symbol: profile ? "👤" : "💬", title: title ?? "", summary: t("Предпросмотр без изменения сообщений", "Preview without changing messages"), context: context, theme: theme)
    init(context: AccountContext, profile: Bool) {
        self.context = context; self.profile = profile
        let data = context.sharedContext.currentPresentationData.with { $0 }; theme = data.theme; ru = data.strings.baseLanguageCode.hasPrefix("ru")
        super.init(style: .insetGrouped); title = profile ? t("Оформление профиля", "Profile appearance") : t("Меню чата и сообщения", "Chat and message menus")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func t(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        sample.axis = .vertical; sample.spacing = 10; sample.isLayoutMarginsRelativeArrangement = true; sample.layoutMargins = UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        extracted.contentView.addSubview(material); material.isUserInteractionEnabled = false
        extracted.contentView.addSubview(sample); sample.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([sample.leadingAnchor.constraint(equalTo: extracted.contentView.leadingAnchor), sample.trailingAnchor.constraint(equalTo: extracted.contentView.trailingAnchor), sample.topAnchor.constraint(equalTo: extracted.contentView.topAnchor), sample.bottomAnchor.constraint(equalTo: extracted.contentView.bottomAnchor)])
        rebuildSample()
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 1 : keys.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 { return profile ? t("Условный профиль · информация приведена для примера", "Sample profile · illustrative information") : t("Нажмите на сообщение. Меню использует ту же анимацию и материал, что и в чате. Действия в примере ничего не отправляют.", "Tap the message. Its menu uses the same animation and material as a chat. Sample actions do not send anything.") }
        return profile ? nil : t("Кнопки ниже относятся к меню в заголовке чата; доступность звонков зависит от типа чата.", "The buttons below control the chat header menu; calls depend on the chat type.")
    }
    private func value(_ key: String) -> Bool {
        switch key { case "profile_style": return store.profileStyle; case "profile_channel": return store.profileChannel; case "profile_birthday": return store.profileBirthday; case "profile_business": return store.profileBusiness; case "profile_background": return store.profileBackground; case "profile_emoji": return store.profileEmoji; case "profile_photo_banner": return store.profilePhotoBanner; case "message_menu_blur": return store.messageMenuBlur; case "menu_search": return store.menuSearch; case "menu_mute": return store.menuMute; case "menu_call": return store.menuCall; default: return store.menuVideo }
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil); cell.selectionStyle = .none
        if indexPath.section == 0 {
            let container = NebulaExtractedPreviewHost(extracted: extracted, material: material, dark: theme.overallDarkAppearance)
            container.translatesAutoresizingMaskIntoConstraints = false; cell.contentView.addSubview(container)
            let height = sample.systemLayoutSizeFitting(CGSize(width: max(220, tableView.bounds.width - 72), height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
            NSLayoutConstraint.activate([container.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 12), container.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -12), container.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 16), container.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -16), container.heightAnchor.constraint(equalToConstant: max(180, height))])
            cell.backgroundColor = .clear; return cell
        }
        let key = keys[indexPath.row]
        let names = ru ? ["Стеклянный профиль", "Канал", "День рождения", "Данные бизнеса", "Фон профиля", "Эмодзи профиля", "Фото в шапке"] : ["Glass profile", "Channel", "Birthday", "Business information", "Profile background", "Profile emoji", "Header photo"]
        cell.textLabel?.text = profile ? names[indexPath.row] : (ru ? ["Размытие за меню сообщения", "Поиск", "Звук", "Звонок", "Видеозвонок"] : ["Message menu blur", "Search", "Sound", "Call", "Video call"])[indexPath.row]
        let toggle = NebulaSwitchControl(); toggle.isOn = value(key); toggle.accessibilityIdentifier = key; toggle.accessibilityLabel = cell.textLabel?.text; toggle.addTarget(self, action: #selector(change(_:)), for: .valueChanged); cell.accessoryView = toggle
        NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    private func rebuildSample() {
        for view in sample.arrangedSubviews { sample.removeArrangedSubview(view); view.removeFromSuperview() }
        material.isHidden = !(profile && store.profileStyle)
        extracted.contentView.backgroundColor = theme.list.itemBlocksBackgroundColor; extracted.contentView.layer.cornerRadius = profile ? 24 : 18
        func label(_ text: String, headline: Bool = false) { let label = UILabel(); label.text = text; label.font = .preferredFont(forTextStyle: headline ? .headline : .body); label.adjustsFontForContentSizeCategory = true; label.numberOfLines = 0; label.textColor = headline ? theme.list.itemPrimaryTextColor : theme.list.itemSecondaryTextColor; sample.addArrangedSubview(label) }
        if profile {
            if store.profilePhotoBanner { let image = UIImageView(image: UIImage(systemName: "person.crop.circle.fill")); image.tintColor = theme.list.itemAccentColor; image.contentMode = .scaleAspectFit; image.heightAnchor.constraint(equalToConstant: 76).isActive = true; sample.addArrangedSubview(image) }
            label("NebulaGram" + (store.profileEmoji ? " ✨" : ""), headline: true)
            if store.profileBackground { extracted.contentView.backgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.14) }
            if store.profileChannel { label(t("Канал · Новости проекта", "Channel · Project news")) }
            if store.profileBirthday { label(t("День рождения · 8 октября", "Birthday · October 8")) }
            if store.profileBusiness { label(t("Часы работы · 09:00–18:00", "Opening hours · 09:00–18:00")) }
        } else {
            label(t("Сообщение для предпросмотра", "Preview message"), headline: true)
            label(t("Нажмите, чтобы раскрыть меню", "Tap to open its menu"))
            let button = UIButton(type: .system); button.setTitle(t("Открыть меню", "Open menu"), for: .normal); button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true; button.addTarget(self, action: #selector(openMenu), for: .touchUpInside); sample.addArrangedSubview(button)
            let headerActions = [(store.menuSearch, t("Поиск", "Search")), (store.menuMute, t("Звук", "Sound")), (store.menuCall, t("Звонок", "Call")), (store.menuVideo, t("Видео", "Video"))].filter { $0.0 }.map { $0.1 }
            label(headerActions.joined(separator: " · "))
        }
    }
    @objc private func change(_ sender: NebulaSwitchControl) {
        guard let key = sender.accessibilityIdentifier else { return }
        do { try store.set(.boolean(sender.isOn), for: key) } catch { sender.isOn = value(key); return }
        rebuildSample(); tableView.reloadData()
    }
    @objc private func openMenu() {
        guard presentedViewController == nil, !extracted.isExtractedToContextPreview else { return }
        let entries = [(t("Ответить", "Reply"), "arrowshape.turn.up.left"), (t("Копировать", "Copy"), "doc.on.doc"), (t("Переслать", "Forward"), "arrowshape.turn.up.right")]
        let actions: [ContextMenuItem] = entries.map { title, symbol in .action(ContextMenuActionItem(text: title, icon: { theme in UIImage(systemName: symbol)?.withTintColor(theme.contextMenu.primaryColor, renderingMode: .alwaysOriginal) }, action: { _, completion in completion(.default) })) }
        let menu = makeContextController(context: context, presentationData: context.sharedContext.currentPresentationData.with { $0 }, source: .extracted(NebulaPreviewExtractedSource(view: extracted, blur: store.messageMenuBlur)), items: .single(ContextController.Items(content: .list(actions))))
        present(menu, animated: true)
    }
    @objc private func close() { dismiss(animated: true) }
}

private final class NebulaExtractedPreviewHost: UIView {
    private let extracted: ContextExtractedContentContainingView
    private let material: GlassBackgroundView
    private let dark: Bool
    init(extracted: ContextExtractedContentContainingView, material: GlassBackgroundView, dark: Bool) { self.extracted = extracted; self.material = material; self.dark = dark; super.init(frame: .zero); addSubview(extracted) }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() { super.layoutSubviews(); extracted.frame = bounds; extracted.contentRect = bounds; if !extracted.isExtractedToContextPreview { extracted.contentView.frame = bounds; material.frame = bounds; material.update(size: bounds.size, cornerRadius: 24, isDark: dark, tintColor: .init(kind: .panel), isInteractive: true, transition: .immediate) } }
}

private final class NebulaPreviewExtractedSource: ContextExtractedContentSource {
    private let view: ContextExtractedContentContainingView
    let blurBackground: Bool
    let keepInPlace = false
    let ignoreContentTouches = true
    init(view: ContextExtractedContentContainingView, blur: Bool) { self.view = view; blurBackground = blur }
    func takeView() -> ContextControllerTakeViewInfo? { guard let window = view.window else { return nil }; return ContextControllerTakeViewInfo(containingItem: .view(view), contentAreaInScreenSpace: window.bounds.inset(by: window.safeAreaInsets)) }
    func putBack() -> ContextControllerPutBackViewInfo? { guard let window = view.window else { return nil }; return ContextControllerPutBackViewInfo(contentAreaInScreenSpace: window.bounds.inset(by: window.safeAreaInsets)) }
}
