import UIKit
import TelegramPresentationData
import NebulaSettingsContract

/// Compact, theme-owned presentation for AI and audio tools.
enum NebulaAiPanelUi {
    static func button(_ button: UIButton, theme: PresentationTheme?, primary: Bool = false, constrainHeight: Bool = true) {
        let accent = theme?.list.itemAccentColor ?? .systemTeal
        let foreground = primary ? (theme?.list.itemCheckColors.foregroundColor ?? .white) : (theme?.list.itemPrimaryTextColor ?? .label)
        let surface = primary ? accent : (theme?.list.itemBlocksBackgroundColor ?? .secondarySystemBackground)
        if #available(iOS 15.0, *) {
            var configuration = UIButton.Configuration.filled()
            configuration.baseBackgroundColor = surface
            configuration.baseForegroundColor = foreground
            configuration.cornerStyle = .large
            configuration.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 14, bottom: 10, trailing: 14)
            configuration.imagePadding = 8
            button.configuration = configuration
        } else {
            button.backgroundColor = surface; button.tintColor = foreground
            button.setTitleColor(foreground, for: .normal); button.layer.cornerRadius = 16
        }
        button.layer.borderWidth = 0
        button.titleLabel?.font = .preferredFont(forTextStyle: .subheadline)
        button.titleLabel?.adjustsFontForContentSizeCategory = true
        button.titleLabel?.numberOfLines = 2
        if constrainHeight { button.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true }
    }
    static func editor(_ editor: UITextView, theme: PresentationTheme?) {
        editor.font = .preferredFont(forTextStyle: .body); editor.adjustsFontForContentSizeCategory = true
        editor.textColor = theme?.list.itemPrimaryTextColor ?? .label
        editor.backgroundColor = theme?.list.itemBlocksBackgroundColor ?? .secondarySystemBackground
        editor.textContainerInset = UIEdgeInsets(top: 12, left: 10, bottom: 12, right: 10)
        editor.layer.cornerRadius = 18; editor.layer.borderWidth = 0
    }
}

