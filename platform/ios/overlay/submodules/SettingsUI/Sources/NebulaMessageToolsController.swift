import UIKit
import Display
import NebulaSettingsContract
import AVFoundation
import TelegramPresentationData

public final class NebulaMessageToolsController: UIViewController {
    private let russian: Bool
    private let theme: PresentationTheme
    private let source: String
    private let attributedSource: NSAttributedString
    private let accountId: String
    private let peerId: String?
    private let applyDraft: ((String) -> Void)?
    private var language: String
    private var result = ""
    private let output = UITextView()
    private let languageButton = UIButton(type: .system)
    private let speech = AVSpeechSynthesizer()
    private let incomingSwitch = NebulaSwitchControl()
    private let outgoingSwitch = NebulaSwitchControl()
    private let draftSwitch = NebulaSwitchControl()

    public init(text: String, russian: Bool, theme: PresentationTheme, accountId: String, peerId: String? = nil, applyDraft: ((String) -> Void)? = nil, attributedSource: NSAttributedString? = nil) {
        self.source = String(text.prefix(50_000))
        self.attributedSource = attributedSource ?? NSAttributedString(string: String(text.prefix(50_000)))
        self.russian = russian
        self.theme = theme
        self.accountId = accountId
        self.peerId = peerId; self.applyDraft = applyDraft
        self.language = russian ? "ru" : "en"
        super.init(nibName: nil, bundle: nil)
        title = russian ? "Инструменты сообщения" : "Message tools"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }

