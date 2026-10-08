import UIKit
import UniformTypeIdentifiers
import AppBundle
import TelegramPresentationData
import NebulaSettingsContract

final class NebulaIconPacksController: UITableViewController, UIDocumentPickerDelegate {
    private let ru: Bool
    private let theme: PresentationTheme
    private var busy = false
    private var packs: [NebulaInstalledIconPack] = []
    init(russian: Bool, theme: PresentationTheme) { ru = russian; self.theme = theme; super.init(style: .insetGrouped); title = russian ? "Наборы иконок" : "Icon packs" }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() { super.viewDidLoad(); NebulaSettingsStyle.apply(theme: theme, to: self); tableView.rowHeight = UITableView.automaticDimension; tableView.estimatedRowHeight = 64; packs = NebulaImportedIcons.installed; navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close)) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 4 : section == 1 ? packs.count : 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { section == 0 ? (ru ? "Встроенные" : "Built-in") : section == 1 ? (ru ? "Импортированные" : "Imported") : nil }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? { section == 2 ? (ru ? "Формат .icons · до 16 МБ. Поддерживаемые значки заменяются при следующем открытии экрана; остальные сохраняют штатный вид. Доступные значки зависят от платформы. Файлы наборов не входят в перенос настроек." : ".icons format · up to 16 MB. Supported icons update when you reopen the screen; other icons keep their native appearance. Available icons depend on the platform. Pack files are not included in settings transfer.") : nil }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil); NebulaSettingsStyle.finish(cell, theme: theme)
        var selected = false
        if indexPath.section == 0 { cell.textLabel?.text = ["Telegram", "Cupertino", "Solar", "Remix Outline"][indexPath.row]; selected = indexPath.row < 3 && NebulaImportedIcons.activeID == nil && NebulaSettingsStore.shared.iconPack == indexPath.row }
        else if indexPath.section == 1 { let pack = packs[indexPath.row]; cell.textLabel?.text = pack.name; cell.detailTextLabel?.text = pack.author + " · " + String(pack.count) + (ru ? " значков" : " icons"); selected = pack.id == NebulaImportedIcons.activeID }
        else { cell.textLabel?.text = busy ? (ru ? "Загрузка…" : "Loading…") : (ru ? "Импортировать .icons" : "Import .icons"); cell.accessoryType = .disclosureIndicator }
        if selected { cell.backgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.18); cell.accessibilityTraits.insert(.selected) }
        cell.isUserInteractionEnabled = !busy; return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true); guard !busy else { return }
        do {
            if indexPath.section == 0 && indexPath.row < 3 { try NebulaSettingsStore.shared.set(.integer(indexPath.row), for: "icon_pack"); try NebulaImportedIcons.select(nil); tableView.reloadData() }
            else if indexPath.section == 1 { try NebulaImportedIcons.select(packs[indexPath.row]); tableView.reloadData() }
            else if indexPath.section == 0 { guard let data = NSDataAsset(name: "NebulaRemixOutline", bundle: getAppBundle())?.data else { throw NebulaIconPackManifest.Failure.invalid }; install(data) }
            else {
                let picker: UIDocumentPickerViewController
                if #available(iOS 14.0, *) { picker = UIDocumentPickerViewController(forOpeningContentTypes: [.data], asCopy: false) }
                else { picker = UIDocumentPickerViewController(documentTypes: ["public.data"], in: .open) }
                picker.allowsMultipleSelection = false; picker.delegate = self; present(picker, animated: true)
            }
        } catch { report() }
    }
    private func install(_ data: Data) {
        busy = true; tableView.reloadData()
        NebulaImportedIcons.install(data: data) { [weak self] result in
            guard let self else { return }; self.busy = false; self.packs = NebulaImportedIcons.installed
            do { try NebulaImportedIcons.select(result.get()) } catch { self.report() }
            self.tableView.reloadData()
        }
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first, urls.count == 1 else { return }
        busy = true; tableView.reloadData()
        controller.dismiss(animated: true) { [weak self] in
            DispatchQueue.global(qos: .userInitiated).async {
                let result = Result { () throws -> Data in
                    let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    guard let stream = InputStream(url: url) else { throw NebulaIconPackManifest.Failure.invalid }
                    stream.open(); defer { stream.close() }
                    var data = Data(), buffer = [UInt8](repeating: 0, count: 65536)
                    while true {
                        let count = stream.read(&buffer, maxLength: 65536)
                        if count < 0 { throw stream.streamError ?? NebulaIconPackManifest.Failure.invalid }
                        if count == 0 { break }
                        guard data.count + count <= 16_000_000 else { throw NebulaIconPackManifest.Failure.invalid }
                        data.append(contentsOf: buffer.prefix(count))
                    }
                    return data
                }
                DispatchQueue.main.async { guard let self else { return }; do { self.install(try result.get()) } catch { self.busy = false; self.tableView.reloadData(); self.report() } }
            }
        }
    }
    override func tableView(_ tableView: UITableView, canEditRowAt indexPath: IndexPath) -> Bool { indexPath.section == 1 && !busy }
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        guard editingStyle == .delete else { return }; do { try NebulaImportedIcons.remove(packs[indexPath.row]); packs = NebulaImportedIcons.installed; tableView.reloadData() } catch { report() }
    }
    private func report() { guard presentedViewController == nil else { return }; let alert = UIAlertController(title: ru ? "Не удалось загрузить набор" : "Could not load the pack", message: ru ? "Нужен исправный .icons с metadata.json и поддерживаемыми значками SVG, PNG или WebP. Предыдущий набор сохранён." : "Use a valid .icons file with metadata.json and supported SVG, PNG or WebP icons. The previous pack is retained.", preferredStyle: .alert); alert.addAction(UIAlertAction(title: "OK", style: .default)); present(alert, animated: true) }
    @objc private func close() { dismiss(animated: true) }
}
