import UIKit

public final class NebulaLinkController: UITableViewController, UITextFieldDelegate {
    private let ru: Bool
    private let continueAction: (() -> Void)?
    private let overview = NebulaLinkOverviewView()
    private let introduction: UIView?
    private let introductionVisibility: ((Bool) -> Void)?
    private let header = UIStackView()
    private let input = UITextField()
    private let explanation = UILabel()
    private var servers: [[String: Any]] = []
    private var selected = ""
    private var serverSort = "default"
    private var page = 1
    private var pages = 1
    private var busy = false
    private var message = ""
    private var probeText = ""
    private var probeRequestId: String?
    private var probeCancelling = false
    private var probeCompleted = 0
    private var probeTotal = 0
    private var urlProbeId: String?
    /// Last auto-select state from the core ("auto.status").
    private var autoState: [String: Any] = [:]
    private var autoEnabled: Bool { autoState["enabled"] as? Bool ?? false }
    private let service = NebulaLinkService.shared

    public init(russian: Bool, introduction: UIView? = nil, introductionVisibility: ((Bool) -> Void)? = nil, continueAction: (() -> Void)? = nil) {
        ru = russian
        self.introduction = introduction; self.introductionVisibility = introductionVisibility
        self.continueAction = continueAction
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }
    public override func viewDidLoad() {
        super.viewDidLoad()
        title = "NebulaLink"
        navigationItem.leftBarButtonItem = continueAction == nil
            ? UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
            : UIBarButtonItem(title: text("Пропустить", "Skip"), style: .plain, target: self, action: #selector(close))
        input.placeholder = text("Подписка или ключ сервера", "Subscription or server key")
        input.autocapitalizationType = .none
        input.autocorrectionType = .no
        input.spellCheckingType = .no
        input.keyboardType = .URL
        input.clearButtonMode = .whileEditing
        input.textContentType = .none
        input.delegate = self
        input.accessibilityIdentifier = "NebulaLink.Subscription"
        explanation.numberOfLines = 0
        explanation.font = .preferredFont(forTextStyle: .subheadline)
        explanation.adjustsFontForContentSizeCategory = true
        explanation.textColor = .secondaryLabel
        explanation.text = text("Только трафик Telegram, без системного VPN. Вставьте свою подписку или ключ. Ссылку получает указанный вами сервер подписки; она не отправляется разработчикам NebulaGram.", "Telegram traffic only, without a system VPN. Paste your subscription or key. Subscription URLs are requested from the server you specify, not sent to NebulaGram developers.")
        refreshControl = UIRefreshControl()
        refreshControl?.addTarget(self, action: #selector(refreshSubscriptions), for: .valueChanged)
        tableView.keyboardDismissMode = .interactive
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        header.axis = .vertical; header.spacing = 0
        if let introduction { header.addArrangedSubview(introduction) }
        if continueAction != nil {
            let optional = UILabel()
            optional.text = text("Необязательный шаг · подключение можно настроить позже", "Optional step · set up a connection later")
            optional.font = .preferredFont(forTextStyle: .footnote)
            optional.adjustsFontForContentSizeCategory = true
            optional.numberOfLines = 0
            optional.textColor = .secondaryLabel
            optional.translatesAutoresizingMaskIntoConstraints = false
            let container = UIView()
            container.addSubview(optional)
            NSLayoutConstraint.activate([
                optional.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 20),
                optional.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -20),
                optional.topAnchor.constraint(equalTo: container.topAnchor, constant: 8),
                optional.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -4)
            ])
            header.addArrangedSubview(container)
        }
        header.addArrangedSubview(overview)
        tableView.tableHeaderView = header
        overview.onAction = { [weak self] in
            guard let self = self, !self.busy else { return }
            self.view.endEditing(true)
            if self.service.state == "connected" || self.service.state == "connecting" {
                self.request("tunnel.stop")
            } else if self.autoEnabled {
                // No id: the core measures the list and connects to the fastest.
                self.request("tunnel.start")
            } else if !self.selected.isEmpty {
                self.request("tunnel.start", ["id": self.selected])
            }
        }
        NotificationCenter.default.addObserver(self, selector: #selector(statusUpdated), name: NebulaLinkService.statusChanged, object: service)
        NotificationCenter.default.addObserver(self, selector: #selector(probeUpdated(_:)), name: NebulaLinkService.probeProgress, object: service)
        NotificationCenter.default.addObserver(self, selector: #selector(autoUpdated(_:)), name: NebulaLinkService.autoChanged, object: service)
        reloadServers()
        reloadAuto()
        reloadSort()
    }
    private func reloadPresentation() {
        overview.update(state: service.state, busy: busy, hasSelection: !selected.isEmpty, russian: ru)
        tableView.reloadData()
        view.setNeedsLayout()
    }
    public override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let width = tableView.bounds.width
        guard width > 0 else { return }
        let size = header.systemLayoutSizeFitting(CGSize(width: width, height: 0),
                withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        if abs(header.frame.width - width) > 0.5 || abs(header.frame.height - size.height) > 0.5 {
            header.frame = CGRect(x: 0, y: 0, width: width, height: size.height)
            tableView.tableHeaderView = header
        }
    }
    deinit { NotificationCenter.default.removeObserver(self) }
    public override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); introductionVisibility?(true) }
    public override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); introductionVisibility?(false) }
    @objc private func statusUpdated() {
        // A proxy-state notification must not rebuild the subscription field while typing.
        overview.update(state: service.state, busy: busy, hasSelection: !selected.isEmpty, russian: ru)
        view.setNeedsLayout()
        let continuation = IndexPath(row: 0, section: 3)
        if continueAction != nil, tableView.indexPathsForVisibleRows?.contains(continuation) == true {
            tableView.reloadRows(at: [continuation], with: .none)
        }
    }
    @objc private func close() {
        guard !busy else { return }
        abandonProbes()
        dismiss(animated: true)
    }
    public override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        abandonProbes()
    }
    private func finish() {
        abandonProbes()
        let action = continueAction
        dismiss(animated: true) { action?() }
    }
    public func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
        guard let current = textField.text, let r = Range(range, in: current) else { return false }
        return current.replacingCharacters(in: r, with: string).utf8.count <= 65536
    }
    private func request(_ method: String, _ payload: [String: Any] = [:], after: ((Any) -> Void)? = nil) {
        guard !busy else { return }
        busy = true; input.isEnabled = false; isModalInPresentation = true
        navigationController?.isModalInPresentation = true
        navigationItem.leftBarButtonItem?.isEnabled = false
        message = text("Подождите…", "Please wait…")
        reloadPresentation()
        service.call(method, payload: payload) { [weak self] result in
            guard let self = self else { return }
            self.refreshControl?.endRefreshing()
            self.busy = false; self.input.isEnabled = true; self.isModalInPresentation = false
            self.navigationController?.isModalInPresentation = false
            self.navigationItem.leftBarButtonItem?.isEnabled = true
            switch result {
            case let .success(data):
                self.message = ""
                after?(data)
            case .failure:
                // Core errors may contain subscription credentials; never display or log them verbatim.
                self.message = self.text("Не удалось выполнить действие. Проверьте ключ, доступность сервера и поддерживаемый протокол.", "Could not complete the action. Check the key, server availability and supported protocol.")
            }
            self.reloadPresentation()
        }
    }
    @objc private func refreshSubscriptions() {
        guard !busy else { refreshControl?.endRefreshing(); return }
        view.endEditing(true)
        request("subscription.refreshAll") { [weak self] data in
            guard let self = self else { return }
            let results = data as? [[String: Any]] ?? []
            let failures = results.filter { $0["error"] != nil }.count
            let updated = results.count - failures
            self.probeText = failures == 0
                ? self.text("Обновлено подписок: \(updated)", "Subscriptions refreshed: \(updated)")
                : self.text("Обновлено: \(updated) · ошибок: \(failures)",
                            "Refreshed: \(updated) · failed: \(failures)")
            self.reloadServers()
        }
    }
    private func reloadServers() {
        request("servers.list", ["page": page, "per_page": 20]) { [weak self] data in
            guard let self = self, let data = data as? [String: Any] else { return }
            self.servers = data["servers"] as? [[String: Any]] ?? []
            self.selected = data["selected"] as? String ?? ""
            self.page = (data["page"] as? Int) ?? 1
            self.pages = (data["pages"] as? Int) ?? 1
        }
    }
    private func reloadAuto() {
        service.call("auto.status") { [weak self] result in
            guard let self = self, case let .success(data) = result, let state = data as? [String: Any] else { return }
            self.autoState = state
            self.reloadAutoRow()
        }
    }
    @objc private func autoUpdated(_ notification: Notification) {
        guard let state = notification.userInfo as? [String: Any] else { return }
        autoState = state
        if state["state"] as? String == "ready", let id = state["server_id"] as? String, !id.isEmpty, id != selected {
            // Auto moved the tunnel: the selection in the list follows it.
            selected = id
            if !busy { tableView.reloadSections(IndexSet(integer: 2), with: .none) }
        }
        reloadAutoRow()
    }
    private func reloadAutoRow() {
        guard !busy, tableView.numberOfSections > 3 else { return }
        tableView.reloadRows(at: [IndexPath(row: 0, section: 3)], with: .none)
    }
    private var autoDetail: String {
        let phase = autoState["state"] as? String ?? "idle"
        if phase == "checking" {
            let measured = autoState["measured"] as? Int ?? 0
            let total = autoState["total"] as? Int ?? 0
            let progress = total > 0 && measured > 0 ? " \(measured)/\(total)" : ""
            return text("Проверяю серверы…", "Checking servers…") + progress
        }
        guard autoEnabled else {
            return text("Подключаться к серверу с наименьшим откликом", "Connect to the server with the lowest latency")
        }
        if phase == "no_answer" { return text("Ни один сервер не ответил", "No server answered") }
        if phase == "failed" { return text("Не удалось подключиться", "Could not connect") }
        let id = autoState["server_id"] as? String ?? ""
        guard !id.isEmpty, let name = autoState["server_name"] as? String else {
            return text("Выберет сервер при подключении", "Picks a server on connect")
        }
        let latency = NebulaLatency.format(autoState["latency_ms"] as? Int ?? 0,
                method: autoState["latency_method"] as? String ?? "",
                checkedAt: (autoState["checked_at"] as? NSNumber)?.int64Value ?? 0,
                unit: text("мс", "ms"), unknown: "—", failed: text("Нет ответа", "No reply"))
        let flag = autoState["flag"] as? String ?? ""
        return [flag.isEmpty ? name : flag + " " + name, latency].joined(separator: " · ")
    }
    private func chooseAuto(from cell: UIView?) {
        guard autoEnabled else {
            request("auto.set", ["enabled": true]) { [weak self] data in
                if let state = data as? [String: Any] { self?.autoState = state }
                self?.reloadServers()
            }
            return
        }
        let sheet = UIAlertController(title: text("Автовыбор", "Auto"), message: autoDetail, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: text("Проверить серверы сейчас", "Check servers now"), style: .default) { [weak self] _ in
            self?.request("auto.check") { [weak self] data in
                if let state = data as? [String: Any] { self?.autoState = state }
                self?.reloadServers()
            }
        })
        sheet.addAction(UIAlertAction(title: text("Выключить автовыбор", "Turn auto off"), style: .destructive) { [weak self] _ in
            self?.request("auto.set", ["enabled": false]) { [weak self] data in
                if let state = data as? [String: Any] { self?.autoState = state }
            }
        })
        sheet.addAction(UIAlertAction(title: text("Отмена", "Cancel"), style: .cancel))
        if let popover = sheet.popoverPresentationController, let cell = cell {
            popover.sourceView = cell
            popover.sourceRect = cell.bounds
        }
        present(sheet, animated: true)
    }
    private func reloadSort() {
        service.call("settings.get") { [weak self] result in
            guard let self = self, case let .success(data) = result,
                  let settings = data as? [String: Any] else { return }
            self.serverSort = settings["server_sort"] as? String == "latency" ? "latency" : "default"
            let path = IndexPath(row: 1, section: 1)
            if self.tableView.indexPathsForVisibleRows?.contains(path) == true {
                self.tableView.reloadRows(at: [path], with: .none)
            }
        }
    }
    private func chooseSort() {
        let choice = NebulaServerSortController(russian: ru, selected: serverSort) { [weak self] value in
            guard let self = self, value != self.serverSort else { return }
            self.request("settings.set", ["server_sort": value]) { [weak self] _ in
                guard let self = self else { return }
                self.serverSort = value
                self.page = 1
                self.reloadServers()
            }
        }
        let sheet = UINavigationController(rootViewController: choice)
        sheet.modalPresentationStyle = .formSheet
        if #available(iOS 15.0, *) { sheet.sheetPresentationController?.detents = [.medium()] }
        present(sheet, animated: true)
    }
    private var probeActionTitle: String {
        guard probeRequestId != nil else { return text("Пинг серверов", "Ping servers") }
        return text(probeCancelling ? "Отмена… " : "Отменить · ",
                    probeCancelling ? "Cancelling… " : "Cancel · ") + "\(probeCompleted)/\(probeTotal)"
    }
    private func serverDetail(_ server: [String: Any]) -> String {
        let latency = NebulaLatency.format(server["latency_ms"] as? Int ?? 0,
                method: server["latency_method"] as? String ?? "",
                checkedAt: (server["checked_at"] as? NSNumber)?.int64Value ?? 0,
                unit: text("мс", "ms"), unknown: "—", failed: text("Нет ответа", "No reply"))
        let protocolName = (server["protocol"] as? String ?? "").uppercased()
        let description = (server["description"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return [protocolName, description, latency].filter { !$0.isEmpty }.joined(separator: " · ")
    }
    private func probeVisibleServers() {
        guard probeRequestId == nil else { return }
        let ids = servers.compactMap { $0["id"] as? String }.filter { !$0.isEmpty }
        // An empty ids array means ALL backend servers; this action is page-scoped.
        guard !ids.isEmpty else { return }
        let requestId = UUID().uuidString
        probeRequestId = requestId; probeCancelling = false
        probeCompleted = 0; probeTotal = ids.count; probeText = ""
        reloadPresentation()
        service.call("probe.servers", payload: ["ids": ids, "method": "nimbo", "timeout": 5, "request_id": requestId]) { [weak self] result in
            guard let self = self, self.probeRequestId == requestId else { return }
            let cancelled = self.probeCancelling
            self.probeRequestId = nil; self.probeCancelling = false
            switch result {
            case .success:
                self.probeText = cancelled ? self.text("Проверка отменена", "Check cancelled") : self.text("Проверка завершена", "Check complete")
            case .failure:
                self.probeText = cancelled ? self.text("Проверка отменена", "Check cancelled") : self.text("Не удалось проверить серверы", "Could not check servers")
            }
            self.reloadPresentation()
            self.refreshLatencies()
        }
    }
    private func cancelProbe() {
        guard let requestId = probeRequestId, !probeCancelling else { return }
        probeCancelling = true
        reloadPresentation()
        service.call("probe.cancel", payload: ["request_id": requestId]) { _ in }
    }
    private func abandonProbes() {
        // The core owns the batch; navigating away must leave it running.
        probeRequestId = nil; probeCancelling = false; urlProbeId = nil
    }
    @objc private func probeUpdated(_ notification: Notification) {
        guard let progress = notification.userInfo, let requestId = probeRequestId,
              progress["request_id"] as? String == requestId else { return }
        if progress["cancelled"] as? Bool == true { probeCancelling = true }
        probeCompleted = progress["completed"] as? Int ?? probeCompleted
        probeTotal = progress["total"] as? Int ?? probeTotal
        var changed = [IndexPath(row: 0, section: 1)]
        if let id = progress["id"] as? String, let ms = progress["latency_ms"] as? Int,
           let index = servers.firstIndex(where: { $0["id"] as? String == id }) {
            servers[index]["latency_ms"] = ms
            servers[index]["latency_method"] = progress["latency_method"] as? String ?? ""
            servers[index]["checked_at"] = progress["checked_at"] as? NSNumber ?? 0
            changed.append(IndexPath(row: index, section: 2))
        }
        let visible = Set(tableView.indexPathsForVisibleRows ?? [])
        tableView.reloadRows(at: changed.filter { visible.contains($0) }, with: .none)
    }
    private func refreshLatencies() {
        let requestedPage = page
        service.call("servers.list", payload: ["page": page, "per_page": 20]) { [weak self] result in
            guard let self = self, self.page == requestedPage, self.probeRequestId == nil, case let .success(data) = result,
                  let object = data as? [String: Any], let latest = object["servers"] as? [[String: Any]] else { return }
            // Refresh measurement fields only; a concurrent selection stays selected.
            for index in self.servers.indices {
                guard let id = self.servers[index]["id"] as? String,
                      let server = latest.first(where: { $0["id"] as? String == id }) else { continue }
                for key in ["latency_ms", "latency_method", "checked_at"] { self.servers[index][key] = server[key] }
            }
            self.reloadPresentation()
        }
    }
    private func probeActiveConnection() {
        guard urlProbeId == nil else { return }
        let requestId = UUID().uuidString
        urlProbeId = requestId
        reloadPresentation()
        // Active-tunnel URL test is unscaled and does not lock connection controls.
        service.call("probe.url", payload: ["url": "https://telegram.org"]) { [weak self] result in
            guard let self = self, self.urlProbeId == requestId else { return }
            self.urlProbeId = nil
            if case let .success(data) = result, let ms = (data as? [String: Any])?["latency_ms"] as? Int {
                self.probeText = ms < 0 ? self.text("Нет ответа", "No reply") : self.text("\(ms) мс", "\(ms) ms")
            } else { self.probeText = self.text("Не удалось проверить соединение", "Could not test connection") }
            self.reloadPresentation()
        }
    }
    public override func numberOfSections(in tableView: UITableView) -> Int { continueAction == nil ? 4 : 5 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return 4
        case 1: return 4
        case 2: return max(1, servers.count + (pages > 1 ? 1 : 0))
        case 3: return 2
        default: return 1
        }
    }
    public override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch section {
        case 0: return text("Подписки и ключи", "Subscriptions and keys")
        case 1: return text("Действия с серверами", "Server actions")
        case 2: return text("Серверы", "Servers")
        case 3: return text("Автовыбор и маршрутизация", "Auto and routing")
        default: return nil
        }
    }
    public override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 { return explanation.text }
        if section == 1 {
            let status = message.isEmpty ? probeText : message
            return status.isEmpty ? nil : status
        }
        if section == 2 { return text("Выберите сервер, затем нажмите «Подключить». Работает внутри приложения. Фоновая работа зависит от ограничений iOS; уведомления доставляются отдельно через APNs.", "Select a server, then tap Connect. Runs inside the app. Background activity is subject to iOS limits; notifications use APNs separately.") }
        if section == 3 { return text("Авто сам выбирает самый быстрый сервер и меняет его, только когда другой заметно быстрее или текущий перестал отвечать. Выбор сервера вручную выключает авто.", "Auto picks the fastest server and moves only when another is clearly faster or the current one stops answering. Choosing a server by hand turns auto off.") }
        return nil
    }
    public override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.textColor = busy ? .secondaryLabel : .label
        cell.selectionStyle = busy ? .none : .default
        if indexPath.section == 0 && indexPath.row == 0 {
            input.removeFromSuperview(); input.translatesAutoresizingMaskIntoConstraints = false
            cell.contentView.addSubview(input)
            NSLayoutConstraint.activate([
                input.leadingAnchor.constraint(equalTo: cell.contentView.layoutMarginsGuide.leadingAnchor),
                input.trailingAnchor.constraint(equalTo: cell.contentView.layoutMarginsGuide.trailingAnchor),
                input.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 12),
                input.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -12),
                input.heightAnchor.constraint(greaterThanOrEqualToConstant: 28)
            ])
            cell.selectionStyle = .none
        } else if indexPath.section == 0 {
            cell.textLabel?.text = indexPath.row == 3 ? text("Из буфера", "From clipboard") : indexPath.row == 1
                ? text("Добавить подписку или ключ", "Add subscription or key")
                : text("Управление подписками", "Manage subscriptions")
            cell.imageView?.image = UIImage(systemName: indexPath.row == 3 ? "doc.on.clipboard" : indexPath.row == 1 ? "link" : "list.bullet.rectangle")
            if indexPath.row == 3 { cell.accessibilityIdentifier = "NebulaLink.Paste" }
            if indexPath.row == 2 { cell.accessoryType = .disclosureIndicator }
        } else if indexPath.section == 1 {
            cell.textLabel?.text = [probeActionTitle,
                                   text("Сортировка серверов", "Server sorting"),
                                   text("Обновить подписки", "Refresh subscriptions"),
                                   urlProbeId == nil ? text("Проверить соединение", "Test connection") : text("Проверяем соединение…", "Testing connection…")][indexPath.row]
            cell.imageView?.image = UIImage(systemName: ["waveform.path.ecg", "line.3.horizontal.decrease", "arrow.clockwise", "network"][indexPath.row])
            if indexPath.row == 0 { cell.accessibilityIdentifier = "NebulaLink.NimboPing" }
            if indexPath.row == 1 {
                cell.detailTextLabel?.text = serverSort == "latency"
                    ? text("По задержке", "By latency")
                    : text("По умолчанию (порядок подписки)", "Default (subscription order)")
                cell.accessoryType = .disclosureIndicator
            }
            if indexPath.row == 2 { cell.accessibilityIdentifier = "NebulaLink.RefreshSubscriptions" }
        } else if indexPath.section == 2 {
            if servers.isEmpty {
                cell.textLabel?.text = text("Добавьте подписку или ключ выше", "Add a subscription or key above")
                cell.selectionStyle = .none
            } else if indexPath.row == servers.count {
                cell.textLabel?.text = text("Страница \(page) из \(pages) · Далее", "Page \(page) of \(pages) · Next")
            } else {
                let server = servers[indexPath.row]
                cell.textLabel?.text = server["name"] as? String ?? "Server"
                cell.imageView?.image = UIImage(systemName: "server.rack")
                cell.detailTextLabel?.text = serverDetail(server)
                cell.detailTextLabel?.numberOfLines = 0
                cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
                // With auto on the checkmark belongs to the Auto row, which names the pick.
                cell.accessoryType = !autoEnabled && server["id"] as? String == selected ? .checkmark : .none
            }
        } else if indexPath.section == 3 {
            cell.detailTextLabel?.numberOfLines = 0
            cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
            if indexPath.row == 0 {
                cell.textLabel?.text = text("Авто — самый быстрый", "Auto — the fastest")
                cell.imageView?.image = UIImage(systemName: "bolt.horizontal")
                cell.detailTextLabel?.text = autoDetail
                cell.accessoryType = autoEnabled ? .checkmark : .none
                cell.accessibilityIdentifier = "NebulaLink.Auto"
            } else {
                cell.textLabel?.text = text("Маршрутизация", "Routing")
                cell.imageView?.image = UIImage(systemName: "arrow.triangle.branch")
                cell.detailTextLabel?.text = text("Что идёт напрямую, что через прокси, что блокируется", "What goes direct, through the proxy or nowhere")
                cell.accessoryType = .disclosureIndicator
                cell.accessibilityIdentifier = "NebulaLink.Routing"
            }
        } else {
            cell.textLabel?.text = service.state == "connected" ? text("Продолжить с NebulaLink", "Continue with NebulaLink") : text("Продолжить без NebulaLink", "Continue without NebulaLink")
        }
        return cell
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard !busy else { return }
        view.endEditing(true)
        switch (indexPath.section, indexPath.row) {
        case (0, 1):
            let value = (input.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { input.becomeFirstResponder(); return }
            request("onboarding.import", ["input": value]) { [weak self] _ in
                self?.input.text = ""; self?.page = 1; self?.reloadServers()
            }
        case (0, 2):
            let manager = NebulaSubscriptionsController(russian: ru)
            manager.onChange = { [weak self] in
                self?.page = 1
                self?.reloadServers()
            }
            navigationController?.pushViewController(manager, animated: true)
        case (0, 3):
            guard let value = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !value.isEmpty, value.utf8.count <= 512 * 1024 else {
                message = text("В буфере нет текста для импорта", "No import text in the clipboard")
                tableView.reloadData(); return
            }
            input.text = value
            input.becomeFirstResponder()
        case (1, 0):
            if probeRequestId == nil { probeVisibleServers() } else { cancelProbe() }
        case (1, 1): chooseSort()
        case (1, 2): refreshSubscriptions()
        case (1, 3): probeActiveConnection()
        case (2, _):
            guard !servers.isEmpty else { return }
            if indexPath.row == servers.count { abandonProbes(); page = page % pages + 1; reloadServers() }
            else if let id = servers[indexPath.row]["id"] as? String {
                request("server.select", ["id": id]) { [weak self] _ in
                    self?.selected = id
                    // The core turns auto off on a manual pick; mirror it at once.
                    self?.autoState["enabled"] = false
                }
            }
        case (3, 0): chooseAuto(from: tableView.cellForRow(at: indexPath))
        case (3, 1): navigationController?.pushViewController(NebulaRoutingController(russian: ru), animated: true)
        case (4, _):
            if service.state == "connected" { finish() }
            else { request("tunnel.stop") { [weak self] _ in self?.finish() } }
        default: break
        }
    }
}
