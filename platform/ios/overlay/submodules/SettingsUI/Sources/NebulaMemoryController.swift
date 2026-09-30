import Foundation
import UIKit
import Darwin
import AccountContext

/// Root controller installs this once so warnings remain active after leaving Settings.
public enum NebulaMemoryWarnings {
    private static let key = "app.nebulagram.memory.warning"
    private static var observer: NSObjectProtocol?
    public static var enabled: Bool { UserDefaults.standard.bool(forKey: key) }
    public static func setEnabled(_ value: Bool) { UserDefaults.standard.set(value, forKey: key) }
    public static func install() {
        guard observer == nil else { return }
        observer = NotificationCenter.default.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main) { _ in
            guard NebulaMemoryWarnings.enabled, UIApplication.shared.applicationState == .active,
                  let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).flatMap({ $0.windows }).first(where: { $0.isKeyWindow }) else { return }
            var presenter = window.rootViewController
            while let shown = presenter?.presentedViewController { presenter = shown }
            guard let presenter, !(presenter is UIAlertController) else { return }
            let ru = Locale.current.languageCode == "ru"
            let alert = UIAlertController(title: ru ? "Недостаточно памяти" : "Low memory", message: ru ? "iOS запросила освобождение памяти приложения." : "iOS requested the app to release memory.", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            presenter.present(alert, animated: true)
        }
    }
}

/// Experimental, visible-only process memory view using public Mach metrics.
final class NebulaMemoryController: UITableViewController {
    private let context: AccountContext
    private let russian: Bool
    private var timer: Timer?
    private var residentBytes: UInt64?
    private func text(_ ru: String, _ en: String) -> String { russian ? ru : en }
    init(context: AccountContext, russian: Bool) {
        self.context = context
        self.russian = russian
        super.init(style: .insetGrouped)
        title = russian ? "Память приложения" : "App memory"
        navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit {
        timer?.invalidate()
    }
    override func viewDidLoad() {
        super.viewDidLoad()
        let theme = context.sharedContext.currentPresentationData.with { $0 }.theme
        tableView.backgroundColor = theme.list.blocksBackgroundColor
        tableView.separatorColor = theme.list.itemSecondaryTextColor.withAlphaComponent(0.12)
        view.tintColor = theme.list.itemAccentColor
        let navigationAppearance = UINavigationBarAppearance()
        navigationAppearance.configureWithOpaqueBackground()
        navigationAppearance.backgroundColor = theme.list.blocksBackgroundColor
        navigationAppearance.titleTextAttributes = [.foregroundColor: theme.list.itemPrimaryTextColor]
        navigationController?.navigationBar.standardAppearance = navigationAppearance
        navigationController?.navigationBar.scrollEdgeAppearance = navigationAppearance
        sample()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        sample()
        timer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in self?.sample() }
    }
    override func viewWillDisappear(_ animated: Bool) {
        timer?.invalidate(); timer = nil
        super.viewWillDisappear(animated)
    }
    private func sample() {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<integer_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { pointer in
            pointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { value in
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), value, &count)
            }
        }
        residentBytes = result == KERN_SUCCESS ? UInt64(info.resident_size) : nil
        if isViewLoaded { tableView.reloadData() }
    }
    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 2 : 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? text("Текущий процесс", "Current process") : text("Экспериментально", "Experimental")
    }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 0 ? text("Показатели обновляются, пока открыт экран. iOS управляет освобождением памяти автоматически.", "Metrics refresh while this screen is open. iOS manages memory reclamation automatically.") : nil
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .value1, reuseIdentifier: nil)
        let theme = context.sharedContext.currentPresentationData.with { $0 }.theme
        defer { NebulaSettingsStyle.finish(cell, theme: theme) }
        cell.selectionStyle = .none
        if indexPath.section == 1 {
            cell.textLabel?.text = text("Предупреждение о нехватке памяти", "Low-memory warning")
            let control = UISwitch(); control.isOn = NebulaMemoryWarnings.enabled
            control.addTarget(self, action: #selector(warningChanged(_:)), for: .valueChanged)
            cell.accessoryView = control
        } else if indexPath.row == 0 {
            cell.textLabel?.text = text("Память процесса", "Process memory")
            cell.detailTextLabel?.text = residentBytes.map { String(format: "%.1f MB", Double($0) / 1_048_576) } ?? "—"
        } else {
            cell.textLabel?.text = text("Память устройства", "Device memory")
            cell.detailTextLabel?.text = String(format: "%.1f GB", Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824)
        }
        return cell
    }
    @objc private func warningChanged(_ sender: UISwitch) { NebulaMemoryWarnings.setEnabled(sender.isOn) }
    @objc private func close() { dismiss(animated: true) }
}