final class NebulaAiPanelHeader: UIView {
    enum Kind { case voice, speech, chat }
    private let glyph: Glyph
    private let subtitle = UILabel()
    private var action: (() -> Void)?
    var detail: String { get { subtitle.text ?? "" } set { subtitle.text = newValue } }
    init(kind: Kind, title: String, detail: String, theme: PresentationTheme?) {
        glyph = Glyph(kind: kind, color: theme?.list.itemAccentColor ?? .systemTeal)
        super.init(frame: .zero)
        let label = UILabel(); label.text = title; label.font = .preferredFont(forTextStyle: .headline)
        label.adjustsFontForContentSizeCategory = true; label.textColor = theme?.list.itemPrimaryTextColor ?? .label
        label.numberOfLines = 0; label.accessibilityTraits.insert(.header)
        subtitle.text = detail; subtitle.font = .preferredFont(forTextStyle: .caption1)
        subtitle.adjustsFontForContentSizeCategory = true; subtitle.numberOfLines = 0
        subtitle.textColor = theme?.list.itemSecondaryTextColor ?? .secondaryLabel
        let labels = UIStackView(arrangedSubviews: [label, subtitle]); labels.axis = .vertical; labels.spacing = 4
        let row = UIStackView(arrangedSubviews: [glyph, labels]); row.axis = .horizontal; row.spacing = 12; row.alignment = .center
        row.translatesAutoresizingMaskIntoConstraints = false; addSubview(row)
        NSLayoutConstraint.activate([glyph.widthAnchor.constraint(equalToConstant: 44), glyph.heightAnchor.constraint(equalToConstant: 44),
            row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16), row.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            row.topAnchor.constraint(equalTo: topAnchor, constant: 16), row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16)])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func setBusy(_ busy: Bool) { glyph.busy = busy }
    func setAction(_ action: @escaping () -> Void, label: String) {
        self.action = action; isAccessibilityElement = true; accessibilityLabel = label; accessibilityTraits = [.button, .header]
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(activate)))
    }
    @objc private func activate() { action?() }
    override func accessibilityActivate() -> Bool { guard let action else { return false }; action(); return true }
    func setPageVisible(_ visible: Bool) { glyph.pageVisible = visible }
    func fit(in table: UITableView) {
        let width = table.bounds.width
        guard width > 0 else { return }
        let height = systemLayoutSizeFitting(CGSize(width: width, height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height
        if table.tableHeaderView !== self || abs(frame.width - width) > 0.5 || abs(frame.height - height) > 0.5 {
            frame = CGRect(x: 0, y: 0, width: width, height: height); table.tableHeaderView = self
        }
    }
    private final class Glyph: UIView {
        let kind: Kind, color: UIColor
        var busy = false { didSet { updateMotion() } }
        var pageVisible = true { didSet { updateMotion() } }
        init(kind: Kind, color: UIColor) {
            self.kind = kind; self.color = color; super.init(frame: .zero)
            backgroundColor = .clear; isAccessibilityElement = false
            NotificationCenter.default.addObserver(self, selector: #selector(updateMotion), name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(updateMotion), name: UIApplication.didBecomeActiveNotification, object: nil)
            NotificationCenter.default.addObserver(self, selector: #selector(updateMotion), name: UIApplication.willResignActiveNotification, object: nil)
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override func didMoveToWindow() { super.didMoveToWindow(); updateMotion() }
        @objc private func updateMotion() {
            let animate = busy && pageVisible && window != nil && UIApplication.shared.applicationState == .active && !UIAccessibility.isReduceMotionEnabled && NebulaSettingsStore.shared.liquidAnimations
            if animate && layer.animation(forKey: "nebulaWork") == nil {
                let motion = CAKeyframeAnimation(keyPath: "transform.translation.y")
                motion.values = [0, -2, 0]; motion.duration = 1.2; motion.repeatCount = .infinity
                motion.timingFunctions = [CAMediaTimingFunction(name: .easeInEaseOut), CAMediaTimingFunction(name: .easeInEaseOut)]
                layer.add(motion, forKey: "nebulaWork")
            } else if !animate { layer.removeAnimation(forKey: "nebulaWork") }
        }
        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            context.saveGState(); defer { context.restoreGState() }
            context.scaleBy(x: rect.width / 44, y: rect.height / 44)
            color.withAlphaComponent(0.12).setFill(); UIBezierPath(roundedRect: CGRect(x: 1, y: 1, width: 42, height: 42), cornerRadius: 15).fill()
            color.setStroke(); let path = UIBezierPath(); path.lineWidth = 1.8; path.lineCapStyle = .round; path.lineJoinStyle = .round
            switch kind {
            case .voice:
                path.append(UIBezierPath(roundedRect: CGRect(x: 15, y: 9, width: 10, height: 18), cornerRadius: 5))
                path.move(to: CGPoint(x: 11, y: 21)); path.addCurve(to: CGPoint(x: 29, y: 21), controlPoint1: CGPoint(x: 11, y: 34), controlPoint2: CGPoint(x: 29, y: 34))
                path.move(to: CGPoint(x: 20, y: 30)); path.addLine(to: CGPoint(x: 20, y: 35)); path.move(to: CGPoint(x: 15, y: 35)); path.addLine(to: CGPoint(x: 25, y: 35))
            case .speech:
                path.move(to: CGPoint(x: 10, y: 18)); path.addLine(to: CGPoint(x: 16, y: 18)); path.addLine(to: CGPoint(x: 23, y: 12)); path.addLine(to: CGPoint(x: 23, y: 32)); path.addLine(to: CGPoint(x: 16, y: 26)); path.addLine(to: CGPoint(x: 10, y: 26)); path.close()
                path.move(to: CGPoint(x: 28, y: 17)); path.addCurve(to: CGPoint(x: 28, y: 27), controlPoint1: CGPoint(x: 32, y: 19), controlPoint2: CGPoint(x: 32, y: 25))
            case .chat:
                path.append(UIBezierPath(roundedRect: CGRect(x: 9, y: 11, width: 25, height: 20), cornerRadius: 7))
                path.move(to: CGPoint(x: 16, y: 31)); path.addLine(to: CGPoint(x: 13, y: 36)); path.addLine(to: CGPoint(x: 24, y: 31))
                path.move(to: CGPoint(x: 15, y: 19)); path.addLine(to: CGPoint(x: 26, y: 19)); path.move(to: CGPoint(x: 15, y: 24)); path.addLine(to: CGPoint(x: 22, y: 24))
            }
            path.stroke()
            color.setFill(); let star = UIBezierPath(); star.move(to: CGPoint(x: 34, y: 5)); star.addLine(to: CGPoint(x: 36, y: 10)); star.addLine(to: CGPoint(x: 41, y: 12)); star.addLine(to: CGPoint(x: 36, y: 14)); star.addLine(to: CGPoint(x: 34, y: 19)); star.addLine(to: CGPoint(x: 32, y: 14)); star.addLine(to: CGPoint(x: 27, y: 12)); star.addLine(to: CGPoint(x: 32, y: 10)); star.close(); star.fill()
        }
        deinit { NotificationCenter.default.removeObserver(self) }
    }
}
