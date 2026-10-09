import UIKit
import AccountContext
import TelegramPresentationData

/// Values shown here come from the installed application and the pinned iOS
/// source revision. No Android version or CI run time is substituted for them.
public final class NebulaBuildInfoController: UITableViewController {
    private let russian: Bool
    private let context: AccountContext
    private let theme: PresentationTheme
    private lazy var hero = NebulaSettingsHero(symbol: "info.circle", title: russian ? "Обновления" : "Updates",
        summary: russian ? "Версия приложения и новости выпусков" : "Installed version and release news", context: context, theme: theme)
    private let sourceVersion = "12.9.2"
    private let sourceRevision = "6ad963e5b62d354da79040f388ae2b9132fb17b8"

    public init(context: AccountContext, russian: Bool) {
        self.context = context
        self.theme = context.sharedContext.currentPresentationData.with { $0 }.theme
        self.russian = russian
        super.init(style: .insetGrouped)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    public override func viewDidLoad() {
        super.viewDidLoad()
        title = russian ? "О сборке" : "Build information"
        NebulaSettingsStyle.apply(theme: theme, to: self)
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 56
    }
    public override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); hero.fit(in: tableView) }
    public override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); hero.setPageVisible(true) }
    public override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); hero.setPageVisible(false) }
    @objc private func close() { dismiss(animated: true) }

    private var rows: [(String, String)] {
        let info = Bundle.main.infoDictionary ?? [:]
        let version = info["CFBundleShortVersionString"] as? String ?? "—"
        let number = info["CFBundleVersion"] as? String ?? "—"
        #if arch(arm64)
        let architecture = "arm64"
        #elseif arch(x86_64)
        let architecture = "x86_64"
        #else
        let architecture = "unknown"
        #endif
        var values = [
            (russian ? "Приложение" : "Application", "NebulaGram \(version)"),
            (russian ? "Номер сборки" : "Build number", number),
            (russian ? "Архитектура" : "Architecture", architecture),
            (russian ? "Основа" : "Based on", "Telegram iOS \(sourceVersion)"),
            (russian ? "Версия исходного кода" : "Source revision", sourceRevision)
        ]
        if let date = info["NebulaBuildDate"] as? String, !date.isEmpty {
            values.append((russian ? "Дата сборки" : "Build date", date))
        }
        values.append((russian ? "Новости и обновления" : "Release news", "@ngram_releases"))
        return values
    }

    public override func numberOfSections(in tableView: UITableView) -> Int { 1 }
    public override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { rows.count }
    public override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "detail") ?? UITableViewCell(style: .subtitle, reuseIdentifier: "detail")
        let row = rows[indexPath.row]
        cell.textLabel?.text = row.0
        cell.detailTextLabel?.text = row.1
        cell.detailTextLabel?.numberOfLines = 0
        cell.selectionStyle = .default
        cell.accessoryType = indexPath.row == rows.count - 1 ? .disclosureIndicator : .none
        NebulaSettingsStyle.finish(cell, theme: theme)
        return cell
    }
    public override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.row == rows.count - 1 {
            if let url = URL(string: "https://t.me/ngram_releases") { UIApplication.shared.open(url) }
        } else {
            UIPasteboard.general.string = rows[indexPath.row].1
        }
    }
}
