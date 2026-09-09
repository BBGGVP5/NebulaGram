import UIKit

// Presentation only: these views never read a phone number, code or password.
struct NebulaAuthCopy {
    let russian: Bool
    init(_ language: String) { russian = language.lowercased().hasPrefix("ru") }
    var welcome: String { russian ? "Ближе друг к другу\nс NebulaGram" : "A little closer\nwith NebulaGram" }
    var subtitle: String { russian ? "Ваши разговоры. Ваш стиль. Ваш Telegram." : "Your conversations. Your style. Your Telegram." }
    var start: String { russian ? "Начать общение" : "Start messaging" }
    var phone: String { russian ? "Ваш номер телефона" : "Your phone number" }
    var password: String { russian ? "Ваш пароль" : "Your password" }
    var linkTitle: String { russian ? "NebulaLink внутри" : "NebulaLink inside" }
    var linkSubtitle: String { russian ? "Подключите подписку или ключ сервера перед входом. Можно продолжить без прокси." : "Connect a subscription or server key before signing in. You can also continue without a proxy." }
}

final class NebulaAuthArtView: UIView {
    enum Kind { case welcome, phone, code, password, link }
    let kind: Kind
    private let accent: UIColor
    private var timer: Timer?
    private var phase = 0
    private var active = true
    private var digits: [UILabel] = []
    init(kind: Kind, accent: UIColor) {
        self.kind = kind
        self.accent = accent
        super.init(frame: .zero)
        isOpaque = false
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        contentMode = .redraw
        if kind == .phone || kind == .code {
            for _ in 0..<5 {
                let label = UILabel()
                label.textAlignment = .center
                label.textColor = accent
                label.layer.borderWidth = 2
                label.layer.borderColor = accent.withAlphaComponent(0.45).cgColor
                label.clipsToBounds = true
                addSubview(label); digits.append(label)
            }
            updateDigits(animated: false)
        }
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(accessibilityChanged), name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
        center.addObserver(self, selector: #selector(background), name: UIApplication.willResignActiveNotification, object: nil)
        center.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { timer?.invalidate(); NotificationCenter.default.removeObserver(self) }
    override func didMoveToWindow() { super.didMoveToWindow(); updateTimer() }
    override var isHidden: Bool { didSet { updateTimer() } }
    @objc private func accessibilityChanged() { updateTimer() }
    @objc private func background() { active = false; updateTimer() }
    @objc private func foreground() { active = true; updateTimer() }
    private func updateTimer() {
        timer?.invalidate(); timer = nil
        guard !digits.isEmpty, window != nil, !isHidden, active, !UIAccessibility.isReduceMotionEnabled else {
            digits.forEach { $0.layer.removeAllAnimations() }
            return
        }
        let timer = Timer(timeInterval: 1.4, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            self.phase = (self.phase + 1) % 3
            self.updateDigits(animated: true)
        }
        self.timer = timer
        RunLoop.main.add(timer, forMode: .common)
    }
    private func updateDigits(animated: Bool) {
        // Decorative examples only; never mirror a real phone number or login code.
        let examples = kind == .phone ? ["+6217", "+7786", "+2049"] : ["•••••", "•1•••", "••7••"]
        for (index, character) in examples[phase].enumerated() {
            let label = digits[index]
            label.text = String(character)
            label.backgroundColor = index == 0 ? accent.withAlphaComponent(0.15) : .clear
            if animated {
                let spring = CASpringAnimation(keyPath: "transform.translation.y")
                spring.fromValue = -8; spring.toValue = 0; spring.damping = 18; spring.stiffness = 170
                spring.duration = 0.5; spring.beginTime = CACurrentMediaTime() + Double(index) * 0.04
                label.layer.add(spring, forKey: "nebula.digit")
            }
        }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard !digits.isEmpty else { return }
        let width = min(250, bounds.width)
        let gap: CGFloat = 4
        let cell = max(1, (width - gap * 4) / 5)
        let height = min(90, bounds.height)
        for (index, label) in digits.enumerated() {
            label.frame = CGRect(x: (bounds.width - width) / 2 + CGFloat(index) * (cell + gap), y: (bounds.height - height) / 2, width: cell, height: height)
            label.layer.cornerRadius = cell / 2
            label.font = .monospacedDigitSystemFont(ofSize: min(46, cell * 0.95), weight: .medium)
        }
    }

    override func draw(_ rect: CGRect) {
        if !digits.isEmpty { return }
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let side = min(bounds.width, bounds.height)
        context.translateBy(x: (bounds.width - side) / 2, y: (bounds.height - side) / 2)
        context.scaleBy(x: side / 200, y: side / 200)
        let tile = UIBezierPath(roundedRect: CGRect(x: 10, y: 10, width: 180, height: 180), cornerRadius: 54)
        accent.withAlphaComponent(0.10).setFill()
        tile.fill()
        // Identical 200-unit dart/trails to Android's NebulaMark, not a stock paper plane.
        let dart = UIBezierPath()
        dart.move(to: CGPoint(x: 148, y: 52))
        for point in [CGPoint(x: 96, y: 138), CGPoint(x: 88, y: 104), CGPoint(x: 54, y: 96)] { dart.addLine(to: point) }
        dart.close()
        dart.lineWidth = 13
        dart.lineJoinStyle = .round
        accent.setFill(); accent.setStroke(); dart.fill(); dart.stroke()
        for (i, curve) in [[44.0,148,58,142,70,139,82,139], [40.0,124,49,120,57,118,65,118]].enumerated() {
            let path = UIBezierPath()
            path.move(to: CGPoint(x: curve[0], y: curve[1]))
            path.addCurve(to: CGPoint(x: curve[6], y: curve[7]), controlPoint1: CGPoint(x: curve[2], y: curve[3]), controlPoint2: CGPoint(x: curve[4], y: curve[5]))
            path.lineWidth = i == 0 ? 9 : 7
            path.lineCapStyle = .round
            accent.withAlphaComponent(i == 0 ? 0.85 : 0.55).setStroke(); path.stroke()
        }
        let symbol: String?
        switch kind {
        case .welcome: symbol = nil
        case .phone: symbol = "phone.fill"
        case .code: symbol = "number"
        case .password: symbol = "lock.fill"
        case .link: symbol = "link"
        }
        if let symbol = symbol {
            let badge = CGRect(x: 136, y: 132, width: 52, height: 52)
            UIColor.secondarySystemBackground.setFill()
            UIBezierPath(ovalIn: badge).fill()
            UIImage(systemName: symbol)?.withTintColor(accent, renderingMode: .alwaysOriginal).draw(in: badge.insetBy(dx: 14, dy: 14))
        }
    }
}

final class NebulaAuthBackdropView: UIView {
    private let glow = CAGradientLayer()
    private let card = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterial))
    private let accent: UIColor
    private let surface: UIColor
    init(accent: UIColor, surface: UIColor) {
        self.accent = accent
        self.surface = surface
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        accessibilityElementsHidden = true
        backgroundColor = surface
        glow.colors = [accent.withAlphaComponent(0.16).cgColor, surface.withAlphaComponent(0).cgColor]
        glow.startPoint = CGPoint(x: 0.9, y: 0)
        glow.endPoint = CGPoint(x: 0.1, y: 1)
        layer.addSublayer(glow)
        card.layer.cornerRadius = 24
        card.clipsToBounds = true
        card.layer.borderWidth = 0.5
        addSubview(card)
        NotificationCenter.default.addObserver(self, selector: #selector(updateAccessibility), name: UIAccessibility.reduceTransparencyStatusDidChangeNotification, object: nil)
        updateAccessibility()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    deinit { NotificationCenter.default.removeObserver(self) }
    @objc private func updateAccessibility() {
        card.effect = UIAccessibility.isReduceTransparencyEnabled ? nil : UIBlurEffect(style: .systemThinMaterial)
        card.backgroundColor = UIAccessibility.isReduceTransparencyEnabled ? surface : accent.withAlphaComponent(0.06)
        card.layer.borderColor = accent.withAlphaComponent(0.16).cgColor
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin(); CATransaction.setDisableActions(true)
        glow.frame = bounds
        CATransaction.commit()
    }
    func setFieldFrame(_ frame: CGRect) {
        card.isHidden = frame.isEmpty
        card.frame = frame
    }
}
