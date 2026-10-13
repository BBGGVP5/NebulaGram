import Foundation
import UIKit
import NebulaSettingsContract
import TelegramPresentationData

public enum NebulaAiAction: CaseIterable {
    case ask, translate, rewrite, proofread, summarize

    func title(russian: Bool) -> String {
        switch self {
        case .ask: return russian ? "Спросить" : "Ask"
        case .translate: return russian ? "Перевести" : "Translate"
        case .rewrite: return russian ? "Изменить стиль" : "Rewrite"
        case .proofread: return russian ? "Проверить текст" : "Proofread"
        case .summarize: return russian ? "Краткий пересказ" : "Summarize"
        }
    }

    func instruction(russian: Bool) -> String {
        switch self {
        case .ask: return ""
        case .translate: return russian
            ? "Переведи следующий текст на русский. Сохрани смысл и форматирование. Верни только перевод:"
            : "Translate the following text into English. Preserve meaning and formatting. Return only the translation:"
        case .rewrite: return russian
            ? "Перепиши следующий текст ясно и естественно, сохранив смысл. Верни только готовый текст:"
            : "Rewrite the following text clearly and naturally without changing its meaning. Return only the revised text:"
        case .proofread: return russian
            ? "Исправь ошибки в следующем тексте, сохранив смысл и стиль. Верни только исправленный текст:"
            : "Proofread the following text. Preserve its meaning and style. Return only the corrected text:"
        case .summarize: return russian
            ? "Кратко перескажи следующий текст на русском. Выдели главные мысли:"
            : "Summarize the following text in English. Focus on the key points:"
        }
    }
}

/// Shared full-page and sheet chat. Requests run only on explicit send.
public final class NebulaAiChatController: UIViewController, UITextViewDelegate {
    public static var onDeviceAvailable: Bool { NebulaAiService.localModelAvailable }
    private let translationAccount: String
    private let translationPeer: String?
    private let ru: Bool
    private let theme: PresentationTheme?
    private var streamingAnswer: UITextView?
    private let applyResult: ((String) -> Void)?
    private let applyTitle: String?
    private var action: NebulaAiAction
    private var work: Task<Void, Never>?
    private var gate = NebulaAiRequestGate()
    private var conversation = NebulaAiConversation()
    private let chats = NebulaAiChats.shared
    private var chatId: UUID?
    private var restoring = false
    private var readinessTimer: Timer?
    private let scroll = UIScrollView()
    private let messages = UIStackView()
    private let composer = UITextView()
    private let providerLabel = UILabel()
    private let modelButton = UIButton(type: .system)
    private let chatsButton = UIButton(type: .system)
    private let actionButton = UIButton(type: .system)
    private let languageButton = UIButton(type: .system)
    private var resultLanguage: String
    private let sendButton = UIButton(type: .system)
    private let composerHint = UILabel()
    private var composerHeight: NSLayoutConstraint!
    private var keyboardBottom: NSLayoutConstraint?
    private var waiting: UIStackView?
    private var pulse: NebulaAiPulseView?
    private var isWelcome = true
    private var initial: String

    public init(russian: Bool, initialText: String = "", action: NebulaAiAction = .ask,
                applyResult: ((String) -> Void)? = nil, theme: PresentationTheme? = nil, resultLanguage: String? = nil, applyTitle: String? = nil,
                account: String = "", peer: String? = nil) {
        self.translationAccount = account; self.translationPeer = peer
        self.applyTitle = applyTitle
        self.ru = russian; self.action = action; self.applyResult = applyResult; self.theme = theme
        self.resultLanguage = resultLanguage ?? (russian ? "ru" : "en")
        self.initial = String(initialText.prefix(50_000))
        super.init(nibName: nil, bundle: nil)
        title = applyResult == nil ? "Nebula AI" : (russian ? "ИИ-редактор" : "AI editor")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }

