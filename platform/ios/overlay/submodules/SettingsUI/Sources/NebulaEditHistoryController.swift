import UIKit
import Display
import Postbox
import TelegramCore
import AccountContext
import SwiftSignalKit
import TelegramPresentationData
import TextFormat

final class NebulaEditHistoryController: UIViewController {
    private let context: AccountContext
    private let message: Message?
    private let russian: Bool
    private var operation: Disposable?
    private let stack = UIStackView()
    private var theme: PresentationTheme { context.sharedContext.currentPresentationData.with { $0 }.theme }
    init(context: AccountContext, message: Message? = nil, russian: Bool) {
        self.context = context; self.message = message; self.russian = russian
        super.init(nibName: nil, bundle: nil); title = text("История правок", "Edit history")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { operation?.dispose() }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        let scroll = UIScrollView(); scroll.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(scroll)
        stack.axis = .vertical; stack.spacing = 16; stack.translatesAutoresizingMaskIntoConstraints = false; scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor), scroll.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 16), stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -16),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16), stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32)
        ])
        rebuild()
    }
    private func label(_ value: String) {
        let label = UILabel(); label.numberOfLines = 0; label.font = .preferredFont(forTextStyle: .subheadline)
        label.adjustsFontForContentSizeCategory = true; label.textColor = theme.list.itemSecondaryTextColor; label.text = value; stack.addArrangedSubview(label)
    }
    private func rebuild() {
        operation?.dispose()
        for view in stack.arrangedSubviews { stack.removeArrangedSubview(view); view.removeFromSuperview() }
        let row = UIStackView(); row.axis = .horizontal; row.spacing = 12
        let title = UILabel(); title.text = text("Сохранять правки", "Save edits"); title.numberOfLines = 0
        title.font = .preferredFont(forTextStyle: .body); title.adjustsFontForContentSizeCategory = true; title.textColor = theme.list.itemPrimaryTextColor
        let toggle = NebulaSwitchControl(); toggle.isOn = NebulaEditHistory.enabled(account: context.account.peerId.toInt64()); toggle.addTarget(self, action: #selector(changed(_:)), for: .valueChanged)
        row.addArrangedSubview(title); row.addArrangedSubview(toggle); stack.addArrangedSubview(row)
        label(text("Только после включения: до 32 версий для 1000 последних изменённых сообщений. Секретные, исчезающие, защищённые и служебные сообщения исключены. Выключение сохраняет прежние версии. История находится в локальной базе аккаунта и удаляется при выходе.", "From activation onward: up to 32 versions for the 1000 most recently edited messages. Secret, expiring, protected and service messages are excluded. Turning this off keeps previous versions. History stays in the local account database and is removed on logout."))
        let clear = UIButton(type: .system); clear.setTitle(message == nil ? text("Очистить всю историю аккаунта", "Clear all account history") : text("Очистить историю сообщения", "Clear message history"), for: .normal)
        clear.titleLabel?.numberOfLines = 0; clear.titleLabel?.font = .preferredFont(forTextStyle: .body); clear.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true; clear.addTarget(self, action: #selector(clearHistory), for: .touchUpInside); stack.addArrangedSubview(clear)
        guard let message else { label(text("Откройте инструменты нужного сообщения → «История правок».", "Open a message's tools → Edit history.")); return }
        label(text("Сейчас", "Current")); addVersion(text: message.text, entities: message.textEntitiesAttribute?.entities ?? [], timestamp: message.timestamp)
        label(text("Предыдущие версии", "Previous versions"))
        operation = (NebulaEditHistory.read(account: context.account, id: message.id) |> deliverOnMainQueue).start(next: { [weak self] versions in
            guard let self else { return }
            if versions.isEmpty { self.label(self.text("Сохранённых версий пока нет", "No saved versions yet")) }
            for version in versions { self.addVersion(text: version.text, entities: version.entities, timestamp: version.timestamp) }
        })
    }
    private func addVersion(text: String, entities: [MessageTextEntity], timestamp: Int32) {
        label(DateFormatter.localizedString(from: Date(timeIntervalSince1970: Double(timestamp)), dateStyle: .medium, timeStyle: .medium))
        let body = UITextView(); body.isEditable = false; body.isScrollEnabled = false; body.isSelectable = false
        body.backgroundColor = theme.list.itemBlocksBackgroundColor; body.layer.cornerRadius = 20; body.textContainerInset = UIEdgeInsets(top: 16, left: 12, bottom: 16, right: 12)
        let size = UIFont.preferredFont(forTextStyle: .body).pointSize
        body.attributedText = stringWithAppliedEntities(text, entities: entities,
            baseColor: theme.list.itemPrimaryTextColor, linkColor: theme.list.itemPrimaryTextColor,
            baseFont: Font.regular(size), linkFont: Font.regular(size), boldFont: Font.semibold(size), italicFont: Font.italic(size),
            boldItalicFont: Font.semiboldItalic(size), fixedFont: Font.monospace(size), blockQuoteFont: Font.regular(size), message: nil)
        if entities.contains(where: { $0.type == .Spoiler }) {
            body.isHidden = true
            let reveal = UIButton(type: .system)
            reveal.setTitle(self.text("Спойлер · показать версию", "Spoiler · reveal version"), for: .normal)
            reveal.titleLabel?.numberOfLines = 0
            reveal.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
            reveal.addAction(UIAction { [weak body, weak reveal] _ in body?.isHidden = false; reveal?.isHidden = true }, for: .touchUpInside)
            stack.addArrangedSubview(reveal)
        }
        stack.addArrangedSubview(body)
    }
    @objc private func changed(_ toggle: NebulaSwitchControl) { NebulaEditHistory.setEnabled(account: context.account.peerId.toInt64(), value: toggle.isOn) }
    @objc private func clearHistory() {
        let alert = UIAlertController(title: text("Удалить локальные версии?", "Delete local versions?"), message: text("Текущее сообщение в Telegram останется.", "The current Telegram message stays."), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Очистить", "Clear"), style: .destructive) { [weak self] _ in
            guard let self else { return }; self.operation?.dispose()
            self.operation = (NebulaEditHistory.clear(account: self.context.account, id: self.message?.id) |> deliverOnMainQueue).start(next: { [weak self] _ in self?.rebuild() })
        }); present(alert, animated: true)
    }
}
