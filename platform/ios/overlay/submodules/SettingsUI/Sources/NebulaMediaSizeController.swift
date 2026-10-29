import UIKit
import Display
import NebulaSettingsContract
import TelegramPresentationData

final class NebulaMediaSizeController: UITableViewController {
    private let ru: Bool
    private let theme: PresentationTheme
    private let store = NebulaSettingsStore.shared
    private let keys = ["sticker_percent", "emoji_percent", "gif_percent"]
    private var previews: [NebulaMediaSizePreview] = []
    private var observation: SettingsObservation?

    init(russian: Bool, theme: PresentationTheme) {
        self.ru = russian
        self.theme = theme
        super.init(style: .insetGrouped)
        title = russian ? "Размеры медиа" : "Media sizes"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        NebulaSettingsStyle.apply(theme: theme, to: self)
        navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(close))
        tableView.rowHeight = 64
        previews = (0..<3).map { NebulaMediaSizePreview(kind: $0, theme: theme) }
        observation = store.observe { [weak self] in self?.refresh() }
        refresh()
    }
    private func value(_ index: Int) -> Int { [store.stickerPercent, store.emojiPercent, store.gifPercent][index] }
    private var labels: [String] { ru ? ["Стикеры", "Эмодзи", "GIF"] : ["Stickers", "Emoji", "GIF"] }
    private func refresh() {
        for index in 0..<previews.count {
            previews[index].percent = value(index)
            if let slider = tableView.cellForRow(at: IndexPath(row: 0, section: index))?.contentView.viewWithTag(100 + index) as? UISlider {
                if !slider.isTracking { slider.value = Float(value(index)) }
                slider.accessibilityValue = "\(value(index))%"
            }
        }
    }
    @objc private func close() { dismiss(animated: true) }
    override func numberOfSections(in tableView: UITableView) -> Int { 3 }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { 1 }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { labels[section] }
    override func tableView(_ tableView: UITableView, heightForFooterInSection section: Int) -> CGFloat { section == 2 ? 226 : 174 }
    override func tableView(_ tableView: UITableView, viewForFooterInSection section: Int) -> UIView? {
        let view = UIView()
        let preview = previews[section]
        preview.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(preview)
        NSLayoutConstraint.activate([
            preview.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            preview.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            preview.topAnchor.constraint(equalTo: view.topAnchor, constant: 8),
            preview.heightAnchor.constraint(equalToConstant: 158)
        ])
        if section == 2 {
            let label = UILabel()
            label.text = ru ? "Фото и видео не меняются. Размер GIF ограничен шириной чата и местом для времени сообщения." : "Photos and videos stay unchanged. GIF sizes respect the chat width and message timestamp."
            label.font = .preferredFont(forTextStyle: .footnote)
            label.adjustsFontForContentSizeCategory = true
            label.textColor = theme.list.itemSecondaryTextColor
            label.numberOfLines = 0
            label.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(label)
            NSLayoutConstraint.activate([
                label.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
                label.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
                label.topAnchor.constraint(equalTo: preview.bottomAnchor, constant: 8)
            ])
        }
        return view
    }
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        NebulaSettingsStyle.finish(cell, theme: theme)
        cell.selectionStyle = .none
        let slider = UISlider()
        slider.tag = 100 + indexPath.section
        slider.minimumValue = 50; slider.maximumValue = 150
        slider.value = Float(value(indexPath.section))
        slider.isEnabled = !store.hasLoadError
        slider.accessibilityLabel = labels[indexPath.section]
        slider.accessibilityValue = "\(value(indexPath.section))%"
        slider.addTarget(self, action: #selector(changed(_:)), for: .valueChanged)
        slider.translatesAutoresizingMaskIntoConstraints = false
        cell.contentView.addSubview(slider)
        NSLayoutConstraint.activate([
            slider.leadingAnchor.constraint(equalTo: cell.contentView.leadingAnchor, constant: 20),
            slider.trailingAnchor.constraint(equalTo: cell.contentView.trailingAnchor, constant: -20),
            slider.centerYAnchor.constraint(equalTo: cell.contentView.centerYAnchor)
        ])
        return cell
    }
    @objc private func changed(_ slider: UISlider) {
        let index = slider.tag - 100
        let percent = Int(slider.value.rounded())
        guard percent != value(index) else { return }
        do { try store.set(.integer(percent), for: keys[index]) }
        catch { slider.value = Float(value(index)) }
        refresh()
    }
}

private final class NebulaMediaSizePreview: UIView {
    let kind: Int
    let theme: PresentationTheme
    var percent = 100 { didSet { setNeedsDisplay(); accessibilityValue = "\(percent)%" } }
    init(kind: Int, theme: PresentationTheme) {
        self.kind = kind; self.theme = theme
        super.init(frame: .zero)
        isOpaque = false
        isAccessibilityElement = true
        accessibilityLabel = ["Sticker", "Emoji", "GIF"][kind]
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func draw(_ rect: CGRect) {
        let factor = CGFloat(NebulaMediaGeometry.boundedScale(percent: percent, naturalWidth: 84, availableWidth: Double(bounds.width - 24)))
        let size = CGSize(width: 84 * factor, height: 68 * factor)
        let frame = CGRect(x: (bounds.width - size.width) / 2, y: (bounds.height - size.height) / 2 - 10, width: size.width, height: size.height)
        theme.list.itemSecondaryTextColor.withAlphaComponent(0.3).setStroke()
        let reference = UIBezierPath(roundedRect: CGRect(x: (bounds.width - 84) / 2, y: (bounds.height - 68) / 2 - 10, width: 84, height: 68), cornerRadius: 14)
        reference.setLineDash([4, 4], count: 2, phase: 0)
        reference.stroke()
        if kind == 1 {
            let text = "🙂" as NSString
            let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 52 * factor)]
            let textSize = text.size(withAttributes: attributes)
            text.draw(at: CGPoint(x: bounds.midX - textSize.width / 2, y: frame.midY - textSize.height / 2), withAttributes: attributes)
        } else {
            theme.list.itemAccentColor.withAlphaComponent(0.18).setFill()
            UIBezierPath(roundedRect: frame, cornerRadius: 14 * factor).fill()
            let image = UIImage(systemName: kind == 0 ? "sparkles" : "play.rectangle.fill")?.withTintColor(theme.list.itemAccentColor, renderingMode: .alwaysOriginal)
            image?.draw(in: CGRect(x: frame.midX - 18 * factor, y: frame.midY - 18 * factor, width: 36 * factor, height: 36 * factor))
        }
        let text = "\(percent)%" as NSString
        let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.monospacedDigitSystemFont(ofSize: 13, weight: .medium), .foregroundColor: theme.list.itemSecondaryTextColor]
        text.draw(at: CGPoint(x: bounds.midX - text.size(withAttributes: attributes).width / 2, y: bounds.height - 20), withAttributes: attributes)
    }
}
