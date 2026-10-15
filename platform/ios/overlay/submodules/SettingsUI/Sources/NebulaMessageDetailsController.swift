import UIKit
import Postbox
import TelegramCore
import TelegramPresentationData

final class NebulaMessageDetailsController: UITableViewController {
    struct Field { let title: String; let value: String }
    private let fields: [Field]
    private let theme: PresentationTheme
    private let russian: Bool
    init(message: Message, russian: Bool, theme: PresentationTheme) {
        self.russian = russian
        self.theme = theme
        var fields: [Field] = [
            Field(title: russian ? "ID сообщения" : "Message ID", value: String(message.id.id)),
            Field(title: russian ? "ID диалога" : "Dialog ID", value: String(message.id.peerId.toInt64()))
        ]
        if let author = message.author {
            fields.append(Field(title: russian ? "ID отправителя" : "Sender ID", value: String(author.id.toInt64())))
        }
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss z"
        func date(_ title: String, _ timestamp: Int32) {
            if timestamp > 0 { fields.append(Field(title: title, value: formatter.string(from: Date(timeIntervalSince1970: Double(timestamp))))) }
        }
        date(russian ? "Отправлено" : "Sent", message.timestamp)
        for attribute in message.attributes {
            if let edited = attribute as? EditedMessageAttribute { date(russian ? "Изменено" : "Edited", edited.date) }
        }
        if let forward = message.forwardInfo { date(russian ? "Оригинал пересылки" : "Forwarded original", forward.date) }
        for case let file as TelegramMediaFile in message.media {
            if let name = file.fileName, !name.isEmpty { fields.append(Field(title: russian ? "Имя файла" : "File name", value: name)) }
            if !file.mimeType.isEmpty { fields.append(Field(title: "MIME", value: file.mimeType)) }
            if let size = file.size, size > 0, size != .max {
                fields.append(Field(title: russian ? "Размер файла" : "File size", value: ByteCountFormatter.string(fromByteCount: size, countStyle: .file) + " · \(size) " + (russian ? "байт" : "bytes")))
            }
        }
        self.fields = fields
        super.init(style: .insetGrouped)
        title = russian ? "Подробности сообщения" : "Message details"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 70
    }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { fields.count }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        russian ? "Нажмите строку, чтобы скопировать значение. Показаны данные выбранного сообщения с этого устройства." : "Tap a row to copy its value. These are the selected message's data already present on this device."
    }
    override func tableView(_ tableView: UITableView, cellForRowAt path: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .subtitle, reuseIdentifier: nil)
        cell.textLabel?.text = fields[path.row].title
        cell.detailTextLabel?.text = fields[path.row].value
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.numberOfLines = 0
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.backgroundColor = theme.list.itemBlocksBackgroundColor
        cell.textLabel?.textColor = theme.list.itemPrimaryTextColor
        cell.detailTextLabel?.textColor = theme.list.itemSecondaryTextColor
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt path: IndexPath) {
        tableView.deselectRow(at: path, animated: true)
        UIPasteboard.general.string = fields[path.row].value
    }
}
