import UIKit
import UniformTypeIdentifiers
import Display
import TelegramPresentationData
import NebulaSettingsContract

/// Interface font (Android `NebulaFontFragment`): Telegram's system font or an
/// imported TTF/OTF. The file is checked before it replaces the last working one;
/// the choice applies after a restart.
final class NebulaFontController: UITableViewController, UIDocumentPickerDelegate {
    private let ru: Bool
    private let theme: PresentationTheme
    private var installedName: String?

    init(russian: Bool, theme: PresentationTheme) {
        self.ru = russian
        self.theme = theme
        super.init(style: .insetGrouped)
        title = ru ? "Шрифт" : "Font"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private var customEnabled: Bool { UserDefaults.standard.bool(forKey: NebulaFontFile.enabledKey) && NebulaCustomFont.hasFile }

    override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 56
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
    }
    @objc private func close() { dismiss(animated: true) }

    override func numberOfSections(in tableView: UITableView) -> Int { 2 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { section == 0 ? 2 : 1 }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0 {
            return ru ? "Шрифт меняется во всём интерфейсе после перезапуска приложения. Моноширинный шрифт кода в сообщениях остаётся прежним." : "The font changes across the interface after the app restarts. The monospaced code font in messages stays as it is."
        }
        if let installedName { return (ru ? "Загружен: " : "Loaded: ") + installedName }
        return ru ? "TTF или OTF до 8 МБ. Файл проверяется до того, как заменит прежний." : "TTF or OTF up to 8 MB. The file is checked before it replaces the previous one."
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        if indexPath.section == 0 {
            cell.textLabel?.text = indexPath.row == 0 ? (ru ? "Как в Telegram" : "Telegram default") : (ru ? "Свой шрифт" : "Custom font")
            if indexPath.row == 1 && !NebulaCustomFont.hasFile {
                cell.detailTextLabel?.text = ru ? "Сначала загрузите файл шрифта" : "Import a font file first"
            }
            cell.accessoryType = (indexPath.row == 1) == customEnabled ? .checkmark : .none
        } else {
            cell.textLabel?.text = ru ? "Загрузить TTF или OTF" : "Import TTF or OTF"
            cell.textLabel?.textColor = theme.list.itemAccentColor
        }
        NebulaSettingsStyle.finish(cell, theme: theme)
        if indexPath.section == 1 { cell.textLabel?.textColor = theme.list.itemAccentColor }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        if indexPath.section == 1 {
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.font], asCopy: true)
            picker.delegate = self
            picker.allowsMultipleSelection = false
            present(picker, animated: true)
            return
        }
        if indexPath.row == 1 && !NebulaCustomFont.hasFile { return }
        UserDefaults.standard.set(indexPath.row == 1, forKey: NebulaFontFile.enabledKey)
        tableView.reloadData()
        showRestartNote()
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let size = (try url.resourceValues(forKeys: [.fileSizeKey])).fileSize ?? 0
            guard size <= NebulaFontFile.limit else { throw NebulaFontFile.Failure.size }
            let data = try Data(contentsOf: url)
            try NebulaFontFile.validate(data)
            installedName = try NebulaCustomFont.install(data)
            UserDefaults.standard.set(true, forKey: NebulaFontFile.enabledKey)
            tableView.reloadData()
            showRestartNote()
        } catch {
            let alert = UIAlertController(title: ru ? "Не удалось загрузить шрифт" : "Could not import the font", message: ru ? "Нужен файл TTF или OTF до 8 МБ. Прежний шрифт остался." : "A TTF or OTF file up to 8 MB is required. The previous font is kept.", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
        }
    }

    private func showRestartNote() {
        let alert = UIAlertController(title: ru ? "Шрифт выбран" : "Font selected", message: ru ? "Перезапустите приложение, чтобы применить шрифт." : "Restart the app to apply the font.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