    public override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        navigationController?.overrideUserInterfaceStyle = theme.overallDarkAppearance ? .dark : .light
        view.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
        view.tintColor = theme.list.itemAccentColor
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = view.backgroundColor
        appearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        navigationController?.navigationBar.tintColor = view.tintColor
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(image: UIImage(systemName: "slider.horizontal.3"), style: .plain, target: self, action: #selector(settings))
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -16),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -16),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32)
        ])
        let original = UILabel()
        original.font = .preferredFont(forTextStyle: .body)
        original.adjustsFontForContentSizeCategory = true
        original.textColor = theme.list.itemSecondaryTextColor
        original.numberOfLines = 4
        original.text = source
        stack.addArrangedSubview(original)
        languageButton.titleLabel?.font = .preferredFont(forTextStyle: .body)
        languageButton.titleLabel?.numberOfLines = 0
        languageButton.contentHorizontalAlignment = .leading
        languageButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        languageButton.addTarget(self, action: #selector(chooseLanguage), for: .touchUpInside)
        updateLanguage()
        stack.addArrangedSubview(languageButton)
        let titles = [text("Спросить ИИ", "Ask AI"), text("Перевести", "Translate"), text("Сократить", "Summarize"), text("Озвучить", "Read aloud"), text("В задачу", "Create task"), text("Исправить", "Correct")]
        let symbols = ["sparkles", "character.bubble", "text.alignleft", "speaker.wave.2", "checkmark.circle", "checkmark.magnifyingglass"]
        let buttons = titles.enumerated().map { index, title -> UIButton in
            let button = NebulaToolButton(type: .system)
            button.tag = index
            button.setTitle(title, for: .normal)
            button.setImage(UIImage(systemName: symbols[index]), for: .normal)
            button.backgroundColor = .clear
            button.tintColor = theme.list.itemAccentColor
            button.setTitleColor(theme.list.itemPrimaryTextColor, for: .normal)
            button.addTarget(self, action: #selector(performTool(_:)), for: .touchUpInside)
            return button
        }
        stack.addArrangedSubview(NebulaActionGrid(buttons: buttons))
        addLiveTranslation(to: stack)
        output.font = .preferredFont(forTextStyle: .body)
        output.adjustsFontForContentSizeCategory = true
        output.textColor = theme.list.itemPrimaryTextColor
        output.backgroundColor = theme.list.itemBlocksBackgroundColor
        output.textContainerInset = UIEdgeInsets(top: 16, left: 12, bottom: 16, right: 12)
        output.layer.cornerRadius = 18
        output.isEditable = false
        output.isScrollEnabled = false
        output.isHidden = true
        stack.addArrangedSubview(output)
        let copy = UIButton(type: .system)
        copy.setTitle(text("Скопировать текст", "Copy text"), for: .normal)
        copy.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        copy.addTarget(self, action: #selector(copyText), for: .touchUpInside)
        stack.addArrangedSubview(copy)
        if applyDraft != nil {
            let apply = UIButton(type: .system); apply.setTitle(text("Применить к черновику", "Apply to draft"), for: .normal)
            apply.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
            apply.addTarget(self, action: #selector(useDraft), for: .touchUpInside); stack.addArrangedSubview(apply)
        }
    }
    private func addLiveTranslation(to stack: UIStackView) {
        guard let peerId, !peerId.isEmpty else { return }
        let heading = UILabel()
        heading.text = text("Перевод в реальном времени", "Live translation")
        heading.font = .preferredFont(forTextStyle: .subheadline)
        heading.adjustsFontForContentSizeCategory = true
        heading.textColor = theme.list.itemSecondaryTextColor
        heading.numberOfLines = 0
        stack.addArrangedSubview(heading)
        for (index, toggle) in [incomingSwitch, outgoingSwitch, draftSwitch].enumerated() {
            let label = UILabel()
            label.text = index == 0 ? text("Входящие сообщения", "Incoming messages") : index == 1 ? text("Мои отправленные сообщения", "My sent messages") : text("Мой текст при наборе", "My text while typing")
            label.font = .preferredFont(forTextStyle: .body)
            label.adjustsFontForContentSizeCategory = true; label.numberOfLines = 0
            label.textColor = theme.list.itemPrimaryTextColor
            toggle.tag = index
            toggle.onTintColor = theme.list.itemSwitchColors.contentColor
            toggle.tintColor = theme.list.itemSwitchColors.frameColor
            toggle.thumbTintColor = theme.list.itemSwitchColors.handleColor
            toggle.accessibilityLabel = label.text
            toggle.addTarget(self, action: #selector(toggleTranslation(_:)), for: .valueChanged)
            let row = UIStackView(arrangedSubviews: [label, toggle])
            row.axis = .horizontal; row.alignment = .center; row.spacing = 16
            row.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
            toggle.setContentHuggingPriority(.required, for: .horizontal)
            toggle.setContentCompressionResistancePriority(.required, for: .horizontal)
            stack.addArrangedSubview(row)
        }
        let settings = UIButton(type: .system)
        settings.setTitle(text("Языки и настройки перевода", "Languages and translation settings"), for: .normal)
        settings.titleLabel?.font = .preferredFont(forTextStyle: .subheadline)
        settings.titleLabel?.numberOfLines = 0
        settings.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        settings.addTarget(self, action: #selector(self.settings), for: .touchUpInside)
        stack.addArrangedSubview(settings)
        refreshTranslation()
    }
    private func refreshTranslation() {
        guard let peerId else { return }
        let options = NebulaTranslationPreferences.shared.options(account: accountId, peer: peerId)
        incomingSwitch.isOn = options.incoming; outgoingSwitch.isOn = options.outgoing; draftSwitch.isOn = options.draft
    }
    @objc private func toggleTranslation(_ sender: NebulaSwitchControl) {
        guard let peerId else { return }
        if sender.isOn && !NebulaLiveTranslation.ready {
            sender.isOn = false
            navigationController?.pushViewController(NebulaAiController(russian: russian, theme: theme, account: accountId, peer: peerId), animated: true)
            return
        }
        NebulaTranslationPreferences.shared.update(account: accountId, peer: peerId) {
            if sender.tag == 0 { $0.incoming = sender.isOn } else if sender.tag == 1 { $0.outgoing = sender.isOn } else { $0.draft = sender.isOn }
        }
    }
    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshTranslation()
    }
    private func updateLanguage() {
        languageButton.setTitle(text("Язык результата: ", "Result language: ") + NebulaResultLanguage.title(language, russian: russian) + "  ▾", for: .normal)
    }
    @objc private func chooseLanguage() {
        NebulaResultLanguage.show(from: self, selected: language, russian: russian, theme: theme) { [weak self] code in
            self?.language = code
            self?.updateLanguage()
        }
    }
    @objc private func performTool(_ sender: UIButton) {
        switch sender.tag {
        case 3:
            if #available(iOS 15.0, *) {
                navigationController?.pushViewController(NebulaSpeechController(text: result.isEmpty ? source : result, language: result.isEmpty ? (russian ? "ru" : "en") : language, russian: russian, theme: theme), animated: true)
            } else {
                let utterance = AVSpeechUtterance(string: result.isEmpty ? source : result); utterance.voice = AVSpeechSynthesisVoice(language: language); speech.speak(utterance)
            }
        case 4:
            navigationController?.pushViewController(NebulaTasksController(accountId: accountId, russian: russian, theme: theme, draft: result.isEmpty ? source : result), animated: true)
        default:
            let action: NebulaAiAction = sender.tag == 1 ? .translate : sender.tag == 2 ? .summarize : sender.tag == 5 ? .proofread : .ask
            if action == .translate || action == .proofread || action == .rewrite {
                let editor = NebulaAiEditorController(source: attributedSource, russian: russian, theme: theme, account: accountId, peer: peerId, action: action, resultLanguage: language, apply: { [weak self] value in
                    self?.result = value.string; self?.output.attributedText = value; self?.output.isHidden = false
                })
                navigationController?.pushViewController(editor, animated: true); return
            }
            let editor = NebulaAiChatController(russian: russian, initialText: source, action: action, applyResult: { [weak self] value in
                self?.result = value
                self?.output.text = value
                self?.output.isHidden = false
            }, theme: theme, resultLanguage: language, applyTitle: text("Использовать результат", "Use result"), account: accountId, peer: peerId)
            navigationController?.pushViewController(editor, animated: true)
        }
    }
    @objc private func settings() { navigationController?.pushViewController(NebulaTranslationController(account: accountId, peer: peerId, russian: russian, theme: theme), animated: true) }
    @objc private func useDraft() { guard !result.isEmpty else { return }; applyDraft?(result); dismiss(animated: true) }
    @objc private func copyText() { UIPasteboard.general.string = result.isEmpty ? source : result }
    @objc private func close() { dismiss(animated: true) }
    public override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); speech.stopSpeaking(at: .immediate) }
}

private final class NebulaToolButton: UIButton {
    override func layoutSubviews() {
        super.layoutSubviews()
        imageView?.frame = CGRect(x: (bounds.width - 26) / 2, y: 10, width: 26, height: 26)
        imageView?.contentMode = .scaleAspectFit
        titleLabel?.frame = CGRect(x: 8, y: 42, width: bounds.width - 16, height: bounds.height - 48)
    }
}
