import UIKit

/// Shared lightweight header; native Dynamic Type and no perpetual blur/animation.
final class NebulaSettingsHero: UIView {
    private let stack = UIStackView()
    private let statusLabel = UILabel()
    private let card = UIView()
    private let wash = CAGradientLayer()
    private var active = false

    init(symbol: String, title: String, summary: String) {
        super.init(frame: .zero)
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 26
        card.layer.cornerCurve = .continuous
        card.clipsToBounds = true
        wash.startPoint = CGPoint(x: 0, y: 0)
        wash.endPoint = CGPoint(x: 1, y: 1)
        card.layer.insertSublayer(wash, at: 0)
        card.translatesAutoresizingMaskIntoConstraints = false
        addSubview(card)
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            card.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            card.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            card.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            card.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 22),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -22),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 22),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -22)
        ])
        let brand = UIStackView()
        brand.spacing = 10
        brand.alignment = .center
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = .systemBlue
        icon.contentMode = .scaleAspectFit
        icon.widthAnchor.constraint(equalToConstant: 28).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 28).isActive = true
        icon.isAccessibilityElement = false
        brand.addArrangedSubview(icon)
        brand.addArrangedSubview(label("NEBULAGRAM", style: .caption1, color: .secondaryLabel))
        stack.addArrangedSubview(brand)
        let heading = label(title, style: .title2, color: .label)
        heading.accessibilityTraits.insert(.header)
        stack.addArrangedSubview(heading)
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

    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        wash.frame = card.bounds
        wash.colors = [UIColor.systemBlue.resolvedColor(with: traitCollection).withAlphaComponent(0.16).cgColor,
                       active ? UIColor.systemGreen.resolvedColor(with: traitCollection).withAlphaComponent(0.25).cgColor : UIColor.clear.cgColor]
        CATransaction.commit()
    }

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
        cell.imageView?.image = UIImage(systemName: symbol)
        cell.imageView?.preferredSymbolConfiguration = UIImage.SymbolConfiguration(pointSize: 22, weight: .regular)
        cell.imageView?.tintColor = .systemBlue
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.textLabel?.adjustsFontForContentSizeCategory = true
        cell.textLabel?.numberOfLines = 0
        cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
        cell.detailTextLabel?.adjustsFontForContentSizeCategory = true
        cell.detailTextLabel?.numberOfLines = 0
        cell.detailTextLabel?.textColor = .secondaryLabel
    }
}
