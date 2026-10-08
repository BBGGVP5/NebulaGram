import Display
import UIKit
import AccountContext
import TelegramCore
import TelegramPresentationData
import SwiftSignalKit
import NebulaSettingsContract

final class NebulaMessageFilterController: UITableViewController {
    private let context: AccountContext
    private let choosePeer: (@escaping (Int64, String) -> Void) -> Void
    private let account: Int64
    private let theme: PresentationTheme
    private let ru: Bool
    private var rules: NebulaFilterRules
    private var titles: [Int64: String] = [:]
    private var lookup: Disposable?
    private var peers: [Int64] { rules.excludedPeers.sorted() }
    private lazy var hero = NebulaSettingsHero(symbol: "🔎", title: title ?? "", summary: t("Скрывайте лишнее, сохраняя сообщения", "Hide distractions without deleting messages"), context: context, theme: theme)
    init(context: AccountContext, choosePeer: @escaping (@escaping (Int64, String) -> Void) -> Void) {
        self.context = context; self.choosePeer = choosePeer; account = context.account.peerId.toInt64()
        let data = context.sharedContext.currentPresentationData.with { $0 }; theme = data.theme; ru = data.strings.baseLanguageCode.hasPrefix("ru")
        rules = NebulaMessageFilter.shared.rules(account: account)
        super.init(style: .insetGrouped); title = t("Фильтр сообщений", "Message filter")
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { lookup?.dispose() }
    private func t(_ ru: String, _ en: String) -> String { self.ru ? ru : en }
    override func viewDidLoad() {
        super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        lookup = (context.engine.data.get(EngineDataMap(peers.map { TelegramEngine.EngineData.Item.Peer.Peer(id: EnginePeer.Id($0)) })) |> deliverOnMainQueue).start(next: { [weak self] peers in
            guard let self else { return }; for (id, peer) in peers { if let peer { self.titles[id.toInt64()] = peer.compactDisplayTitle } }; self.tableView.reloadData()
        })
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 1 : section == 1 ? 4 : peers.count + 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { section == 1 ? t("Правила", "Rules") : section == 2 ? t("Исключения", "Excluded chats") : nil }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 1 { return t("Только входящие сообщения текущего аккаунта. Нажмите «показать», чтобы раскрыть скрытое сообщение. История не удаляется. Список заблокированных авторов загружается при открытии чата.", "Incoming messages in this account only. Tap reveal to show a filtered message. History is not deleted. Blocked authors load when opening a chat.") }
        if section == 2 { return t("Фильтр не применяется к этим чатам. Смахните строку, чтобы убрать исключение.", "These chats bypass filtering. Swipe a row to remove an exception.") }; return nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        var value: Bool?
        if indexPath.section == 0 { cell.textLabel?.text = t("Фильтр сообщений", "Message filter"); value = rules.enabled }
        else if indexPath.section == 1 {
            cell.textLabel?.text = [t("Слова и фразы", "Words and phrases"), t("Транслитерация", "Transliteration"), t("Только целые слова", "Whole words only"), t("Заблокированные авторы", "Blocked authors")][indexPath.row]
            if indexPath.row == 0 { cell.detailTextLabel?.text = rules.words.isEmpty ? t("Не заданы", "Not set") : t("Строк: ", "Lines: ") + String(rules.words.split(separator: "\n").count); cell.accessoryType = .disclosureIndicator }
            else { value = [rules.transliterate, rules.wholeWords, rules.blockedAuthors][indexPath.row - 1] }
        } else if indexPath.row == 0 { cell.textLabel?.text = t("Добавить чат", "Add chat"); cell.accessoryType = .disclosureIndicator }
        else { let id = peers[indexPath.row - 1]; cell.textLabel?.text = titles[id] ?? String(id) }
        if let value {
            let toggle = NebulaSwitchControl(); toggle.isOn = value; toggle.tag = indexPath.section == 0 ? 0 : indexPath.row
            toggle.accessibilityLabel = cell.textLabel?.text; toggle.addTarget(self, action: #selector(change(_:)), for: .valueChanged)
            cell.accessoryView = toggle; cell.selectionStyle = .none
        }
        NebulaSettingsStyle.finish(cell, theme: theme); return cell
    }
    @objc private func change(_ sender: NebulaSwitchControl) {
        switch sender.tag { case 0: rules.enabled = sender.isOn; case 1: rules.transliterate = sender.isOn; case 2: rules.wholeWords = sender.isOn; default: rules.blockedAuthors = sender.isOn }; save()
    }
    private func save() {
        do { try NebulaMessageFilter.shared.save(rules, account: account) }
        catch { rules = NebulaMessageFilter.shared.rules(account: account) }
        tableView.reloadData()
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 1 && indexPath.row == 0 {
            navigationController?.pushViewController(NebulaFilterWordsEditor(value: rules.words, russian: ru, theme: theme) { [weak self] value in self?.rules.words = value; self?.save() }, animated: true)
        } else if indexPath.section == 2 && indexPath.row == 0 && peers.count < 256 {
            choosePeer { [weak self] id, title in guard let self else { return }; self.rules.excludedPeers.insert(id); self.titles[id] = title; self.save() }
        }
    }
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool { indexPath.section == 2 && indexPath.row > 0 }
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) { if editingStyle == .delete { rules.excludedPeers.remove(peers[indexPath.row - 1]); save() } }
    @objc private func close() { dismiss(animated: true) }
}

private final class NebulaFilterWordsEditor: UIViewController, UITextViewDelegate {
    private let input = UITextView()
    private let saved: (String) -> Void
    private let ru: Bool
    private let theme: PresentationTheme
    init(value: String, russian: Bool, theme: PresentationTheme, saved: @escaping (String) -> Void) {
        self.saved = saved; ru = russian; self.theme = theme; super.init(nibName: nil, bundle: nil); input.text = value
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad(); title = ru ? "По одной фразе в строке" : "One phrase per line"
        view.backgroundColor = theme.list.blocksBackgroundColor; input.backgroundColor = theme.list.itemBlocksBackgroundColor; input.textColor = theme.list.itemPrimaryTextColor
        input.font = .preferredFont(forTextStyle: .body); input.adjustsFontForContentSizeCategory = true; input.delegate = self
        input.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(input)
        input.textContainerInset = UIEdgeInsets(top: 16, left: 12, bottom: 16, right: 12)
        NSLayoutConstraint.activate([input.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 16), input.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16), input.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16), input.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16)])
        input.keyboardDismissMode = .interactive; input.layer.cornerRadius = 20
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .save, target: self, action: #selector(save))
        NotificationCenter.default.addObserver(self, selector: #selector(keyboard(_:)), name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
    }
    deinit { NotificationCenter.default.removeObserver(self) }
    func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
        let next = (textView.text as NSString).replacingCharacters(in: range, with: text)
        return next.count <= 4000 && next.split(separator: "\n").count <= 200
    }
    @objc private func keyboard(_ note: Notification) { if let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect { input.contentInset.bottom = max(0, input.convert(frame, from: nil).intersection(input.bounds).height); input.verticalScrollIndicatorInsets.bottom = input.contentInset.bottom } }
    @objc private func save() { saved(input.text); navigationController?.popViewController(animated: true) }
}
