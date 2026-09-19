import UIKit

/// Shared lightweight header; native Dynamic Type and no perpetual blur/animation.
final class NebulaSettingsHero: UIView {
    private let stack = UIStackView()
    private let statusLabel = UILabel()
    private let card = UIView()
    private var active = false

    init(symbol: String, title: String, summary: String) {
        super.init(frame: .zero)
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 20
        card.layer.cornerCurve = .continuous
        card.clipsToBounds = true
        card.translatesAutoresizingMaskIntoConstraints = false
        addSubview(card)
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            card.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            card.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            card.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 20),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -20)
        ])
        let brand = UIStackView()
        brand.spacing = 12
        brand.alignment = .center
        let tile = UIView()
        tile.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.12)
        tile.layer.cornerRadius = 14
        tile.widthAnchor.constraint(equalToConstant: 48).isActive = true
        tile.heightAnchor.constraint(equalToConstant: 48).isActive = true
        let icon = UIImageView(image: UIImage(systemName: symbol,
            withConfiguration: UIImage.SymbolConfiguration(pointSize: 25, weight: .regular)))
        icon.tintColor = .systemBlue
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        tile.addSubview(icon)
        NSLayoutConstraint.activate([
            icon.centerXAnchor.constraint(equalTo: tile.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: tile.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 28),
            icon.heightAnchor.constraint(equalToConstant: 28)
        ])
        icon.isAccessibilityElement = false
        brand.addArrangedSubview(tile)
        let heading = label(title, style: .title3, color: .label)
        heading.font = UIFontMetrics(forTextStyle: .title3).scaledFont(for: .systemFont(ofSize: 20, weight: .semibold))
        heading.accessibilityTraits.insert(.header)
        brand.addArrangedSubview(heading)
        stack.addArrangedSubview(brand)
        stack.addArrangedSubview(label(summary, style: .subheadline, color: .secondaryLabel))
        statusLabel.font = .preferredFont(forTextStyle: .footnote)
        statusLabel.adjustsFontForContentSizeCategory = true
        statusLabel.numberOfLines = 0
        statusLabel.textColor = .systemBlue
        statusLabel.layer.cornerRadius = 10
        statusLabel.clipsToBounds = true
        statusLabel.textAlignment = .center
        stack.addArrangedSubview(statusLabel)
        statusLabel.isHidden = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setStatus(_ text: String, active: Bool = false) {
        let value = (active ? "●  " : "○  ") + text
        guard self.active != active || statusLabel.text != value else { return }
        self.active = active
        statusLabel.text = value
        statusLabel.accessibilityLabel = text
        statusLabel.isHidden = text.isEmpty
        statusLabel.textColor = active ? .systemGreen : .secondaryLabel
        statusLabel.backgroundColor = (active ? UIColor.systemGreen : UIColor.secondaryLabel).withAlphaComponent(0.10)
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
