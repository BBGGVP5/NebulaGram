import UIKit

/// A static, self-sizing status surface. No blur snapshots or display-link work.
final class NebulaLinkOverviewView: UIView {
    private let card = UIView()
    private let gradient = CAGradientLayer()
    private let status = UILabel()
    private let detail = UILabel()
    private let action = UIButton(type: .system)
    private let symbol = UIImageView(image: UIImage(systemName: "network"))
    private var connected = false
    var onAction: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        card.layer.cornerRadius = 26
        card.layer.cornerCurve = .continuous
        card.clipsToBounds = true
        card.layer.insertSublayer(gradient, at: 0)
        card.translatesAutoresizingMaskIntoConstraints = false
        addSubview(card)
        status.font = .preferredFont(forTextStyle: .title2)
        detail.font = .preferredFont(forTextStyle: .subheadline)
        detail.textColor = .secondaryLabel
        for label in [status, detail] {
            label.numberOfLines = 0
            label.adjustsFontForContentSizeCategory = true
        }
        status.accessibilityTraits = .header
        symbol.contentMode = .scaleAspectFit
        symbol.isAccessibilityElement = false
        let labels = UIStackView(arrangedSubviews: [status, detail])
        labels.axis = .vertical
        labels.spacing = 6
        let heading = UIStackView(arrangedSubviews: [symbol, labels])
        heading.spacing = 16
        heading.alignment = .center
        symbol.widthAnchor.constraint(equalToConstant: 32).isActive = true
        symbol.heightAnchor.constraint(equalToConstant: 32).isActive = true
        action.titleLabel?.font = .preferredFont(forTextStyle: .headline)
        action.titleLabel?.adjustsFontForContentSizeCategory = true
        action.titleLabel?.numberOfLines = 0
        action.titleLabel?.textAlignment = .center
        action.contentEdgeInsets = UIEdgeInsets(top: 14, left: 18, bottom: 14, right: 18)
        action.layer.cornerRadius = 22
        action.clipsToBounds = true
        action.accessibilityIdentifier = "NebulaLink.ConnectionAction"
        action.addTarget(self, action: #selector(pressed), for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [heading, action])
        stack.axis = .vertical
        stack.spacing = 20
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            card.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            card.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            card.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 22),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -20),
            action.heightAnchor.constraint(greaterThanOrEqualToConstant: 50)
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(state: String, busy: Bool, hasSelection: Bool, russian: Bool) {
        connected = state == "connected"
        let connecting = state == "connecting"
        status.text = connected ? (russian ? "Подключено" : "Connected")
            : connecting ? (russian ? "Подключение…" : "Connecting…")
            : (russian ? "Не подключено" : "Not connected")
        detail.text = connected
            ? (russian ? "Прокси включён для Telegram. Это не системный VPN." : "Proxy enabled for Telegram. Not a system VPN.")
            : (russian ? "Добавьте подписку и выберите сервер ниже." : "Add a subscription and choose a server below.")
        let title = busy ? (russian ? "Подождите…" : "Please wait…")
            : connected || connecting ? (russian ? "Отключить" : "Disconnect")
            : (russian ? "Подключить" : "Connect")
        action.setTitle(title, for: .normal)
        action.isEnabled = !busy && (connected || connecting || hasSelection)
        action.alpha = action.isEnabled ? 1.0 : 0.5
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let accent = connected ? UIColor.systemGreen : tintColor ?? UIColor.systemBlue
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        gradient.frame = card.bounds
        gradient.startPoint = CGPoint(x: 0, y: 0)
        gradient.endPoint = CGPoint(x: 1, y: 1)
        gradient.colors = [UIColor.systemBlue.withAlphaComponent(0.16).resolvedColor(with: traitCollection).cgColor,
                           accent.withAlphaComponent(connected ? 0.22 : 0.04).resolvedColor(with: traitCollection).cgColor]
        CATransaction.commit()
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.borderWidth = 1 / max(traitCollection.displayScale, 1)
        card.layer.borderColor = accent.withAlphaComponent(0.25).resolvedColor(with: traitCollection).cgColor
        status.textColor = .label
        symbol.tintColor = accent
        action.backgroundColor = .label
        action.setTitleColor(.systemBackground, for: .normal)
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        setNeedsLayout()
    }
    @objc private func pressed() { onAction?() }
}
