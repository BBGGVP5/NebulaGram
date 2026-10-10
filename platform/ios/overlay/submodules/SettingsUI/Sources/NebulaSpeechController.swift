import UIKit
import AVFoundation
import AccountContext
import NebulaSettingsContract
import TelegramPresentationData

@available(iOS 15.0, *)
final class NebulaSpeechController: UITableViewController, UITextViewDelegate {
    private let ru: Bool, theme: PresentationTheme?
    private let context: AccountContext?
    private var language: String
    private let source: String
    private let input = UITextView()
    private let device = AVSpeechSynthesizer()
    private var player: AVAudioPlayer?
    private var ownsAudioSession = false
    private var task: Task<Void, Never>?, generation = 0, busy = false, visible = false
    private var status = "", audio: URL?, audioText = ""
    private lazy var hero = NebulaSettingsHero(symbol: "🔊", title: text("Озвучивание", "Speech"), summary: text("Выберите голос и послушайте результат", "Choose a voice and listen to the result"), context: context, theme: theme)
    init(text: String, language: String, russian: Bool, theme: PresentationTheme?, context: AccountContext? = nil) {
        self.source = text; self.language = language; self.ru = russian; self.theme = theme; self.context = context
        super.init(style: .insetGrouped); title = self.text("Озвучивание", "Speech")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self); tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        input.text = source; input.delegate = self; input.font = .preferredFont(forTextStyle: .body); input.adjustsFontForContentSizeCategory = true; input.backgroundColor = .clear; input.textColor = theme?.list.itemPrimaryTextColor ?? .label; input.heightAnchor.constraint(equalToConstant: 180).isActive = true
        NotificationCenter.default.addObserver(self, selector: #selector(stop), name: UIApplication.willResignActiveNotification, object: nil)
    }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); visible = true; hero.setPageVisible(true); tableView.reloadData() }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); visible = false; hero.setPageVisible(false); stop() }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { [1, 3, audio == nil ? 2 : 4][section] }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 2 ? status + "\n\n" + text("Облачный голос создан ИИ. Текст отправляется выбранному сервису только по нажатию. До 4000 символов. Отправка аудио — через обычное меню «Поделиться».", "Cloud speech is AI-generated. Text is sent to the selected service only after tapping. Up to 4000 characters. Share audio using the standard share menu.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme); cell.textLabel?.numberOfLines = 0
        if path.section == 0 {
            cell.selectionStyle = .none; input.removeFromSuperview(); input.translatesAutoresizingMaskIntoConstraints = false; cell.contentView.addSubview(input)
            NSLayoutConstraint.activate([input.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 12), input.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -12), input.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 8), input.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -8)]); return cell
        }
        if path.section == 1 {
            cell.accessoryType = .disclosureIndicator
            if path.row == 0 { cell.textLabel?.text = text("Настройки голоса", "Voice settings"); let prefs = NebulaAudioPreferences.shared; cell.detailTextLabel?.text = prefs.speechService == "device" ? text("На устройстве", "On device") : prefs.connection(speech: true, services: NebulaAiSettings.shared.services)?.name ?? text("Выбрать сервис", "Choose service") }
            else if path.row == 1 { cell.textLabel?.text = text("Язык системного голоса", "Device voice language"); cell.detailTextLabel?.text = language }
            else { cell.textLabel?.text = text("Голос на устройстве", "Device voice"); cell.detailTextLabel?.text = AVSpeechSynthesisVoice(identifier: NebulaAudioPreferences.shared.deviceVoice)?.name ?? text("Автоматически · лучший доступный", "Automatic · best available") }
        } else { cell.textLabel?.text = [busy ? text("Обработка…", "Working…") : text("Создать озвучивание", "Generate speech"), text("Остановить", "Stop"), text("Прослушать", "Listen"), text("Поделиться аудио", "Share audio")][path.row]; cell.textLabel?.textColor = theme?.list.itemAccentColor ?? view.tintColor }
        return cell
    }
    func textViewDidChange(_ textView: UITextView) { stop(); discard(); tableView.reloadSections(IndexSet(integer: 2), with: .none) }
    func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool { ((textView.text ?? "") as NSString).length - range.length + (text as NSString).length <= NebulaAudioProtocol.maximumText || (text as NSString).length < range.length }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
        if path.section == 1 {
            if path.row == 0 { navigationController?.pushViewController(NebulaAudioSettingsController(russian: ru, theme: theme), animated: true) }
            else if path.row == 1 { NebulaResultLanguage.show(from: self, selected: language, russian: ru, theme: theme) { [weak self] code in self?.stop(); self?.language = code; self?.tableView.reloadData() } }
            else { chooseDeviceVoice() }; return
        }
        guard path.section == 2 else { return }
        switch path.row { case 0: if !busy { start() }; case 1: stop(); case 2: play(); default: share() }
    }
    private var voices: [AVSpeechSynthesisVoice] { AVSpeechSynthesisVoice.speechVoices().filter { $0.language.split(separator: "-").first.map(String.init) == language.split(separator: "-").first.map(String.init) }.sorted { $0.quality.rawValue == $1.quality.rawValue ? $0.name < $1.name : $0.quality.rawValue > $1.quality.rawValue } }
    private func chooseDeviceVoice() {
        let voices = self.voices, ids = [""] + self.voices.map(\.identifier)
        let names = [text("Автоматически · лучший доступный", "Automatic · best available")] + voices.map { $0.name + " · " + $0.language + ($0.quality.rawValue > 1 ? text(" · улучшенный", " · enhanced") : "") }
        NebulaChoiceController.show(from: self, title: text("Голос", "Voice"), choices: names, selected: ids.firstIndex(of: NebulaAudioPreferences.shared.deviceVoice), russian: ru, selectionIndicatorVisible: false, theme: theme) { [weak self] index in self?.stop(); NebulaAudioPreferences.shared.deviceVoice = ids[index]; self?.tableView.reloadData() }
    }
    private func start() {
        let value = input.text ?? ""
        guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, value.utf16.count <= NebulaAudioProtocol.maximumText else { status = text("Введите текст длиной до 4000 символов", "Enter text up to 4000 characters"); tableView.reloadData(); return }
        stop(); discard(); input.resignFirstResponder()
        let prefs = NebulaAudioPreferences.shared
        if prefs.speechService == "device" {
            let utterance = AVSpeechUtterance(string: value)
            let selected = AVSpeechSynthesisVoice(identifier: prefs.deviceVoice)
            utterance.voice = selected.flatMap { chosen in self.voices.contains(where: { $0.identifier == chosen.identifier }) ? chosen : nil } ?? voices.first ?? AVSpeechSynthesisVoice(language: language)
            guard utterance.voice != nil else { status = text("Установите голос выбранного языка в настройках iOS", "Install this language's voice in iOS settings"); tableView.reloadData(); return }
            utterance.rate = min(AVSpeechUtteranceMaximumSpeechRate, max(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceDefaultSpeechRate * Float(prefs.speed)))
            device.speak(utterance); status = text("Читаем голосом устройства", "Reading with the device voice"); tableView.reloadData(); return
        }
        do {
            let service = try NebulaAudioService(speech: true); generation += 1; let token = generation
            busy = true; status = text("Создаём голос ИИ… · ", "Generating AI speech… · ") + service.title; tableView.reloadData()
            task = Task { @MainActor [weak self] in
                guard let self else { return }
                do {
                    let data = try await service.speech(text: value); try Task.checkCancellation()
                    guard self.generation == token, self.visible, self.input.text == value else { return }
                    let url = FileManager.default.temporaryDirectory.appendingPathComponent("nebula-speech-\(UUID().uuidString).wav")
                    try data.write(to: url, options: .atomic); self.audio = url; self.audioText = value; self.busy = false; self.task = nil; self.status = self.text("Готово · голос создан ИИ", "Ready · AI-generated voice"); self.tableView.reloadData(); self.play()
                } catch { guard self.generation == token, !Task.isCancelled else { return }; self.busy = false; self.task = nil; self.status = NebulaAiService.message(for: error, russian: self.ru); self.tableView.reloadData() }
            }
        } catch { status = text("Выберите сервис в «ИИ → Аудио и голоса»", "Choose a service in AI → Audio & voices"); tableView.reloadData() }
    }
    private func play() {
        guard let audio, input.text == audioText else { return }
        do { player?.stop(); try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers]); try AVAudioSession.sharedInstance().setActive(true); ownsAudioSession = true; player = try AVAudioPlayer(contentsOf: audio); player?.play() }
        catch { status = text("Не удалось воспроизвести аудио", "Audio playback failed"); tableView.reloadData() }
    }
    private func share() {
        guard let audio, input.text == audioText else { return }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("nebula-speech-exports", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let old = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
            for file in old { let date = (try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast; if Date().timeIntervalSince(date) > 172800 { try? FileManager.default.removeItem(at: file) } }
            let export = directory.appendingPathComponent("Nebula-AI-voice-\(UUID().uuidString).wav"); try FileManager.default.copyItem(at: audio, to: export)
            let chooser = UIActivityViewController(activityItems: [export], applicationActivities: nil); chooser.popoverPresentationController?.sourceView = view; chooser.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1); present(chooser, animated: true)
        } catch { status = text("Не удалось поделиться аудио", "Could not share audio"); tableView.reloadData() }
    }
    @objc private func stop() { generation += 1; task?.cancel(); task = nil; busy = false; device.stopSpeaking(at: .immediate); player?.stop(); player = nil; if ownsAudioSession { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation); ownsAudioSession = false }; if isViewLoaded { tableView.reloadSections(IndexSet(integer: 2), with: .none) } }
    private func discard() { if let audio { try? FileManager.default.removeItem(at: audio) }; audio = nil; audioText = "" }
    deinit { task?.cancel(); device.stopSpeaking(at: .immediate); player?.stop(); if let audio { try? FileManager.default.removeItem(at: audio) }; NotificationCenter.default.removeObserver(self) }
}
