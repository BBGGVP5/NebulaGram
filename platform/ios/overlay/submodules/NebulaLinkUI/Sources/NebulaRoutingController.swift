import UIKit

/// NebulaLink routing: "domain or subnet -> direct, proxy or block" rules,
/// the default for everything else, and the presets.
///
/// The screen only edits the table. Parsing and the translation into Xray
/// routing live in the core ("routing.set" refuses a bad rule), so there is no
/// second validator here to drift from it. Telegram's own servers stay in the
/// tunnel until the user explicitly lifts that guarantee below.
final class NebulaRoutingController: UITableViewController {
    private static let actions = ["proxy", "direct", "block"]

    private let ru: Bool
    private let service = NebulaLinkService.shared
    private var routing: [String: Any] = [:]
    private var presets: [[String: Any]] = []
    private var geoAssets = false
    private var busy = false
    private var message = ""
    private let telegramSwitch = UISwitch()

    init(russian: Bool) {
        ru = russian
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = text("Маршрутизация", "Routing")
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 56
        telegramSwitch.addTarget(self, action: #selector(telegramToggled), for: .valueChanged)
        load()
    }

    // MARK: - state

    private var rules: [[String: Any]] { routing["rules"] as? [[String: Any]] ?? [] }
    private var fallback: String {
        let value = routing["default"] as? String ?? ""
        return value.isEmpty ? "proxy" : value
    }
    private var telegramProxied: Bool { !(routing["telegram_follows_rules"] as? Bool ?? false) }

    private func apply(_ data: Any) {
        guard let view = data as? [String: Any] else { return }
        routing = view["routing"] as? [String: Any] ?? [:]
        presets = view["presets"] as? [[String: Any]] ?? []
        geoAssets = view["geo_assets"] as? Bool ?? false
    }

    private func load() {
        service.call("routing.get") { [weak self] result in
            guard let self = self else { return }
            if case let .success(data) = result { self.apply(data) }
            else { self.message = self.text("Не удалось загрузить правила", "Could not load the rules") }
            self.tableView.reloadData()
        }
    }

    private func send(_ method: String, _ payload: [String: Any]) {
        guard !busy else { return }
        busy = true
        message = text("Применяю…", "Applying…")
        tableView.reloadData()
        service.call(method, payload: payload) { [weak self] result in
            guard let self = self else { return }
            self.busy = false
            switch result {
            case let .success(data):
                self.message = ""
                self.apply(data)
            case .failure:
                // The core's text is not passed through the service (it may hold
                // subscription credentials elsewhere), so name the likely cause.
                self.message = self.text("Правило не принято: проверьте домен, IP или подсеть.",
                                         "The rule was not accepted: check the domain, IP or subnet.")
            }
            self.tableView.reloadData()
        }
    }

    private func save(_ next: [String: Any]) {
        var table = next
        table["preset"] = ""
        send("routing.set", ["routing": table])
    }

    private func actionTitle(_ action: String) -> String {
        switch action {
        case "direct": return text("Напрямую", "Direct")
        case "block": return text("Блокировать", "Block")
        default: return text("Через прокси", "Through the proxy")
        }
    }

    private func presetHint(_ id: String) -> String {
        switch id {
        case "ru_direct": return text(".ru, .рф и .su — напрямую, остальное через прокси", ".ru, .рф and .su direct, everything else through the proxy")
        case "telegram_only": return text("Через прокси только Telegram, остальное напрямую", "Only Telegram through the proxy, everything else direct")
        case "all_proxy": return text("Без правил: весь трафик через прокси", "No rules: all traffic through the proxy")
        default: return ""
        }
    }

    // MARK: - table

    override func numberOfSections(in tableView: UITableView) -> Int { 3 }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return presets.count
        case 1: return 2
        default: return rules.count + 1
        }
    }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch section {
        case 0: return text("Готовые наборы", "Presets")
        case 1: return text("Общие", "General")
        default: return text("Правила", "Rules")
        }
    }

    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        guard section == 2 else { return nil }
        var lines: [String] = []
        if !message.isEmpty { lines.append(message) }
        let usesGeo = rules.contains { rule in
            let match = rule["match"] as? String ?? ""
            return match.hasPrefix("geoip:") || match.hasPrefix("geosite:")
        }
        if usesGeo && !geoAssets {
            lines.append(text("Правила geoip: и geosite: работают, только когда рядом с NebulaLink лежат geoip.dat и geosite.dat. Без них такие правила пропускаются.",
                              "geoip: and geosite: rules only work when geoip.dat and geosite.dat are next to NebulaLink. Without them such rules are skipped."))
        }
        lines.append(text("Правила проверяются сверху вниз, срабатывает первое совпавшее. Изменения применяются к работающему туннелю сразу.",
                          "Rules are checked top to bottom and the first match wins. Changes apply to a running tunnel at once."))
        return lines.joined(separator: "\n\n")
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.numberOfLines = 0
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.textColor = .secondaryLabel
        cell.textLabel?.textColor = busy ? .secondaryLabel : .label
        cell.selectionStyle = busy ? .none : .default
        switch indexPath.section {
        case 0:
            let preset = presets[indexPath.row]
            let id = preset["id"] as? String ?? ""
            cell.textLabel?.text = ru ? preset["title_ru"] as? String : preset["title_en"] as? String
            cell.detailTextLabel?.text = presetHint(id)
            cell.imageView?.image = UIImage(systemName: "list.bullet")
            cell.accessoryType = id == routing["preset"] as? String ? .checkmark : .none
        case 1 where indexPath.row == 0:
            cell.textLabel?.text = text("Остальной трафик", "Everything else")
            cell.detailTextLabel?.text = actionTitle(fallback)
            cell.imageView?.image = UIImage(systemName: "arrow.triangle.branch")
            cell.accessoryType = .disclosureIndicator
        case 1:
            cell.textLabel?.text = text("Telegram всегда через прокси", "Telegram always through the proxy")
            cell.detailTextLabel?.text = text("Правила не уводят сам мессенджер из туннеля", "The rules never take the messenger itself out of the tunnel")
            cell.imageView?.image = UIImage(systemName: "lock.shield")
            telegramSwitch.isOn = telegramProxied
            telegramSwitch.isEnabled = !busy
            cell.accessoryView = telegramSwitch
            cell.selectionStyle = .none
        default:
            if indexPath.row == rules.count {
                cell.textLabel?.text = text("Добавить правило", "Add rule")
                cell.detailTextLabel?.text = text("Домен, суффикс, IP или подсеть: example.com, *.ru, 10.0.0.0/8", "Domain, suffix, IP or subnet: example.com, *.ru, 10.0.0.0/8")
                cell.imageView?.image = UIImage(systemName: "plus.circle")
                cell.accessibilityIdentifier = "NebulaLink.Routing.Add"
            } else {
                let rule = rules[indexPath.row]
                cell.textLabel?.text = rule["match"] as? String
                cell.detailTextLabel?.text = actionTitle(rule["action"] as? String ?? "proxy")
                cell.imageView?.image = UIImage(systemName: "globe")
                cell.accessoryType = .disclosureIndicator
            }
        }
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !busy else { return }
        let cell = tableView.cellForRow(at: indexPath)
        switch (indexPath.section, indexPath.row) {
        case (0, let row):
            confirmPreset(presets[row])
        case (1, 0):
            chooseAction(title: text("Остальной трафик", "Everything else"), current: fallback, deletable: false, from: cell) { [weak self] action in
                guard let self = self else { return }
                var next = self.routing
                next["default"] = action == "proxy" ? "" : action
                self.save(next)
            }
        case (2, let row) where row == rules.count:
            askMatch(from: cell)
        case (2, let row):
            let rule = rules[row]
            chooseAction(title: rule["match"] as? String ?? "", current: rule["action"] as? String ?? "proxy",
                         deletable: true, from: cell) { [weak self] action in
                guard let self = self else { return }
                var list = self.rules
                guard row < list.count else { return }
                if action == "delete" { list.remove(at: row) } else { list[row]["action"] = action }
                var next = self.routing
                next["rules"] = list
                self.save(next)
            }
        default: break
        }
    }

    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool {
        indexPath.section == 2 && indexPath.row < rules.count && !busy
    }

    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        guard editingStyle == .delete, indexPath.section == 2, indexPath.row < rules.count else { return }
        var list = rules
        list.remove(at: indexPath.row)
        var next = routing
        next["rules"] = list
        save(next)
    }

    // MARK: - dialogs

    private func present(_ sheet: UIAlertController, from view: UIView?) {
        if let popover = sheet.popoverPresentationController {
            popover.sourceView = view ?? self.view
            popover.sourceRect = view?.bounds ?? CGRect(x: self.view.bounds.midX, y: self.view.bounds.midY, width: 1, height: 1)
        }
        present(sheet, animated: true)
    }

    private func chooseAction(title: String, current: String, deletable: Bool, from view: UIView?, chosen: @escaping (String) -> Void) {
        let sheet = UIAlertController(title: title, message: nil, preferredStyle: .actionSheet)
        for action in Self.actions {
            let label = (action == current ? "✓ " : "") + actionTitle(action)
            sheet.addAction(UIAlertAction(title: label, style: .default) { _ in chosen(action) })
        }
        if deletable {
            sheet.addAction(UIAlertAction(title: text("Удалить правило", "Delete rule"), style: .destructive) { _ in chosen("delete") })
        }
        sheet.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        present(sheet, from: view)
    }

    /// Address first, then action: a rule is never saved half-filled.
    private func askMatch(from view: UIView?) {
        let alert = UIAlertController(title: text("Домен или подсеть", "Domain or subnet"), message: nil, preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "example.com"
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
            field.spellCheckingType = .no
            field.keyboardType = .URL
        }
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Далее", "Next"), style: .default) { [weak self, weak alert] _ in
            guard let self = self else { return }
            let typed = (alert?.textFields?.first?.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !typed.isEmpty else { return }
            self.chooseAction(title: typed, current: "direct", deletable: false, from: view) { [weak self] action in
                guard let self = self else { return }
                var list = self.rules
                list.append(["match": typed, "action": action])
                var next = self.routing
                next["rules"] = list
                self.save(next)
            }
        })
        present(alert, animated: true)
    }

    private func confirmPreset(_ preset: [String: Any]) {
        guard let id = preset["id"] as? String else { return }
        let title = ru ? preset["title_ru"] as? String : preset["title_en"] as? String
        let alert = UIAlertController(title: title,
                                      message: text("Текущие правила будут заменены набором.", "The current rules will be replaced by the preset."),
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        alert.addAction(UIAlertAction(title: text("Применить", "Apply"), style: .default) { [weak self] _ in
            self?.send("routing.preset", ["id": id])
        })
        present(alert, animated: true)
    }

    /// Lifting the Telegram guarantee takes an explicit confirmation.
    @objc private func telegramToggled() {
        if telegramSwitch.isOn {
            var next = routing
            next["telegram_follows_rules"] = false
            save(next)
            return
        }
        let alert = UIAlertController(title: text("Пустить Telegram по правилам?", "Let Telegram follow the rules?"),
                                      message: text("Серверы Telegram пойдут туда, куда их отправят правила, — в том числе напрямую. Там, где Telegram заблокирован, он перестанет подключаться.",
                                                    "Telegram's servers will go wherever the rules send them, direct included. Where Telegram is blocked it will stop connecting."),
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel) { [weak self] _ in
            self?.telegramSwitch.setOn(true, animated: true)
        })
        alert.addAction(UIAlertAction(title: text("Пустить", "Allow"), style: .destructive) { [weak self] _ in
            guard let self = self else { return }
            var next = self.routing
            next["telegram_follows_rules"] = true
            self.save(next)
        })
        present(alert, animated: true)
    }
}
