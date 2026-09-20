import UIKit

/// Plain context/status; the navigation bar already supplies the screen title.
final class NebulaSettingsHero: UIView {
    private let stack = UIStackView()
    private let statusLabel = UILabel()
    private var active = false

    init(symbol: String, title: String, summary: String) {
        super.init(frame: .zero)
        stack.axis = .vertical
        stack.spacing = 6
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8)
        ])
        stack.addArrangedSubview(label(summary, style: .subheadline, color: .secondaryLabel))
        statusLabel.font = .preferredFont(forTextStyle: .footnote)
        statusLabel.adjustsFontForContentSizeCategory = true
        statusLabel.numberOfLines = 0
        statusLabel.textColor = .systemBlue
        stack.addArrangedSubview(statusLabel)
        statusLabel.isHidden = true
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

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