    public static func presentSheet(from host: UIViewController, russian: Bool, theme: PresentationTheme? = nil) {
        guard host.presentedViewController == nil else { return }
        let chat = NebulaAiChatController(russian: russian, theme: theme)
        let navigation = UINavigationController(rootViewController: chat)
        navigation.modalPresentationStyle = .pageSheet
        if #available(iOS 15.0, *) {
            navigation.sheetPresentationController?.detents = [.large()]
            navigation.sheetPresentationController?.prefersGrabberVisible = true
            navigation.sheetPresentationController?.preferredCornerRadius = 28
        }
        host.present(navigation, animated: true)
    }

    public override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        view.backgroundColor = (theme?.list.plainBackgroundColor ?? .systemBackground).withAlphaComponent(1)
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = view.backgroundColor
        appearance.titleTextAttributes = [.foregroundColor: theme?.list.itemPrimaryTextColor ?? UIColor.label]
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        navigationController?.navigationBar.tintColor = theme?.list.itemAccentColor ?? view.tintColor
        if let theme { view.tintColor = theme.list.itemAccentColor }
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(image: UIImage(systemName: "ellipsis"), style: .plain, target: self, action: #selector(showChatMenu))
        navigationItem.rightBarButtonItem?.accessibilityLabel = text("Действия чата", "Chat actions")
        let layout = UIStackView(); layout.axis = .vertical; layout.spacing = 8
        layout.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(layout)
        NSLayoutConstraint.activate([
            layout.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            layout.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            layout.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8)
        ])
        if #available(iOS 15.0, *) {
            layout.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -8).isActive = true
        } else {
            keyboardBottom = layout.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -8)
            keyboardBottom?.isActive = true
            NotificationCenter.default.addObserver(self, selector: #selector(keyboardChanged(_:)), name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
        }
        providerLabel.font = .preferredFont(forTextStyle: .subheadline); providerLabel.textColor = theme?.list.itemPrimaryTextColor ?? .label
        providerLabel.numberOfLines = 2; providerLabel.textAlignment = .left
        providerLabel.adjustsFontForContentSizeCategory = true
        let chatActions = UIStackView(); chatActions.axis = .horizontal; chatActions.spacing = 8; chatActions.alignment = .center
        chatsButton.setImage(UIImage(systemName: "clock.arrow.circlepath"), for: .normal)
        chatsButton.titleLabel?.font = .preferredFont(forTextStyle: .caption1)
        chatsButton.addTarget(self, action: #selector(showChats), for: .touchUpInside)
        chatsButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        chatsButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        chatsButton.setContentHuggingPriority(.required, for: .horizontal)
        chatActions.addArrangedSubview(chatsButton)
        modelButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
        providerLabel.translatesAutoresizingMaskIntoConstraints = false; modelButton.addSubview(providerLabel)
        let chevron = UIImageView(image: UIImage(systemName: "chevron.down")); chevron.tintColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel; chevron.contentMode = .scaleAspectFit; chevron.translatesAutoresizingMaskIntoConstraints = false; modelButton.addSubview(chevron)
        NSLayoutConstraint.activate([providerLabel.leadingAnchor.constraint(equalTo: modelButton.leadingAnchor, constant: 4), providerLabel.trailingAnchor.constraint(equalTo: chevron.leadingAnchor, constant: -8), providerLabel.topAnchor.constraint(equalTo: modelButton.topAnchor, constant: 4), providerLabel.bottomAnchor.constraint(equalTo: modelButton.bottomAnchor, constant: -4),
            chevron.trailingAnchor.constraint(equalTo: modelButton.trailingAnchor, constant: -4), chevron.centerYAnchor.constraint(equalTo: modelButton.centerYAnchor), chevron.widthAnchor.constraint(equalToConstant: 12), chevron.heightAnchor.constraint(equalToConstant: 12), modelButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44)])
        chatActions.addArrangedSubview(modelButton)
        let freshButton = UIButton(type: .system)
        freshButton.setImage(UIImage(systemName: "plus.bubble"), for: .normal); freshButton.accessibilityLabel = text("Новый чат", "New chat")
        NebulaAiPanelUi.button(chatsButton, theme: theme); NebulaAiPanelUi.button(freshButton, theme: theme)
        freshButton.addTarget(self, action: #selector(newChat), for: .touchUpInside)
        freshButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        freshButton.widthAnchor.constraint(equalToConstant: 44).isActive = true
        chatActions.addArrangedSubview(freshButton)
        layout.addArrangedSubview(chatActions)
        actionButton.contentHorizontalAlignment = .leading
        actionButton.addTarget(self, action: #selector(pickAction), for: .touchUpInside)
        actionButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        layout.addArrangedSubview(actionButton)
        languageButton.contentHorizontalAlignment = .leading
        languageButton.titleLabel?.numberOfLines = 0
        languageButton.titleLabel?.font = .preferredFont(forTextStyle: .body)
        languageButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        languageButton.addTarget(self, action: #selector(pickLanguage), for: .touchUpInside)
        layout.addArrangedSubview(languageButton)
        scroll.keyboardDismissMode = .interactive; scroll.alwaysBounceVertical = true
        layout.addArrangedSubview(scroll)
        messages.axis = .vertical; messages.spacing = 18; messages.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(messages)
        NSLayoutConstraint.activate([
            messages.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            messages.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -16),
            messages.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            messages.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            messages.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor)
        ])
        let input = UIStackView(); input.axis = .horizontal; input.alignment = .bottom; input.spacing = 8
        input.isLayoutMarginsRelativeArrangement = true; input.layoutMargins = UIEdgeInsets(top: 6, left: 12, bottom: 6, right: 6)
        input.backgroundColor = theme?.list.itemBlocksBackgroundColor ?? .secondarySystemBackground; input.layer.cornerRadius = 26
        input.layer.borderWidth = 0
        setupText(composer); composer.isEditable = true; composer.isScrollEnabled = true
        composer.delegate = self; composer.text = initial; composer.accessibilityLabel = text("Сообщение для ИИ", "Message to AI")
        composerHeight = composer.heightAnchor.constraint(equalToConstant: 44); composerHeight.isActive = true
        composerHint.text = text("Напишите запрос…", "Write a prompt…"); composerHint.font = .preferredFont(forTextStyle: .body); composerHint.adjustsFontForContentSizeCategory = true; composerHint.textColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel; composerHint.isUserInteractionEnabled = false; composerHint.translatesAutoresizingMaskIntoConstraints = false; composer.addSubview(composerHint)
        composerHint.lineBreakMode = .byTruncatingTail
        NSLayoutConstraint.activate([composerHint.leadingAnchor.constraint(equalTo: composer.frameLayoutGuide.leadingAnchor, constant: 9), composerHint.trailingAnchor.constraint(lessThanOrEqualTo: composer.frameLayoutGuide.trailingAnchor, constant: -9), composerHint.topAnchor.constraint(equalTo: composer.frameLayoutGuide.topAnchor, constant: 10)])
        input.addArrangedSubview(composer)
        sendButton.backgroundColor = view.tintColor; sendButton.tintColor = theme?.list.itemCheckColors.foregroundColor ?? .white; sendButton.layer.cornerRadius = 22
        sendButton.widthAnchor.constraint(equalToConstant: 44).isActive = true
        sendButton.heightAnchor.constraint(equalToConstant: 44).isActive = true
        sendButton.addTarget(self, action: #selector(send), for: .touchUpInside)
        input.addArrangedSubview(sendButton); layout.addArrangedSubview(input)
        let note = label(text("ИИ может ошибаться. Важное проверяйте.", "AI can make mistakes. Check important details."), style: .caption2)
        note.textColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel; note.textAlignment = .center; layout.addArrangedSubview(note)
        NotificationCenter.default.addObserver(self, selector: #selector(appActive), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appInactive), name: UIApplication.willResignActiveNotification, object: nil)
        restoreChat(chats.current()); refreshStatus(); updateSend(); textViewDidChange(composer)
    }
    public override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        let selected = chats.current()
        if selected.id != chatId { restoreChat(selected) }
        appActive()
    }
    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated); appInactive()
        cancel(showMessage: work != nil)
    }
    deinit { work?.cancel(); readinessTimer?.invalidate(); NotificationCenter.default.removeObserver(self) }
    @objc private func appActive() {
        guard isViewLoaded, view.window != nil else { return }
        refreshStatus(); pulse?.start()
        readinessTimer?.invalidate()
        readinessTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in self?.refreshStatus() }
    }
    @objc private func appInactive() { readinessTimer?.invalidate(); readinessTimer = nil; pulse?.stop() }
    private func refreshStatus() {
        let settings = NebulaAiSettings.shared
        let value = !settings.enabled ? text("ИИ выключен · откройте настройки", "AI is off · open settings")
            : settings.provider == .appleIntelligence ? NebulaAiService.localModelStatus(russian: ru)
            : settings.provider.title + " · " + (settings.model(for: settings.provider).isEmpty ? text("Нужна настройка", "Setup needed") : settings.model(for: settings.provider))
        if providerLabel.text != value { providerLabel.text = value }
        modelButton.accessibilityLabel = value + ". " + text("Изменить подключение", "Change connection")
        actionButton.setTitle(action.title(russian: ru) + "  ▾", for: .normal)
        languageButton.setTitle(text("Язык результата: ", "Result language: ") + NebulaResultLanguage.title(resultLanguage, russian: ru) + "  ▾", for: .normal)
        languageButton.isHidden = action != .translate && action != .summarize
        actionButton.isHidden = action == .ask && applyResult == nil
    }
    private func label(_ value: String, style: UIFont.TextStyle = .body) -> UILabel {
        let v = UILabel(); v.text = value; v.font = .preferredFont(forTextStyle: style)
        v.numberOfLines = 0; v.adjustsFontForContentSizeCategory = true; v.textColor = theme?.list.itemPrimaryTextColor ?? .label; return v
    }
    private func setupText(_ v: UITextView) {
        v.font = .preferredFont(forTextStyle: .body); v.adjustsFontForContentSizeCategory = true
        v.textColor = theme?.list.itemPrimaryTextColor ?? .label; v.backgroundColor = .clear; v.isEditable = false; v.isScrollEnabled = false
        v.textContainerInset = UIEdgeInsets(top: 10, left: 4, bottom: 10, right: 4)
    }
    private func removeMessages() { for v in messages.arrangedSubviews { messages.removeArrangedSubview(v); v.removeFromSuperview() } }
    private func showWelcome() {
        removeMessages(); isWelcome = true
        let icon = UIImageView(image: UIImage(systemName: "sparkles")); icon.contentMode = .left; icon.tintColor = .systemPurple
        icon.heightAnchor.constraint(equalToConstant: 48).isActive = true; messages.addArrangedSubview(icon)
        messages.addArrangedSubview(label(text("С чего начнём?", "Where shall we start?"), style: .largeTitle))
        let hint = label(text("Задайте вопрос, разберите текст или придумайте что-нибудь вместе.", "Ask a question, work through a text, or create something together."))
        hint.textColor = .secondaryLabel; messages.addArrangedSubview(hint)
        for suggestion in [text("Объясни простыми словами", "Explain in simple terms"), text("Помоги написать текст", "Help me write"), text("Предложи идеи", "Suggest ideas")] {
            let button = NebulaAiChatButton(title: suggestion + "  ↗") { [weak self] in
                self?.composer.text = suggestion + " "; self?.composer.becomeFirstResponder()
                if let self = self { self.textViewDidChange(self.composer) }
            }
            button.backgroundColor = theme?.list.itemBlocksBackgroundColor ?? .secondarySystemBackground; button.layer.cornerRadius = 18
            messages.addArrangedSubview(button)
        }
    }
    private func restoreChat(_ chat: NebulaAiChatSession) {
        chatId = chat.id
        conversation = NebulaAiConversation()
        conversation.select(chat.identity)
        if chat.turns.isEmpty { showWelcome() }
        else {
            removeMessages(); isWelcome = false; restoring = true
            for turn in chat.turns {
                let reply = action == .ask ? NebulaAiChatReply.clean(turn.output) : turn.output
                conversation.append(input: turn.input, output: reply)
                appendUser(turn.input); appendAnswer(reply)
            }
            restoring = false
            view.layoutIfNeeded()
            scroll.setContentOffset(CGPoint(x: 0, y: max(0, scroll.contentSize.height - scroll.bounds.height)), animated: false)
        }
        let count = chats.list().filter { !$0.turns.isEmpty }.count
        updateChatsCount(count)
    }
    private func appendUser(_ text: String) {
        messages.addArrangedSubview(NebulaAiChatBubble(value: text, foreground: theme?.list.itemPrimaryTextColor ?? .label, surface: theme?.list.itemBlocksBackgroundColor ?? .secondarySystemBackground))
    }
    private func appendAnswer(_ raw: String, actions: Bool = true) {
        let result = UITextView(); setupText(result)
        let parsed = NebulaAiMarkdown.parse(raw)
        let style = NSMutableParagraphStyle(); style.lineSpacing = 4
        let rich = NSMutableAttributedString(string: parsed.text, attributes: [.font: UIFont.preferredFont(forTextStyle: .body), .foregroundColor: theme?.list.itemPrimaryTextColor ?? UIColor.label, .paragraphStyle: style])
        for mark in parsed.marks {
            let body = UIFont.preferredFont(forTextStyle: .body)
            let font: UIFont
            switch mark.kind {
            case .code: font = .monospacedSystemFont(ofSize: body.pointSize, weight: .regular)
            case .heading: font = .preferredFont(forTextStyle: .title3)
            case .bold: font = .boldSystemFont(ofSize: body.pointSize)
            case .italic: font = .italicSystemFont(ofSize: body.pointSize)
            }
            rich.addAttribute(.font, value: font, range: mark.range)
        }
        result.attributedText = rich; messages.addArrangedSubview(result)
        if actions {
            let controls = UIStackView(); controls.axis = .horizontal; controls.spacing = 8; controls.alignment = .center
            let copied = label(text("Скопировано", "Copied"), style: .caption1); copied.textColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel; copied.isHidden = true
            let copy = NebulaAiChatButton(title: "") { UIPasteboard.general.string = raw; copied.isHidden = false; UIAccessibility.post(notification: .announcement, argument: copied.text); DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak copied] in copied?.isHidden = true } }
            copy.setImage(UIImage(systemName: "doc.on.doc"), for: .normal); copy.tintColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel; copy.accessibilityLabel = text("Копировать ответ", "Copy answer"); copy.widthAnchor.constraint(equalToConstant: 44).isActive = true
            controls.addArrangedSubview(copy); controls.addArrangedSubview(copied)
            if applyResult != nil {
                controls.addArrangedSubview(NebulaAiChatButton(title: applyTitle ?? text("Вставить в черновик", "Use in draft")) { [weak self] in self?.applyResult?(raw); self?.close() })
            }
            controls.addArrangedSubview(UIView())
            messages.addArrangedSubview(controls)
        }
        if !restoring {
            view.layoutIfNeeded()
            let frame = result.convert(result.bounds, to: scroll)
            scroll.setContentOffset(CGPoint(x: 0, y: max(0, min(frame.minY, scroll.contentSize.height - scroll.bounds.height))), animated: !UIAccessibility.isReduceMotionEnabled)
            if !UIAccessibility.isReduceMotionEnabled { result.alpha = 0; UIView.animate(withDuration: 0.22) { result.alpha = 1 } }
            UIAccessibility.post(notification: .announcement, argument: text("Ответ готов", "Response ready"))
        }
    }
    private func startWaiting() {
        let row = UIStackView(); row.axis = .horizontal; row.spacing = 12; row.alignment = .center
        let glyph = NebulaAiPulseView(); glyph.tintColor = view.tintColor; row.addArrangedSubview(glyph); glyph.widthAnchor.constraint(equalToConstant: 32).isActive = true
        glyph.heightAnchor.constraint(equalToConstant: 32).isActive = true; row.addArrangedSubview(label(text("Думаю…", "Thinking…")))
        messages.addArrangedSubview(row); waiting = row; pulse = glyph; glyph.start()
        view.layoutIfNeeded(); scroll.scrollRectToVisible(row.convert(row.bounds, to: scroll), animated: true)
    }
    private func showPartial(_ text: String) {
        if streamingAnswer == nil {
            pulse?.stop()
            waiting?.isHidden = true
            let result = UITextView(); setupText(result)
            messages.addArrangedSubview(result); streamingAnswer = result
        }
        let nearBottom = scroll.contentOffset.y + scroll.bounds.height >= scroll.contentSize.height - 100
        streamingAnswer?.text = action == .ask ? NebulaAiChatReply.clean(text, partial: true) : text
        view.layoutIfNeeded()
        if nearBottom, let result = streamingAnswer { scroll.scrollRectToVisible(result.convert(result.bounds, to: scroll), animated: false) }
    }
    private func finish() {
        if let partial = streamingAnswer { messages.removeArrangedSubview(partial); partial.removeFromSuperview() }; streamingAnswer = nil
        gate.cancel(); work = nil; pulse?.stop(); pulse = nil
        if let waiting = waiting { messages.removeArrangedSubview(waiting); waiting.removeFromSuperview() }
        waiting = nil; updateSend()
    }
    private func updateSend() {
        sendButton.setImage(UIImage(systemName: work == nil ? "arrow.up" : "stop.fill"), for: .normal)
        sendButton.accessibilityLabel = work == nil ? text("Отправить", "Send") : text("Остановить", "Stop")
        actionButton.isEnabled = work == nil
        sendButton.isEnabled = work != nil || !composer.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        sendButton.alpha = sendButton.isEnabled ? 1 : 0.4
        composerHint.isHidden = !composer.text.isEmpty
    }
    @objc private func send() {
        if work != nil { cancel(showMessage: true); return }
        let input = composer.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { return }
        let settings = NebulaAiSettings.shared
        guard settings.enabled && settings.isConfigured() else { openSettings(); return }
        let instruction: String
        if action == .translate {
            instruction = "Translate the following text into language code \(resultLanguage). Preserve meaning and formatting. Return only the translation:"
        } else if action == .summarize {
            instruction = "Summarize the following text in language code \(resultLanguage). Focus on the key points:"
        } else { instruction = action.instruction(russian: ru) }
        let current = instruction.isEmpty ? input : instruction + "\n\n" + input
        let roleInstructions = action == .ask ? (settings.roles.selected(russian: ru)?.instruction ?? settings.instructions) : ""
        let service = NebulaAiService(instructions: roleInstructions)
        let identity = settings.conversationIdentity(action: String(describing: action), language: resultLanguage, instructions: roleInstructions)
        let selectedChat = chats.current()
        if selectedChat.id != chatId { restoreChat(selectedChat) }
        if !selectedChat.identity.isEmpty && selectedChat.identity != identity { restoreChat(chats.fresh()) }
        conversation.select(identity)
        guard let request = conversation.request(current, limit: settings.provider == .appleIntelligence ? 9_500 : 49_000) else {
            let alert = UIAlertController(title: text("Сократите сообщение для выбранной модели", "Shorten this message for the selected model"), message: nil, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true); return
        }
        if isWelcome { removeMessages(); isWelcome = false }
        while messages.arrangedSubviews.count > 36 { let old = messages.arrangedSubviews[0]; messages.removeArrangedSubview(old); old.removeFromSuperview() }
        appendUser(input); composer.text = ""; textViewDidChange(composer); startWaiting()
        let provider = settings.provider.title; let id = gate.begin()
        work = Task { [weak self] in
            guard let self = self else { return }
            do {
                let generated = try await service.generate(input: request) { [weak self] partial in
                    await MainActor.run {
                        guard let self, self.gate.accepts(id) else { return }
                        self.showPartial(partial)
                    }
                }
                guard !Task.isCancelled, self.gate.accepts(id) else { return }
                let value = self.action == .ask ? NebulaAiChatReply.clean(generated) : generated
                guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw NebulaAiServiceError.invalidResponse }
                self.finish(); self.conversation.append(input: current, output: value); self.appendAnswer(value)
                if let chatId = self.chatId {
                    self.chats.append(id: chatId, identity: identity, input: input, output: value)
                    let count = self.chats.list().filter { !$0.turns.isEmpty }.count
                    self.updateChatsCount(count)
                }
                _ = NebulaAiHistory.shared.append(provider: provider, input: input, output: value)
            } catch {
                guard !Task.isCancelled, self.gate.accepts(id) else { return }
                self.finish(); self.appendAnswer(NebulaAiService.message(for: error, russian: self.ru), actions: false)
                self.messages.addArrangedSubview(NebulaAiChatButton(title: self.text("Изменить запрос", "Edit request")) { [weak self] in
                    self?.composer.text = input; self?.composer.becomeFirstResponder()
                    if let self = self { self.textViewDidChange(self.composer) }
                })
            }
        }
        updateSend()
    }
    private func cancel(showMessage: Bool) {
        let running = work != nil; gate.cancel(); work?.cancel(); finish()
        if running && showMessage { appendAnswer(text("Ответ остановлен", "Response stopped"), actions: false) }
    }
    @objc private func newChat() {
        cancel(showMessage: false); composer.text = ""; textViewDidChange(composer)
        restoreChat(chats.fresh())
    }
    @objc private func showChats() {
        let sessions = chats.list()
        NebulaChoiceController.show(from: self, title: text("Чаты Nebula AI", "Nebula AI chats"),
            choices: sessions.map { $0.title.isEmpty ? text("Новый чат", "New chat") : $0.title },
            selected: sessions.firstIndex(where: { $0.id == chatId }), russian: ru, selectionIndicatorVisible: false, theme: theme) { [weak self] index in
                guard let self = self, sessions.indices.contains(index), let selected = self.chats.select(sessions[index].id) else { return }
                self.cancel(showMessage: false)
                self.composer.text = ""; self.textViewDidChange(self.composer)
                self.restoreChat(selected)
            }
    }
    @objc private func openSettings() { navigationController?.pushViewController(NebulaAiController(russian: ru, theme: theme, account: translationAccount, peer: translationPeer), animated: true) }
    private func updateChatsCount(_ count: Int) {
        chatsButton.setTitle(count == 0 ? nil : count > 99 ? "99+" : " \(count)", for: .normal)
        chatsButton.accessibilityLabel = text("История чатов", "Chat history") + (count == 0 ? "" : " · \(count)")
    }
    @objc private func showChatMenu() {
        let menu = UIAlertController(title: "Nebula AI", message: nil, preferredStyle: .actionSheet)
        let mode = UIAlertAction(title: text("Действие с текстом", "Text action"), style: .default) { [weak self] _ in self?.pickAction() }; mode.isEnabled = work == nil; menu.addAction(mode)
        menu.addAction(UIAlertAction(title: text("Настройки ИИ", "AI settings"), style: .default) { [weak self] _ in self?.openSettings() })
        menu.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel)); menu.popoverPresentationController?.barButtonItem = navigationItem.rightBarButtonItem; present(menu, animated: true)
    }
    @objc private func pickLanguage() {
        NebulaResultLanguage.show(from: self, selected: resultLanguage, russian: ru, theme: theme) { [weak self] code in
            guard let self else { return }
            self.cancel(showMessage: false)
            self.resultLanguage = code
            self.refreshStatus()
        }
    }
    @objc private func pickAction() {
        let options = NebulaAiAction.allCases
        NebulaChoiceController.show(from: self, title: text("Что сделать с текстом?", "What should AI do?"), choices: options.map { $0.title(russian: ru) }, selected: options.firstIndex(of: action), russian: ru, theme: theme) { [weak self] index in
            self?.action = options[index]; self?.refreshStatus()
        }
    }
    @objc private func close() {
        cancel(showMessage: false)
        if let navigation = navigationController, navigation.viewControllers.first !== self { navigation.popViewController(animated: true) }
        else { dismiss(animated: true) }
    }
    public func textViewDidChange(_ textView: UITextView) {
        let width = max(120, composer.bounds.width)
        composerHeight.constant = min(144, max(44, composer.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height))
        updateSend()
    }
    public func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
        return (textView.text as NSString).replacingCharacters(in: range, with: text).count <= 50_000
    }
    @objc private func keyboardChanged(_ notification: Notification) {
        guard let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
        let local = view.convert(frame, from: nil)
        let overlap = max(0, view.bounds.maxY - local.minY - view.safeAreaInsets.bottom)
        keyboardBottom?.constant = -8 - overlap
        UIView.animate(withDuration: 0.25) { self.view.layoutIfNeeded() }
    }
}

