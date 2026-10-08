import UIKit
import AccountContext
import TelegramPresentationData

/// The same compact introduction geometry is used by list pages and NebulaLink.
final class NebulaSettingsHero: UIView {
    private let stack = UIStackView()
    private let statusLabel = UILabel()
    private var active = false
    private let titleLabel = UILabel()
    private let summaryLabel = UILabel()
    private var animatedEmoji: NebulaAnimatedSettingsEmoji?

    init(symbol: String, title: String, summary: String, context: AccountContext? = nil, theme: PresentationTheme? = nil) {
        super.init(frame: .zero)
        stack.axis = .vertical
        stack.spacing = 8
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -24)
        ])
        let emoji = Self.emoji(for: symbol)
        let artwork: UIView
        if let context {
            let animation = NebulaAnimatedSettingsEmoji(context: context, emoji: emoji)
            animatedEmoji = animation; artwork = animation
        } else {
            let image = UILabel(); image.text = emoji; image.textAlignment = .center
            image.font = .systemFont(ofSize: 72); artwork = image
        }
        artwork.isAccessibilityElement = false
        stack.addArrangedSubview(artwork)
        artwork.widthAnchor.constraint(equalToConstant: 88).isActive = true
        artwork.heightAnchor.constraint(equalToConstant: 88).isActive = true
        stack.setCustomSpacing(12, after: artwork)
        titleLabel.text = title
        titleLabel.font = UIFontMetrics(forTextStyle: .title1).scaledFont(for: .systemFont(ofSize: 28, weight: .bold))
        titleLabel.adjustsFontForContentSizeCategory = true; titleLabel.numberOfLines = 0
        titleLabel.textAlignment = .center; titleLabel.accessibilityTraits.insert(.header)
        titleLabel.textColor = theme?.list.itemPrimaryTextColor ?? .label
        stack.addArrangedSubview(titleLabel)
        summaryLabel.text = summary.trimmingCharacters(in: CharacterSet(charactersIn: ". "))
        summaryLabel.font = .preferredFont(forTextStyle: .subheadline)
        summaryLabel.adjustsFontForContentSizeCategory = true; summaryLabel.numberOfLines = 0
        summaryLabel.textAlignment = .center; summaryLabel.textColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel
        stack.addArrangedSubview(summaryLabel)
        summaryLabel.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
        summaryLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: summaryLabel.font.lineHeight * 2).isActive = true
        statusLabel.font = .preferredFont(forTextStyle: .footnote)
        statusLabel.adjustsFontForContentSizeCategory = true
        statusLabel.numberOfLines = 0
        statusLabel.textAlignment = .center
        statusLabel.textColor = .systemBlue
        stack.addArrangedSubview(statusLabel)
        statusLabel.isHidden = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func setPageVisible(_ visible: Bool) { animatedEmoji?.setPageVisible(visible) }
    static func emoji(for symbol: String) -> String {
        switch symbol {
        case "gearshape", "tools": return "🧰"
        case "paintpalette": return "🎨"
        case "link": return "🔗"
        case "sparkles": return "🤖"
        case "hand.raised", "lock.shield": return "🔐"
        case "checkmark.circle": return "✅"
        case "folder": return "🗂️"
        case "person.crop.circle": return "👤"
        case "bubble.left.and.bubble.right": return "💬"
        case "info.circle": return "🚀"
        case "rectangle.bottomthird.inset.filled": return "🧭"
        default: return symbol
        }
    }

    func setStatus(_ text: String, active: Bool = false) {
        let value = text
        guard self.active != active || statusLabel.text != value else { return }
        self.active = active
        statusLabel.text = value
        statusLabel.accessibilityLabel = text
        statusLabel.isHidden = text.isEmpty
        statusLabel.textColor = active ? .systemGreen : .secondaryLabel
        setNeedsLayout()
    }

    func fit(in table: UITableView) {
        let width = table.bounds.width
        guard width > 0 else { return }
        let height = ceil(systemLayoutSizeFitting(CGSize(width: width, height: 0),
            withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height)
        if table.tableHeaderView !== self || abs(frame.width - width) > 0.5 || abs(frame.height - height) > 0.5 {
            frame = CGRect(x: 0, y: 0, width: width, height: height)
            table.tableHeaderView = self
        }
    }

    private func label(_ text: String, style: UIFont.TextStyle, color: UIColor) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: style)
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 0
        label.textColor = color
        return label
    }

    static func style(_ cell: UITableViewCell, symbol: String) {
        cell.imageView?.image = NebulaSettingsStyle.icon(symbol: symbol)
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.numberOfLines = 0
        cell.detailTextLabel?.textColor = .secondaryLabel
    }
}
