import UIKit
import NebulaSettingsContract
import TelegramPresentationData

public final class NebulaAiEditorController: UIViewController {
    private let source: NSAttributedString
    private let ru: Bool
    private let theme: PresentationTheme
    private let apply: ((NSAttributedString) -> Void)?
    private let account: String
    private let peer: String?
    private var mode = 0
    private var style = 0
    private var customStyle = ""
    private var language: String
    private var result: NSAttributedString?
    private var work: Task<Void, Never>?
    private var revision = 0
    private let languageButton = UIButton(type: .system)
    private let styleScroll = UIScrollView()
    private let styleStack = UIStackView()
    private let textView = UITextView()
    private let caption = UILabel()
    private let primary = UIButton(type: .system)
    private let reset = UIButton(type: .system)
    private var styleButtons: [UIButton] = []
    public init(source: NSAttributedString, russian: Bool, theme: PresentationTheme, account: String, peer: String? = nil, action: NebulaAiAction = .translate, apply: ((NSAttributedString) -> Void)? = nil) {
        self.source = NSAttributedString(attributedString: source); ru = russian; self.theme = theme; self.account = account; self.peer = peer; self.apply = apply
        language = russian ? "ru" : "en"
        mode = action == .proofread ? 2 : action == .rewrite ? 1 : 0
        super.init(nibName: nil, bundle: nil); title = "Nebula AI"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear; view.tintColor = theme.list.itemAccentColor
        overrideUserInterfaceStyle = theme.overallDarkAppearance ? .dark : .light
        let material = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterial))
        material.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(material)
        NSLayoutConstraint.activate([material.leadingAnchor.constraint(equalTo: view.leadingAnchor), material.trailingAnchor.constraint(equalTo: view.trailingAnchor), material.topAnchor.constraint(equalTo: view.topAnchor), material.bottomAnchor.constraint(equalTo: view.bottomAnchor)])
        if UIAccessibility.isReduceTransparencyEnabled { material.effect = nil; material.backgroundColor = theme.list.blocksBackgroundColor }
        let appearance = UINavigationBarAppearance(); appearance.configureWithTransparentBackground()
        appearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        navigationItem.standardAppearance = appearance; navigationItem.scrollEdgeAppearance = appearance
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(image: UIImage(systemName: "slider.horizontal.3"), style: .plain, target: self, action: #selector(settings))
        let scroll = UIScrollView(); scroll.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(scroll)
        let stack = UIStackView(); stack.axis = .vertical; stack.spacing = 16; stack.translatesAutoresizingMaskIntoConstraints = false; scroll.addSubview(stack)
        primary.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(primary)
        primary.titleLabel?.font = .preferredFont(forTextStyle: .headline); primary.titleLabel?.adjustsFontForContentSizeCategory = true
        primary.titleLabel?.numberOfLines = 2; primary.backgroundColor = theme.list.itemAccentColor
        primary.setTitleColor(theme.list.itemCheckColors.foregroundColor, for: .normal); primary.layer.cornerRadius = 24
        primary.contentEdgeInsets = UIEdgeInsets(top: 16, left: 20, bottom: 16, right: 20)
        primary.addTarget(self, action: #selector(run), for: .touchUpInside)
        NSLayoutConstraint.activate([
            primary.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16), primary.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16), primary.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16), primary.heightAnchor.constraint(greaterThanOrEqualToConstant: 52),
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16), scroll.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor), scroll.bottomAnchor.constraint(equalTo: primary.topAnchor, constant: -16),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 16), stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -16), stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor), stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -16), stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32)
        ])
        let segments = NebulaEditorSegments(titles: [text("Перевод", "Translate"), text("Стили", "Styles"), text("Исправить", "Correct")], accent: theme.list.itemAccentColor, selected: mode)
        segments.changed = { [weak self] value in self?.mode = value; self?.resetResult(); self?.refresh(animated: true) }; stack.addArrangedSubview(segments)
        languageButton.titleLabel?.font = .preferredFont(forTextStyle: .body); languageButton.titleLabel?.adjustsFontForContentSizeCategory = true
        languageButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        languageButton.addTarget(self, action: #selector(chooseLanguage), for: .touchUpInside); stack.addArrangedSubview(languageButton)
        styleScroll.showsHorizontalScrollIndicator = false; styleStack.axis = .horizontal; styleStack.spacing = 8
        styleScroll.addSubview(styleStack); styleStack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([styleStack.leadingAnchor.constraint(equalTo: styleScroll.contentLayoutGuide.leadingAnchor), styleStack.trailingAnchor.constraint(equalTo: styleScroll.contentLayoutGuide.trailingAnchor), styleStack.topAnchor.constraint(equalTo: styleScroll.contentLayoutGuide.topAnchor), styleStack.bottomAnchor.constraint(equalTo: styleScroll.contentLayoutGuide.bottomAnchor), styleStack.heightAnchor.constraint(equalTo: styleScroll.frameLayoutGuide.heightAnchor), styleScroll.heightAnchor.constraint(equalToConstant: max(52, UIFont.preferredFont(forTextStyle: .body).lineHeight + 28))])
        let styles = ru ? ["💼 Деловой", "👋 Дружелюбный", "✂️ Кратко", "✨ Живее", "✍️ Свой"] : ["💼 Formal", "👋 Friendly", "✂️ Concise", "✨ Lively", "✍️ Custom"]
        for (index, title) in styles.enumerated() {
            let button = UIButton(type: .system); button.tag = index; button.setTitle(title, for: .normal)
            button.titleLabel?.font = .preferredFont(forTextStyle: .body); button.titleLabel?.adjustsFontForContentSizeCategory = true
            button.contentEdgeInsets = UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16); button.layer.cornerRadius = 20
            button.addTarget(self, action: #selector(chooseStyle(_:)), for: .touchUpInside); styleStack.addArrangedSubview(button); styleButtons.append(button)
        }
        stack.addArrangedSubview(styleScroll)
        let card = UIStackView(); card.axis = .vertical; card.spacing = 12; card.isLayoutMarginsRelativeArrangement = true
        card.layoutMargins = UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)
        card.backgroundColor = theme.list.itemBlocksBackgroundColor.withAlphaComponent(0.5); card.layer.cornerRadius = 24
        caption.font = .preferredFont(forTextStyle: .subheadline); caption.adjustsFontForContentSizeCategory = true; caption.textColor = theme.list.itemSecondaryTextColor
        card.addArrangedSubview(caption)
        textView.font = .preferredFont(forTextStyle: .body); textView.adjustsFontForContentSizeCategory = true
        textView.textColor = theme.list.itemPrimaryTextColor; textView.backgroundColor = .clear; textView.isEditable = false; textView.isScrollEnabled = false
        textView.heightAnchor.constraint(greaterThanOrEqualToConstant: 140).isActive = true; card.addArrangedSubview(textView); stack.addArrangedSubview(card)
        reset.setTitle(text("Вернуться к оригиналу", "Return to original"), for: .normal); reset.addTarget(self, action: #selector(resetResult), for: .touchUpInside); stack.addArrangedSubview(reset)
        refresh(animated: false)
    }
    private func refresh(animated: Bool) {
        let update = {
            self.languageButton.isHidden = self.mode != 0; self.styleScroll.isHidden = self.mode != 1
            self.languageButton.setTitle(NebulaResultLanguage.title(self.language, russian: self.ru) + "  ›", for: .normal)
            self.caption.text = self.result == nil ? self.text("Оригинал", "Original") : self.text("Результат", "Result")
            self.textView.attributedText = self.result ?? self.source
            self.textView.font = .preferredFont(forTextStyle: .body); self.textView.textColor = self.theme.list.itemPrimaryTextColor
            self.reset.isHidden = self.result == nil
            self.primary.setTitle(self.work != nil ? self.text("Остановить", "Stop") : self.result != nil ? self.text(self.apply == nil ? "Копировать" : "Применить", self.apply == nil ? "Copy" : "Apply") : [self.text("Перевести", "Translate"), self.text("Изменить стиль", "Change style"), self.text("Исправить", "Correct")][self.mode], for: .normal)
            for (index, button) in self.styleButtons.enumerated() {
                button.backgroundColor = (index == self.style ? self.theme.list.itemAccentColor : self.theme.list.itemBlocksBackgroundColor).withAlphaComponent(index == self.style ? 0.24 : 0.4)
                button.accessibilityTraits = index == self.style ? [.button, .selected] : .button
            }
            self.view.layoutIfNeeded()
        }
        if animated && !UIAccessibility.isReduceMotionEnabled { UIView.animate(withDuration: 0.22, animations: update) } else { update() }
    }
    @objc private func chooseLanguage() {
        NebulaResultLanguage.show(from: self, selected: language, russian: ru, theme: theme) { [weak self] value in self?.language = value; self?.resetResult() }
    }
    @objc private func chooseStyle(_ sender: UIButton) {
        if sender.tag < 4 { style = sender.tag; resetResult(); return }
        let alert = UIAlertController(title: text("Свой стиль", "Custom style"), message: nil, preferredStyle: .alert)
        alert.addTextField { $0.text = self.customStyle; $0.placeholder = self.text("Как изменить текст", "How to change the text") }
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Выбрать", "Select"), style: .default) { [weak self, weak alert] _ in
            guard let self, let text = alert?.textFields?.first?.text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            self.customStyle = String(text.prefix(2000)); self.style = 4; self.resetResult()
        }); present(alert, animated: true)
    }
    @objc private func resetResult() { revision += 1; work?.cancel(); work = nil; result = nil; if isViewLoaded { refresh(animated: false) } }
    @objc private func run() {
        if work != nil { resetResult(); return }
        if let result { if let apply { apply(result); if navigationController?.viewControllers.first === self { dismiss(animated: true) } else { navigationController?.popViewController(animated: true) } } else { UIPasteboard.general.string = result.string }; return }
        guard NebulaLiveTranslation.ready else { settings(); return }
        let instruction: String
        if mode == 0 { instruction = "Translate into language code \(language)." }
        else if mode == 2 { instruction = "Correct spelling and grammar. Preserve the author's language, meaning and tone." }
        else { instruction = ["Rewrite in a professional business tone.", "Rewrite in a friendly natural tone.", "Make the text concise without losing key facts.", "Make the wording vivid and engaging without inventing facts.", customStyle][style] + " Preserve the original language." }
        revision += 1; let version = revision, connection = NebulaLiveTranslation.connectionIdentity
        work = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let value = try await NebulaRichEditorTransform.generate(self.source, instruction: instruction)
                guard !Task.isCancelled, self.revision == version, connection == NebulaLiveTranslation.connectionIdentity else { return }
                self.work = nil; self.result = value; self.refresh(animated: true)
            } catch {
                guard !Task.isCancelled, self.revision == version else { return }
                self.work = nil; self.refresh(animated: false)
                let alert = UIAlertController(title: self.text("Не удалось обработать текст", "Could not process text"), message: NebulaAiService.message(for: error, russian: self.ru), preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "OK", style: .default)); self.present(alert, animated: true)
            }
        }
        refresh(animated: false)
    }
    @objc private func settings() { resetResult(); navigationController?.pushViewController(NebulaAiController(russian: ru, theme: theme, account: account, peer: peer), animated: true) }
    @objc private func close() { resetResult(); dismiss(animated: true) }
    public override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); revision += 1; work?.cancel(); work = nil }
    deinit { work?.cancel() }
}

enum NebulaRichEditorTransform {
    static func generate(_ source: NSAttributedString, instruction: String) async throws -> NSAttributedString {
        let transform = try NebulaRichTextTransform(source)
        guard !source.string.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw NebulaAiServiceError.emptyInput }
        let service = NebulaAiService(instructions: instruction + "\n" + transform.instructions)
        let raw = try await service.generate(input: transform.encoded)
        try Task.checkCancellation()
        if let value = try? transform.decode(raw) { return value }
        // Bound fallback requests; a complex malformed result remains unapplied.
        guard transform.segments.count <= 16 else { throw NebulaAiServiceError.invalidResponse }
        let fallback = NebulaAiService(instructions: instruction + " Preserve emoji, line breaks and meaning. Return only the transformed text.")
        var values: [String] = []
        for part in transform.segments {
            try Task.checkCancellation()
            let text = part.original.string
            if part.protected || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { values.append(text) }
            else {
                let translated = try await fallback.generate(input: text)
                let leading = String(text.prefix(while: { $0.isWhitespace }))
                let trailing = String(text.reversed().prefix(while: { $0.isWhitespace }).reversed())
                values.append(leading + translated + trailing)
            }
        }
        return try transform.combining(values)
    }
}