private final class NebulaAiChatButton: UIButton {
    private let action: () -> Void
    init(title: String, action: @escaping () -> Void) {
        self.action = action; super.init(frame: .zero)
        setTitle(title, for: .normal); setTitleColor(tintColor, for: .normal)
        titleLabel?.font = .preferredFont(forTextStyle: .subheadline); titleLabel?.numberOfLines = 0
        titleLabel?.adjustsFontForContentSizeCategory = true
        heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        addTarget(self, action: #selector(activate), for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func tintColorDidChange() { super.tintColorDidChange(); setTitleColor(tintColor, for: .normal) }
    @objc private func activate() { action() }
}
private final class NebulaAiPulseView: UIImageView {
    init() {
        super.init(image: UIImage(systemName: "sparkles")); tintColor = .systemPurple; contentMode = .scaleAspectFit
        NotificationCenter.default.addObserver(self, selector: #selector(motionChanged), name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func didMoveToWindow() { super.didMoveToWindow(); window == nil ? stop() : start() }
    @objc private func motionChanged() { stop(); if window != nil { start() } }
    func start() {
        guard !UIAccessibility.isReduceMotionEnabled, layer.animation(forKey: "thinking") == nil else { return }
        let animation = CABasicAnimation(keyPath: "transform.scale"); animation.fromValue = 0.78; animation.toValue = 1.05
        animation.duration = 0.8; animation.autoreverses = true; animation.repeatCount = .infinity
        animation.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut); layer.add(animation, forKey: "thinking")
    }
    func stop() { layer.removeAnimation(forKey: "thinking") }
}
