import UIKit

public final class NebulaLinkController: UITableViewController, UITextFieldDelegate {
    private let ru: Bool
    private let continueAction: (() -> Void)?
    private let overview = NebulaLinkOverviewView()
    private let input = UITextField()
    private let explanation = UILabel()
    private var servers: [[String: Any]] = []
    private var selected = ""
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
    private let service = NebulaLinkService.shared

    public init(russian: Bool, continueAction: (() -> Void)? = nil) {
        ru = russian
        self.continueAction = continueAction
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    private func text(_ russian: String, _ english: String) -> String { ru ? russian : english }
    public override func viewDidLoad() {
        super.viewDidLoad()
        title = "NebulaLink"
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
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
        tableView.keyboardDismissMode = .interactive
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        tableView.tableHeaderView = overview
        overview.onAction = { [weak self] in
            guard let self = self, !self.busy else { return }
            self.view.endEditing(true)
            if self.service.state == "connected" || self.service.state == "connecting" {
                self.request("tunnel.stop")
            } else if !self.selected.isEmpty {
                self.request("tunnel.start", ["id": self.selected])
            }
        }
        NotificationCenter.default.addObserver(self, selector: #selector(statusUpdated), name: NebulaLinkService.statusChanged, object: service)
        NotificationCenter.default.addObserver(self, selector: #selector(probeUpdated(_:)), name: NebulaLinkService.probeProgress, object: service)
        reloadServers()
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
        let size = overview.systemLayoutSizeFitting(CGSize(width: width, height: 0),
                withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        if abs(overview.frame.width - width) > 0.5 || abs(overview.frame.height - size.height) > 0.5 {
            overview.frame = CGRect(x: 0, y: 0, width: width, height: size.height)
            tableView.tableHeaderView = overview
        }
    }
    deinit { NotificationCenter.default.removeObserver(self) }
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
    private func reloadServers() {
        request("servers.list", ["page": page, "per_page": 20]) { [weak self] data in
            guard let self = self, let data = data as? [String: Any] else { return }
            self.servers = data["servers"] as? [[String: Any]] ?? []
            self.selected = data["selected"] as? String ?? ""
            self.page = (data["page"] as? Int) ?? 1
            self.pages = (data["pages"] as? Int) ?? 1
        }
    }
    private var probeActionTitle: String {
        guard probeRequestId != nil else { return text("Nimbo Ping · серверы этой страницы", "Nimbo Ping · servers on this page") }
        return text(probeCancelling ? "Отмена… " : "Отменить Nimbo Ping · ",
                    probeCancelling ? "Cancelling… " : "Cancel Nimbo Ping · ") + "\(probeCompleted)/\(probeTotal)"
    }
    private func serverDetail(_ server: [String: Any]) -> String {
        let latency = NebulaLatency.format(server["latency_ms"] as? Int ?? 0,
                method: server["latency_method"] as? String ?? "",
                checkedAt: (server["checked_at"] as? NSNumber)?.int64Value ?? 0,
                unit: text("мс", "ms"), unknown: "—", failed: text("Нет ответа", "No reply"))
        return (server["protocol"] as? String ?? "").uppercased() + " · " + latency
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
        cancelProbe()
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
                self.probeText = ms < 0 ? self.text("Нет ответа", "No reply") : self.text("Ответ через прокси: \(ms) мс", "Response through proxy: \(ms) ms")
            } else { self.probeText = self.text("Не удалось проверить соединение", "Could not test connection") }
            self.reloadPresentation()
        }
    }
    public override func numberOfSections(in tableView: UITableView) -> Int { continueAction == nil ? 3 : 4 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return 2
        case 1: return 3
        case 2: return max(1, servers.count + (pages > 1 ? 1 : 0))
        default: return 1
        }
    }
    public override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch section {
        case 0: return text("Подписки и ключи", "Subscriptions and keys")
        case 1: return text("Диагностика и обновление", "Diagnostics and refresh")
        case 2: return text("Серверы", "Servers")
        default: return nil
        }
    }
    public override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 { return explanation.text }
        if section == 1 {
            let explanation = text("≈ — оценка: время GET в Nimbo Ping делится на 3,3 и округляется до ближайшей миллисекунды. Проверка соединения через активный прокси показывает исходное время.", "≈ is an estimate: Nimbo Ping GET time divided by 3.3, rounded to the nearest millisecond. The active-proxy connection test shows its original timing.")
            let status = message.isEmpty ? probeText : message
            return status.isEmpty ? explanation : status + "\n" + explanation
        }
        if section == 2 { return text("Выберите сервер, затем нажмите «Подключить». Работает внутри приложения. Фоновая работа зависит от ограничений iOS; уведомления доставляются отдельно через APNs.", "Select a server, then tap Connect. Runs inside the app. Background activity is subject to iOS limits; notifications use APNs separately.") }
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
            cell.textLabel?.text = text("Добавить подписку или ключ", "Add subscription or key")
            cell.imageView?.image = UIImage(systemName: "link")
        } else if indexPath.section == 1 {
            cell.textLabel?.text = [probeActionTitle,
                                   urlProbeId == nil ? text("Проверить соединение", "Test connection") : text("Проверяем соединение…", "Testing connection…"),
                                   text("Обновить подписки", "Refresh subscriptions")][indexPath.row]
            cell.imageView?.image = UIImage(systemName: ["waveform.path.ecg", "network", "arrow.clockwise"][indexPath.row])
            if indexPath.row == 0 { cell.accessibilityIdentifier = "NebulaLink.NimboPing" }
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
                cell.accessoryType = server["id"] as? String == selected ? .checkmark : .none
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
        case (1, 0):
            if probeRequestId == nil { probeVisibleServers() } else { cancelProbe() }
        case (1, 1): probeActiveConnection()
        case (1, 2): request("subscription.refreshAll") { [weak self] _ in self?.reloadServers() }
        case (2, _):
            guard !servers.isEmpty else { return }
            if indexPath.row == servers.count { abandonProbes(); page = page % pages + 1; reloadServers() }
            else if let id = servers[indexPath.row]["id"] as? String {
                request("server.select", ["id": id]) { [weak self] _ in
                    self?.selected = id
                }
            }
        case (3, _):
            if service.state == "connected" { finish() }
            else { request("tunnel.stop") { [weak self] _ in self?.finish() } }
        default: break
        }
    }
}
