import UIKit
import AVFoundation
import TelegramPresentationData

public final class NebulaMessageToolsController: UIViewController {
    private let russian: Bool
    private let theme: PresentationTheme
    private let source: String
    private let accountId: String
    private let peerId: String?
    private let applyDraft: ((String) -> Void)?
    private var language: String
    private var result = ""
    private let output = UITextView()
    private let languageButton = UIButton(type: .system)
    private let speech = AVSpeechSynthesizer()

    public init(text: String, russian: Bool, theme: PresentationTheme, accountId: String, peerId: String? = nil, applyDraft: ((String) -> Void)? = nil) {
        self.source = String(text.prefix(50_000))
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
        let titles = [text("Спросить ИИ", "Ask AI"), text("Перевести", "Translate"), text("Сократить", "Summarize"), text("Озвучить", "Read aloud"), text("В задачу", "Create task")]
        let symbols = ["sparkles", "character.bubble", "text.alignleft", "speaker.wave.2", "checkmark.circle"]
        let buttons = titles.enumerated().map { index, title -> UIButton in
            let button = NebulaToolButton(type: .system)
            button.tag = index
            button.setTitle(title, for: .normal)
            button.setImage(UIImage(systemName: symbols[index]), for: .normal)
            button.backgroundColor = theme.list.itemBlocksBackgroundColor
            button.tintColor = theme.list.itemAccentColor
            button.setTitleColor(theme.list.itemPrimaryTextColor, for: .normal)
            button.addTarget(self, action: #selector(performTool(_:)), for: .touchUpInside)
            return button
        }
        stack.addArrangedSubview(NebulaActionGrid(buttons: buttons))
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
            if speech.isSpeaking { speech.stopSpeaking(at: .immediate); return }
            let utterance = AVSpeechUtterance(string: result.isEmpty ? source : result)
            if !result.isEmpty { utterance.voice = AVSpeechSynthesisVoice(language: language) }
            speech.speak(utterance)
        case 4:
            navigationController?.pushViewController(NebulaTasksController(accountId: accountId, russian: russian, theme: theme, draft: result.isEmpty ? source : result), animated: true)
        default:
            let action: NebulaAiAction = sender.tag == 1 ? .translate : sender.tag == 2 ? .summarize : .ask
            let editor = NebulaAiChatController(russian: russian, initialText: source, action: action, applyResult: { [weak self] value in
                self?.result = value
                self?.output.text = value
                self?.output.isHidden = false
            }, theme: theme, resultLanguage: language, applyTitle: text("Использовать результат", "Use result"))
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
        imageView?.frame = CGRect(x: (bounds.width - 28) / 2, y: 15, width: 28, height: 28)
        imageView?.contentMode = .scaleAspectFit
        titleLabel?.frame = CGRect(x: 8, y: 50, width: bounds.width - 16, height: bounds.height - 56)
    }
}
