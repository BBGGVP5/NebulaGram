import UIKit
import AccountContext
import TelegramCore
import TelegramPresentationData
import Postbox
import SwiftSignalKit
import NebulaSettingsContract

@available(iOS 15.0, *)
public final class NebulaAudioTranscriptionController: UITableViewController {
    private let context: AccountContext, message: Message, file: TelegramMediaFile
    private let theme: PresentationTheme, ru: Bool
    private let fetch = MetaDisposable(), data = MetaDisposable()
    private var task: Task<Void, Never>?, deadline: Foundation.Timer?
    private var busy = false, generation = 0, transcript = "", translated = "", language = "ru", status = ""
    private var transcriptLanguage = "en"
    private lazy var hero = NebulaSettingsHero(symbol: "🎙️", title: text("Расшифровка", "Transcription"), summary: text("Голосовые и видеосообщения", "Voice and video messages"), context: context, theme: theme)
    public nonisolated static func eligibleFile(_ message: Message) -> TelegramMediaFile? {
        guard message.id.namespace == Namespaces.Message.Cloud, message.id.id > 0,
              message.id.peerId.namespace != Namespaces.Peer.SecretChat,
              !message.containsSecretMedia, !message.isCopyProtected(), !NebulaDeletedCapture.isRetained(message),
              let file = message.effectiveMedia.compactMap({ $0 as? TelegramMediaFile }).first(where: { $0.isVoice || $0.isInstantVideo || $0.isVideo }),
              let size = file.size, size > 0, size <= Int64(NebulaAudioTranscription.maximumBytes) else { return nil }
        return file
    }
    public init(context: AccountContext, message: Message, file: TelegramMediaFile) {
        self.context = context; self.message = message; self.file = file
        let presentation = context.sharedContext.currentPresentationData.with { $0 }; theme = presentation.theme; ru = presentation.strings.baseLanguageCode.hasPrefix("ru"); language = ru ? "ru" : "en"
        transcriptLanguage = language
        super.init(style: .insetGrouped); title = "Nebula AI"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    public override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        NotificationCenter.default.addObserver(self, selector: #selector(cancel), name: UIApplication.didEnterBackgroundNotification, object: nil)
    }
    public override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    public override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    public override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false); cancel() }
    public override func numberOfSections(in tableView: UITableView) -> Int { transcript.isEmpty ? 1 : 2 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 2 : 7 }
    public override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        guard section == 0 else { return nil }
        let settings = NebulaAudioPreferences.shared
        settings.migrate(services: NebulaAiSettings.shared.services)
        let service = settings.connection(speech: false, services: NebulaAiSettings.shared.services)
        if settings.localTranscription {
            return text("Apple распознаёт запись на устройстве. До 14 МБ и 10 минут. Язык записи: ", "Apple transcribes this recording on device. Up to 14 MB and 10 minutes. Recording language: ") + settings.transcriptionLocale + "\n" + NebulaLocalTranscription.availability(locale: settings.transcriptionLocale, russian: ru) + (status.isEmpty ? "" : "\n\n" + status)
        }
        return text("По нажатию запись отправится выбранному сервису для распознавания. Максимум 14 МБ. Исходное сообщение не изменится.", "Tapping sends this recording to the selected service for transcription. Maximum 14 MB. The original message stays unchanged.") + (service.map { "\n" + $0.name + " · " + settings.model(speech: false, connection: $0) } ?? text("\nВыберите сервис в «ИИ → Аудио и голоса»", "\nChoose a service in AI → Audio & voices")) + (status.isEmpty ? "" : "\n\n" + status)

    }
    public override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme)
        cell.textLabel?.numberOfLines = 0
        if indexPath.section == 0 {
            cell.textLabel?.text = indexPath.row == 1 ? text("Аудио и голоса", "Audio & voices") : busy ? text("Отменить расшифровку", "Cancel transcription") : text("Расшифровать", "Transcribe")
            cell.textLabel?.textColor = theme.list.itemAccentColor
        } else if indexPath.row > 0 {
            cell.textLabel?.text = ["", text("Копировать результат", "Copy result"), text("Перевести расшифровку", "Translate transcript"), text("Озвучить результат", "Read result aloud"), text("Язык результата", "Result language") + " · " + language, text("Исходная расшифровка", "Original transcript"), text("Краткое содержание", "Summary")][indexPath.row]
            cell.textLabel?.textColor = theme.list.itemAccentColor
        } else {
            cell.selectionStyle = .none
            let view = UITextView(); view.isEditable = false; view.isSelectable = true; view.text = translated.isEmpty ? transcript : translated
            view.font = .preferredFont(forTextStyle: .body); view.adjustsFontForContentSizeCategory = true
            view.textColor = theme.list.itemPrimaryTextColor; view.backgroundColor = .clear; view.translatesAutoresizingMaskIntoConstraints = false
            cell.contentView.addSubview(view)
            NSLayoutConstraint.activate([view.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 12), view.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -12), view.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 8), view.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -8), view.heightAnchor.constraint(equalToConstant: min(340, max(180, self.view.bounds.height * 0.4)))])
        }
        return cell
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 0, indexPath.row == 1 { navigationController?.pushViewController(NebulaAudioSettingsController(russian: ru, theme: theme, context: context), animated: true); return }
        if indexPath.section == 1 {
            switch indexPath.row {
            case 1: UIPasteboard.general.string = translated.isEmpty ? transcript : translated
            case 2:
                let editor = NebulaAiEditorController(source: NSAttributedString(string: transcript), russian: ru, theme: theme, account: String(context.account.peerId.toInt64()), peer: String(message.id.peerId.toInt64()), action: .translate, resultLanguage: language, apply: { [weak self] value in self?.translated = value.string; self?.tableView.reloadData() })
                navigationController?.pushViewController(editor, animated: true)
            case 3: navigationController?.pushViewController(NebulaSpeechController(text: translated.isEmpty ? transcript : translated, language: translated.isEmpty ? transcriptLanguage : language, russian: ru, theme: theme, context: context), animated: true)
            case 4: NebulaResultLanguage.show(from: self, selected: language, russian: ru, theme: theme) { [weak self] code in self?.language = code; self?.tableView.reloadData() }
            case 5: translated = ""; tableView.reloadData()
            case 6: navigationController?.pushViewController(NebulaAiChatController(russian: ru, initialText: transcript, action: .summarize, applyResult: { [weak self] value in self?.translated = value; self?.tableView.reloadData() }, theme: theme, resultLanguage: language, applyTitle: text("Использовать результат", "Use result"), account: String(context.account.peerId.toInt64()), peer: String(message.id.peerId.toInt64())), animated: true)
            default: break
            }; return
        }
        if busy { cancel() } else { start() }
    }
    private func start() {
        guard Self.eligibleFile(message)?.fileId == file.fileId else { report(text("Эту запись нельзя расшифровать", "This recording cannot be transcribed")); return }
        do {
            let local = NebulaAudioPreferences.shared.localTranscription, locale = NebulaAudioPreferences.shared.transcriptionLocale
            let service = local ? nil : try NebulaAudioService()
            generation += 1; let token = generation; busy = true; status = text("Загружаем запись…", "Downloading recording…"); tableView.reloadData()
            deadline = Foundation.Timer.scheduledTimer(withTimeInterval: 180, repeats: false) { [weak self] _ in guard let self, self.generation == token else { return }; self.cancel(); self.report(self.text("Время ожидания истекло. Попробуйте ещё раз.", "Request timed out. Please try again.")) }
            let reference = MediaReference<TelegramMediaFile>.message(message: MessageReference(message), media: file)
            fetch.set(context.engine.resources.fetch(reference: reference.resourceReference(file.resource), userLocation: .peer(message.id.peerId), userContentType: .other).start())
            data.set((context.account.postbox.mediaBox.resourceData(file.resource) |> filter { $0.complete } |> take(1) |> deliverOnMainQueue).start(next: { [weak self] resource in
                guard let self, self.generation == token, self.busy else { return }
                if local {
                    self.deadline?.invalidate()
                    self.deadline = Foundation.Timer.scheduledTimer(withTimeInterval: 1200, repeats: false) { [weak self] _ in guard let self, self.generation == token else { return }; self.cancel(); self.report(self.text("Время ожидания истекло", "Request timed out")) }
                }
                self.status = self.text("Распознаём речь…", "Transcribing…"); self.tableView.reloadData()
                self.task = Task { @MainActor [weak self] in
                    guard let self else { return }
                    do {
                        let result: String
                        if local {
                            try await NebulaLocalTranscription.authorize()
                            try Task.checkCancellation(); guard self.generation == token else { return }
                            try NebulaLocalTranscription.requireAvailable(locale: locale)
                            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("nebula-transcript-\(UUID().uuidString)", isDirectory: true)
                            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700, .protectionKey: FileProtectionType.complete])
                            defer { try? FileManager.default.removeItem(at: directory) }
                            let pcm = directory.appendingPathComponent("recording.caf")
                            self.status = self.text("Готовим запись на устройстве…", "Preparing recording on device…"); self.tableView.reloadData()
                            try await NebulaRecordingPCM.decode(source: URL(fileURLWithPath: resource.path), destination: pcm, expectedDuration: self.file.duration)
                            result = try await NebulaLocalTranscription.transcribe(pcm: pcm, directory: directory, locale: locale) { [weak self] value in
                                guard let self, self.generation == token, self.busy else { return }
                                self.status = self.text("Распознаём на устройстве…", "Transcribing on device…") + (value.isEmpty ? "" : "\n" + String(value.suffix(2000))); self.tableView.reloadData()
                            }
                        } else if let service {
                            result = try await service.transcribe(file: URL(fileURLWithPath: resource.path), mime: self.file.mimeType)
                        } else { throw NebulaLocalAudioPolicy.Failure.incomplete }
                        try Task.checkCancellation(); guard self.generation == token else { return }
                        self.transcript = result; self.transcriptLanguage = local ? locale : (self.ru ? "ru" : "en"); self.translated = ""; self.finish(); self.status = self.text("Готово", "Done"); self.tableView.reloadData()
                    } catch {
                        guard self.generation == token, !Task.isCancelled else { return }
                        self.finish(); self.report(local ? NebulaLocalTranscription.message(for: error, russian: self.ru) : NebulaAiService.message(for: error, russian: self.ru))
                    }
                }
            }))
        } catch { report(text("Выберите сервис с моделью и ключом в «ИИ → Аудио и голоса»", "Choose a service with a model and key in AI → Audio & voices")) }
    }
    private func finish() { busy = false; deadline?.invalidate(); fetch.set(nil); data.set(nil) }
    @objc private func cancel() { guard busy else { return }; generation += 1; task?.cancel(); task = nil; finish(); status = text("Расшифровка отменена", "Transcription cancelled"); tableView.reloadData() }
    private func report(_ message: String) { status = message; tableView.reloadData() }
    @objc private func close() { cancel(); dismiss(animated: true) }
    deinit { task?.cancel(); fetch.dispose(); data.dispose(); deadline?.invalidate(); NotificationCenter.default.removeObserver(self) }
}
