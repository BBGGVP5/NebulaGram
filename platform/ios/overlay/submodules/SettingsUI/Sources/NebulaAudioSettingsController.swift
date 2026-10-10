import UIKit
import Display
import Speech
import AccountContext
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaAudioSettingsController: UITableViewController {
    private let ru: Bool, theme: PresentationTheme?
    private let context: AccountContext?
    private let audio = NebulaAudioPreferences.shared
    private let services = NebulaAiSettings.shared.services
    private lazy var hero = NebulaSettingsHero(symbol: "🎙️", title: text("Аудио и голоса", "Audio & voices"), summary: text("Распознавание, перевод и озвучивание", "Transcription, translation and speech"), context: context, theme: theme)
    init(russian: Bool, theme: PresentationTheme?, context: AccountContext? = nil) {
        ru = russian; self.theme = theme; self.context = context
        super.init(style: .insetGrouped); title = text("Аудио и голоса", "Audio & voices"); audio.migrate(services: services)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    override func viewDidLoad() { super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self); tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64 }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true); tableView.reloadData() }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? (audio.localTranscription ? 4 : 3) : section == 1 ? (audio.speechService == "device" ? 2 : 5) : 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { [text("Распознавание", "Transcription"), text("Озвучивание", "Speech"), nil][section] }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 { return text("Включите расшифровку Nebula, выберите Apple на устройстве или OpenAI/Gemini, затем удерживайте голосовое или кружок и выберите «Nebula AI · расшифровать». Telegram Premium для этого не требуется. Локальный режим: до 10 минут, язык должен поддерживаться устройством; запись не отправляется в облако.", "Enable Nebula transcription, choose Apple on device or OpenAI/Gemini, then hold a voice or video message and choose Nebula AI · Transcribe. Telegram Premium is not required. Local mode: up to 10 minutes, with a language supported by this device; the recording is not uploaded to a cloud service.") }
        return section == 1 ? text("Облачный голос создан ИИ. По нажатию текст или запись отправится указанному сервису с вашим ключом. Доступ и стоимость зависят от сервиса. Аудиомодели выбираются отдельно от текстового чата. Записи: до 14 МБ, озвучивание: до 4000 символов. Голоса устройства выбираются в окне озвучивания.", "Cloud speech is AI-generated. Tapping sends text or recordings to the named service using your key; availability and cost depend on that service. Audio models are separate from text chat. Recordings: up to 14 MB; speech: up to 4000 characters. Choose installed device voices in the speech screen.") : nil
    }
    private func title(speech: Bool) -> String {
        if speech && audio.speechService == "device" { return text("На устройстве", "On device") }
        if !speech && audio.localTranscription { return text("Apple · на устройстве", "Apple · on device") }
        return audio.connection(speech: speech, services: services)?.name ?? text("Выбрать сервис", "Choose service")
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme)
        cell.textLabel?.numberOfLines = 0; cell.detailTextLabel?.numberOfLines = 0; cell.accessoryType = .disclosureIndicator
        let speech = path.section == 1, connection = audio.connection(speech: speech, services: services)
        if path.section == 0, audio.localTranscription, path.row >= 2 {
            cell.textLabel?.text = path.row == 2 ? text("Язык записи", "Recording language") : text("Локальное распознавание", "On-device recognition")
            cell.detailTextLabel?.text = path.row == 2 ? Locale(identifier: ru ? "ru" : "en").localizedString(forIdentifier: audio.transcriptionLocale) ?? audio.transcriptionLocale : localAvailability()
            return cell
        }
        if speech, audio.speechService == "device", path.row == 1 { cell.textLabel?.text = text("Темп речи", "Speaking pace"); cell.detailTextLabel?.text = String(format: "%.1f×", audio.speed); return cell }
        switch (path.section, path.row) {
        case (0, 0):
            cell.textLabel?.text = text("Расшифровка Nebula", "Nebula transcription")
            cell.detailTextLabel?.text = text("Голосовые и кружки без Telegram Premium", "Voice and video messages without Telegram Premium")
            let toggle = NebulaSwitchControl(); toggle.isOn = audio.transcriptionEnabled; toggle.accessibilityLabel = cell.textLabel?.text; toggle.addTarget(self, action: #selector(toggleTranscription(_:)), for: .valueChanged); cell.accessoryView = toggle; cell.accessoryType = .none; cell.selectionStyle = .none
        case (0, 1), (1, 0): cell.textLabel?.text = text("Сервис", "Service"); cell.detailTextLabel?.text = title(speech: speech)
        case (0, 2), (1, 1): cell.textLabel?.text = text("Модель", "Model"); cell.detailTextLabel?.text = connection.map { audio.model(speech: speech, connection: $0) } ?? text("Сначала выберите сервис", "Select a service first")
        case (1, 2): cell.textLabel?.text = text("Голос ИИ", "AI voice"); cell.detailTextLabel?.text = connection.map { audio.voice(provider: $0.provider) } ?? text("Голос устройства", "Device voice")
        case (1, 3): cell.textLabel?.text = text("Манера речи ИИ", "AI speaking style"); cell.detailTextLabel?.text = styleNames[styleIds.firstIndex(of: audio.style) ?? 0]
        case (1, 4): cell.textLabel?.text = text("Темп речи", "Speaking pace"); cell.detailTextLabel?.text = String(format: "%.1f×", audio.speed)
        default: cell.textLabel?.text = text("Настроить сервисы", "Configure services")
        }
        return cell
    }
    private var styleIds: [String] { ["neutral", "warm", "calm", "lively"] }
    private var styleNames: [String] { [text("Естественно", "Natural"), text("Дружелюбно", "Friendly"), text("Спокойно", "Calm"), text("Живо", "Expressive")] }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
        let speech = path.section == 1
        if path.section == 2 { navigationController?.pushViewController(NebulaAiServicesController(russian: ru, theme: theme, context: context), animated: true); return }
        if path.section == 0 && path.row == 0 { return }
        if path.row == (speech ? 0 : 1) { chooseService(speech: speech); return }
        if path.section == 0, audio.localTranscription {
            if path.row == 2 { chooseLocalLanguage() } else { showLocalStatus() }; return
        }
        if path.row == 4 || speech && audio.speechService == "device" && path.row == 1 { let values = [0.8, 1, 1.2]; choose(text("Темп речи", "Speaking pace"), values.map { String(format: "%.1f×", $0) }, values.firstIndex(of: audio.speed)) { [weak self] index in self?.audio.speed = values[index]; self?.tableView.reloadData() }; return }
        if path.row == 3 { choose(text("Манера речи", "Speaking style"), styleNames, styleIds.firstIndex(of: audio.style)) { [weak self] index in guard let self else { return }; self.audio.style = self.styleIds[index]; self.tableView.reloadData() }; return }
        guard let connection = audio.connection(speech: speech, services: services) else { return }
        if speech && path.row == 2 {
            let voices = connection.provider == .openAI ? NebulaAudioProtocol.openAIVoices : NebulaAudioProtocol.geminiVoices
            choose(text("Голос ИИ", "AI voice"), voices, voices.firstIndex(of: audio.voice(provider: connection.provider))) { [weak self] index in self?.audio.setVoice(voices[index], provider: connection.provider); self?.tableView.reloadData() }; return
        }
        let alert = UIAlertController(title: text("Модель аудио", "Audio model"), message: nil, preferredStyle: .alert)
        alert.addTextField { $0.text = self.audio.model(speech: speech, connection: connection); $0.autocapitalizationType = .none; $0.autocorrectionType = .no }
        alert.addAction(UIAlertAction(title: text("Сохранить", "Save"), style: .default) { [weak self] _ in
            guard let self else { return }
            do { try self.audio.setModel(alert.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "", speech: speech, provider: connection.provider); self.tableView.reloadData() }
            catch { let error = UIAlertController(title: self.text("Проверьте ID модели", "Check the model ID"), message: nil, preferredStyle: .alert); error.addAction(UIAlertAction(title: "OK", style: .default)); self.present(error, animated: true) }
        }); alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel)); present(alert, animated: true)
    }
    @objc private func toggleTranscription(_ sender: NebulaSwitchControl) {
        if !sender.isOn { audio.transcriptionEnabled = false; tableView.reloadData(); return }
        if audio.localTranscription || audio.connection(speech: false, services: services) != nil { audio.transcriptionEnabled = true; tableView.reloadData() }
        else { tableView.reloadData(); chooseService(speech: false, enableAfterSelection: true) }
    }
    private func chooseService(speech: Bool, enableAfterSelection: Bool = false) {
        let connections = services.connections.filter { NebulaAudioPreferences.supports($0.provider) }
        let localIds = speech ? ["device"] : ["", NebulaLocalAudioPolicy.service]
        let localNames = speech ? [text("На устройстве", "On device")] : [text("Не выбран", "Not selected"), text("Apple · на устройстве", "Apple · on device")]
        let ids = localIds + connections.map(\.id)
        let names = localNames + connections.map { $0.name + " · " + $0.provider.title } + [text("Добавить OpenAI", "Add OpenAI"), text("Добавить Gemini", "Add Gemini")]
        choose(text("Сервис аудио", "Audio service"), names, ids.firstIndex(of: speech ? audio.speechService : audio.transcriptionService)) { [weak self] index in
            guard let self else { return }
            if index >= ids.count {
                let provider: NebulaAiProvider = index == ids.count ? .openAI : .gemini
                let editor = NebulaAiConnectionController(connection: nil, russian: self.ru, theme: self.theme, audioSpeech: speech, audioProvider: provider, savedCallback: { [weak self] connection in
                    guard let self else { return }
                    if speech { self.audio.speechService = connection.id } else { self.audio.transcriptionService = connection.id; if enableAfterSelection { self.audio.transcriptionEnabled = true } }
                    try? self.audio.setModel(connection.model, speech: speech, provider: connection.provider)
                })
                self.navigationController?.pushViewController(editor, animated: true); return
            }
            if speech { self.audio.speechService = ids[index] }
            else { self.audio.transcriptionService = ids[index]; if ids[index].isEmpty { self.audio.transcriptionEnabled = false } else if enableAfterSelection { self.audio.transcriptionEnabled = true } }
            self.tableView.reloadData()
        }
    }
    private func localAvailability() -> String {
        if #available(iOS 15.0, *) { return NebulaLocalTranscription.availability(locale: audio.transcriptionLocale, russian: ru) }
        return text("Требуется iOS 15 или новее", "Requires iOS 15 or later")
    }
    private func chooseLocalLanguage() {
        let display = Locale(identifier: ru ? "ru" : "en")
        let codes = SFSpeechRecognizer.supportedLocales().compactMap { NebulaLocalAudioPolicy.locale($0.identifier) }.sorted {
            let a = $0 == audio.transcriptionLocale ? 0 : $0 == "ru-RU" || $0 == "en-US" ? 1 : 2
            let b = $1 == audio.transcriptionLocale ? 0 : $1 == "ru-RU" || $1 == "en-US" ? 1 : 2
            return a == b ? $0 < $1 : a < b
        }
        choose(text("Язык записи", "Recording language"), codes.map { display.localizedString(forIdentifier: $0) ?? $0 }, codes.firstIndex(of: audio.transcriptionLocale)) { [weak self] index in self?.audio.transcriptionLocale = codes[index]; self?.tableView.reloadData() }
    }
    private func showLocalStatus() {
        let alert = UIAlertController(title: text("Apple · на устройстве", "Apple · on device"), message: localAvailability() + "\n\n" + text("Apple управляет доступностью языковых моделей. Разрешение запрашивается при нажатии «Расшифровать». Этот режим использует только выбранную локальную модель, без облачного запасного варианта.", "Apple manages language model availability. Permission is requested when you tap Transcribe. This mode uses only the selected on-device model with no cloud fallback."), preferredStyle: .alert)
        if SFSpeechRecognizer.authorizationStatus() == .denied {
            alert.addAction(UIAlertAction(title: text("Настройки iOS", "iOS Settings"), style: .default) { _ in if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } })
        }
        alert.addAction(UIAlertAction(title: "OK", style: .cancel)); present(alert, animated: true)
    }
    private func choose(_ title: String, _ names: [String], _ selected: Int?, _ action: @escaping (Int) -> Void) { NebulaChoiceController.show(from: self, title: title, choices: names, selected: selected, russian: ru, selectionIndicatorVisible: false, theme: theme, choose: action) }
}
