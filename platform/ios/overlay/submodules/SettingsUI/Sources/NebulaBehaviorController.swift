import Display
import UIKit
import LocalAuthentication
import AccountContext
import TelegramCore
import SwiftSignalKit
import TelegramPresentationData
import NebulaSettingsContract

public final class NebulaBehaviorController: UITableViewController {
    private let context: AccountContext
    private let account: String
    private let choosePeer: (Bool, @escaping (Int64, String) -> Void) -> Void
    private let prefs = NebulaBehaviorPreferences.shared
    private var savedTitle: String?
    private var peerTitles: [Int64: String] = [:]
    private var lookup: Disposable?
    private var theme: PresentationTheme { context.sharedContext.currentPresentationData.with { $0 }.theme }
    private var ru: Bool { context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.hasPrefix("ru") }
    public init(context: AccountContext, choosePeer: @escaping (Bool, @escaping (Int64, String) -> Void) -> Void) {
        self.context = context; self.account = String(context.account.peerId.toInt64()); self.choosePeer = choosePeer
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { lookup?.dispose() }
    private func t(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    public override func viewDidLoad() {
        super.viewDidLoad(); title = t("Поведение", "Behavior")
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 56
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        var ids = prefs.ignoredMentionPeers(account: account) ?? []
        if let target = prefs.savedTarget(account: account) { ids.insert(target) }
        lookup = (context.engine.data.get(EngineDataMap(ids.map { TelegramEngine.EngineData.Item.Peer.Peer(id: EnginePeer.Id($0)) })) |> deliverOnMainQueue).start(next: { [weak self] peers in
            guard let self else { return }
            for (id, peer) in peers { if let peer { self.peerTitles[id.toInt64()] = peer.compactDisplayTitle } }
            if let target = self.prefs.savedTarget(account: self.account) { self.savedTitle = self.peerTitles[target] }
            self.tableView.reloadData()
        })
    }
    @objc private func close() { dismiss(animated: true) }
    public override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 2 ? 1 : section == 1 ? 4 : 3 }
    public override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        [t("Уведомления", "Notifications"), t("Сообщения", "Messages"), t("Защита", "Protection")][section]
    }
    public override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 1 { return t("Фон обновится при следующем открытии чата. Сохранение открывает черновик пересылки; отправка вручную.", "Wallpaper updates the next time the chat opens. Saving opens a forwarding draft; send it manually.") }
        if section == 2 { return t("Face ID, Touch ID или код устройства перед удалением чата и очисткой истории.", "Face ID, Touch ID or your device passcode before deleting chats or clearing history.") }
        return nil
    }
    private func key(_ index: IndexPath) -> String? {
        if index.section == 0 { return ["mute_non_contacts", "ignore_mentions", ""][index.row].nebulaBehaviorNonEmpty }
        if index.section == 1 { return ["custom_chat_wallpaper", "quote_full_reply", "smooth_fade", ""][index.row].nebulaBehaviorNonEmpty }
        return "biometric_delete"
    }
    public override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        let titles = [[t("Не-контакты без звука", "Silence non-contacts"), t("Игнорировать упоминания", "Ignore mentions"), t("Чаты с игнорированием", "Ignored chats")], [t("Фон отдельных чатов", "Per-chat wallpaper"), t("Цитировать ответы", "Quote whole replies"), t("Плавное затухание", "Smooth fading"), t("Куда сохранять сообщения", "Save messages to")], [t("Подтверждать удаление", "Authenticate before deletion")]]
        cell.textLabel?.text = titles[indexPath.section][indexPath.row]
        cell.textLabel?.numberOfLines = 0; cell.detailTextLabel?.numberOfLines = 0
        if let key = key(indexPath) {
            let toggle = NebulaSwitchControl(); toggle.isOn = key == "smooth_fade" ? prefs.smoothFade : prefs.enabled(key, account: account); toggle.accessibilityIdentifier = key
            toggle.addTarget(self, action: #selector(toggle(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
            if key == "quote_full_reply" { cell.detailTextLabel?.text = t("До лимита Telegram · вне топиков", "Up to Telegram’s limit · outside topics") }
        } else {
            cell.accessoryType = .disclosureIndicator
            if indexPath.section == 0 {
                cell.detailTextLabel?.text = prefs.ignoredMentionPeers(account: account).map { t("Выбрано: ", "Selected: ") + String($0.count) } ?? t("Все чаты", "All chats")
            } else { cell.detailTextLabel?.text = savedTitle ?? (prefs.savedTarget(account: account) == nil ? t("Избранное", "Saved Messages") : t("Выбранный чат", "Chosen chat")) }
        }
        NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    @objc private func toggle(_ sender: NebulaSwitchControl) {
        guard let key = sender.accessibilityIdentifier else { return }
        if key == "biometric_delete", sender.isOn, !LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) {
            sender.isOn = false
            let alert = UIAlertController(title: t("Настройте код устройства", "Set up a device passcode"), message: nil, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true); return
        }
        if key == "smooth_fade" { prefs.setSmoothFade(sender.isOn) }
        else { prefs.set(key, account: account, value: sender.isOn) }
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard key(indexPath) == nil else { return }
        if indexPath.section == 1 {
            NebulaChoiceController.show(from: self, title: t("Куда сохранять", "Save destination"), choices: [t("Избранное", "Saved Messages"), t("Выбрать чат", "Choose chat")], selected: nil, russian: ru, theme: theme) { [weak self] selected in
                guard let self else { return }
                if selected == 0 { self.prefs.setSavedTarget(account: self.account, peer: nil); self.savedTitle = nil; self.tableView.reloadData() }
                else { self.choosePeer(true) { [weak self] id, title in guard let self else { return }; self.prefs.setSavedTarget(account: self.account, peer: id); self.savedTitle = title; self.tableView.reloadData() } }
            }
        } else {
            let peers = (prefs.ignoredMentionPeers(account: account) ?? []).sorted()
            let choices = [t("Все чаты", "All chats"), t("Добавить чат", "Add chat")] + peers.map { t("Убрать: ", "Remove: ") + (peerTitles[$0] ?? String($0)) }
            NebulaChoiceController.show(from: self, title: t("Игнорировать упоминания", "Ignore mentions"), choices: choices, selected: nil, russian: ru, theme: theme) { [weak self] selected in
                guard let self else { return }
                if selected == 0 { self.prefs.setIgnoredMentionPeers(account: self.account, peers: nil) }
                else if selected == 1 {
                    self.choosePeer(false) { [weak self] id, title in
                        guard let self else { return }
                        var selection = self.prefs.ignoredMentionPeers(account: self.account) ?? []; selection.insert(id)
                        self.prefs.setIgnoredMentionPeers(account: self.account, peers: selection); self.peerTitles[id] = title; self.tableView.reloadData()
                    }
                } else { var selection = Set(peers); selection.remove(peers[selected - 2]); self.prefs.setIgnoredMentionPeers(account: self.account, peers: selection) }
                self.tableView.reloadData()
            }
        }
    }
}

private extension String { var nebulaBehaviorNonEmpty: String? { isEmpty ? nil : self } }
