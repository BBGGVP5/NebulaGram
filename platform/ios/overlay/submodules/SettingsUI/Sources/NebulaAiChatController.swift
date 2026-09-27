import Foundation
import UIKit
import NebulaSettingsContract

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

/// A native, user-initiated AI editor. It never sends its output to a Telegram
/// chat; callers may explicitly apply the result to an existing draft.
public final class NebulaAiChatController: UIViewController {
    public static var onDeviceAvailable: Bool { NebulaAiService.localModelAvailable }
    private let ru: Bool
    private let service = NebulaAiService()
    private let history = NebulaAiHistory.shared
    private let applyResult: ((String) -> Void)?
    private var action: NebulaAiAction
    private var work: Task<Void, Never>?
    private var answer = ""

    private let scroll = UIScrollView()
    private let stack = UIStackView()
    private let source = UITextView()
    private let result = UITextView()
    private let providerLabel = UILabel()
    private let actionButton = UIButton(type: .system)
    private let sendButton = UIButton(type: .system)
    private let copyButton = UIButton(type: .system)
    private let applyButton = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .medium)

    public init(russian: Bool, initialText: String = "", action: NebulaAiAction = .ask,
                applyResult: ((String) -> Void)? = nil) {
        self.ru = russian
        self.action = action
        self.applyResult = applyResult
        super.init(nibName: nil, bundle: nil)
        source.text = String(initialText.prefix(50_000))
        title = russian ? "ИИ-помощник" : "AI assistant"
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))

        scroll.translatesAutoresizingMaskIntoConstraints = false
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 14
        stack.layoutMargins = UIEdgeInsets(top: 20, left: 18, bottom: 28, right: 18)
        stack.isLayoutMarginsRelativeArrangement = true
        view.addSubview(scroll)
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor)
        ])

        addHeading(text("Текст запроса", "Request text"))
        setupTextView(source, editable: true)
        source.heightAnchor.constraint(greaterThanOrEqualToConstant: 130).isActive = true
        stack.addArrangedSubview(source)

        providerLabel.font = .preferredFont(forTextStyle: .footnote)
        providerLabel.textColor = .secondaryLabel
        providerLabel.numberOfLines = 0
        providerLabel.adjustsFontForContentSizeCategory = true
        stack.addArrangedSubview(providerLabel)

        actionButton.contentHorizontalAlignment = .leading
        actionButton.addTarget(self, action: #selector(pickAction), for: .touchUpInside)
        stack.addArrangedSubview(actionButton)

        sendButton.setTitle(text("Выполнить", "Run"), for: .normal)
        sendButton.titleLabel?.font = .preferredFont(forTextStyle: .headline)
        sendButton.addTarget(self, action: #selector(send), for: .touchUpInside)
        sendButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        stack.addArrangedSubview(sendButton)

        spinner.hidesWhenStopped = true
        stack.addArrangedSubview(spinner)

        addHeading(text("Результат", "Result"))
        setupTextView(result, editable: false)
        result.heightAnchor.constraint(greaterThanOrEqualToConstant: 170).isActive = true
        stack.addArrangedSubview(result)

        copyButton.setTitle(text("Скопировать результат", "Copy result"), for: .normal)
        copyButton.addTarget(self, action: #selector(copyAnswer), for: .touchUpInside)
        stack.addArrangedSubview(copyButton)
        if applyResult != nil {
            applyButton.setTitle(text("Вставить в черновик", "Use in draft"), for: .normal)
            applyButton.addTarget(self, action: #selector(useAnswer), for: .touchUpInside)
            stack.addArrangedSubview(applyButton)
        }
        copyButton.isEnabled = false
        applyButton.isEnabled = false
        refreshStatus()
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if isMovingFromParent || isBeingDismissed { work?.cancel(); work = nil }
    }

    private func addHeading(_ title: String) {
        let label = UILabel()
        label.text = title
        label.font = .preferredFont(forTextStyle: .headline)
        label.adjustsFontForContentSizeCategory = true
        stack.addArrangedSubview(label)
    }

    private func setupTextView(_ field: UITextView, editable: Bool) {
        field.font = .preferredFont(forTextStyle: .body)
        field.adjustsFontForContentSizeCategory = true
        field.isEditable = editable
        field.isScrollEnabled = true
        field.backgroundColor = .secondarySystemGroupedBackground
        field.layer.cornerRadius = 14
        field.textContainerInset = UIEdgeInsets(top: 14, left: 10, bottom: 14, right: 10)
        field.textColor = .label
    }

    private func refreshStatus() {
        let provider = NebulaAiSettings.shared.provider
        if provider == .appleIntelligence {
            providerLabel.text = NebulaAiService.localModelAvailable
                ? text("Apple Intelligence работает на этом устройстве. Текст не отправляется на сервер.",
                       "Apple Intelligence runs on this device. Text is not sent to a server.")
                : text("Локальная модель недоступна или ещё подготавливается на устройстве.",
                       "The on-device model is unavailable or still preparing.")
        } else {
            providerLabel.text = text("Текст будет отправлен выбранному сервису: ", "Text will be sent to the selected service: ") + provider.title
        }
        actionButton.setTitle(text("Действие: ", "Action: ") + action.title(russian: ru) + "  ▾", for: .normal)
    }

    @objc private func pickAction() {
        let options = NebulaAiAction.allCases
        NebulaChoiceController.show(from: self, title: text("Что сделать с текстом?", "What should AI do?"),
            choices: options.map { $0.title(russian: ru) }, selected: options.firstIndex(of: action), russian: ru) { [weak self] index in
            self?.action = options[index]; self?.refreshStatus()
        }
    }

    @objc private func send() {
        if work != nil { work?.cancel(); work = nil; setBusy(false); return }
        let input = source.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { showError(text("Введите текст запроса.", "Enter request text.")); return }
        let instruction = action.instruction(russian: ru)
        let request = instruction.isEmpty ? input : "\(instruction)\n\n\(input)"
        let provider = NebulaAiSettings.shared.provider.title
        setBusy(true)
        answer = ""
        result.text = ""
        work = Task { [weak self] in
            guard let self = self else { return }
            do {
                let value = try await self.service.generate(input: request)
                guard !Task.isCancelled else { return }
                self.answer = value
                self.result.text = value
                self.copyButton.isEnabled = true
                self.applyButton.isEnabled = self.applyResult != nil
                _ = self.history.append(provider: provider, input: input, output: value)
            } catch {
                if !Task.isCancelled { self.showError(error.localizedDescription) }
            }
            self.work = nil
            self.setBusy(false)
        }
    }

    private func setBusy(_ busy: Bool) {
        source.isEditable = !busy
        actionButton.isEnabled = !busy
        sendButton.setTitle(busy ? text("Остановить", "Stop") : text("Выполнить", "Run"), for: .normal)
        busy ? spinner.startAnimating() : spinner.stopAnimating()
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(title: text("Запрос не выполнен", "Request failed"), message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    @objc private func copyAnswer() { UIPasteboard.general.string = answer }
    @objc private func useAnswer() {
        guard !answer.isEmpty else { return }
        applyResult?(answer)
        if let navigationController = navigationController, navigationController.viewControllers.first !== self {
            navigationController.popViewController(animated: true)
        } else { dismiss(animated: true) }
    }
    @objc private func close() { dismiss(animated: true) }
}
