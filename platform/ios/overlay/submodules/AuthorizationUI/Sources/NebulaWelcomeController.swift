import UIKit

// Same transition surface as the native intro, with Nebula artwork and copy.
final class NebulaWelcomeController: UIViewController {
    var startMessaging: (() -> Void)?
    var startMessagingInAlternativeLanguage: ((String?) -> Void)?
    var createStartButton: ((CGFloat) -> UIView?)?
    var languageChanged: ((String) -> Void)?
    var selectedLanguage = Locale.preferredLanguages.first?.hasPrefix("ru") == true ? "ru" : "en"
    var defaultFrame = CGRect.zero
    var isEnabled = true { didSet { view.isUserInteractionEnabled = isEnabled } }
    private let backdrop: NebulaAuthBackdropView
    private let art: NebulaWelcomeSceneView
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let progress: NebulaAuthProgressView
    private let scroll = UIScrollView()
    private let languageButton = UIButton(type: .system)
    private let skipButton = UIButton(type: .system)
    private var primary: UIView?
    private let accent: UIColor
    private let textColor: UIColor
    private var page = 0
    var pageChanged: ((Int, String) -> Void)?

    init(backgroundColor: UIColor, primaryColor: UIColor, accentColor: UIColor) {
        accent = accentColor
        textColor = primaryColor
        progress = NebulaAuthProgressView(current: 0, steps: 5, accent: accentColor,
                                          muted: primaryColor.withAlphaComponent(0.18))
        backdrop = NebulaAuthBackdropView(accent: accentColor, surface: backgroundColor)
        art = NebulaWelcomeSceneView(accent: accentColor, text: primaryColor,
                                     surface: backgroundColor)
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func loadView() {
        view = UIView(frame: defaultFrame)
        view.addSubview(backdrop)
        view.addSubview(scroll)
        scroll.alwaysBounceVertical = false
        for label in [titleLabel, subtitleLabel] {
            label.numberOfLines = 0
            label.textAlignment = .center
            label.textColor = textColor
            label.adjustsFontForContentSizeCategory = true
            scroll.addSubview(label)
        }
        titleLabel.accessibilityTraits = .header
        subtitleLabel.alpha = 0.8
        view.addSubview(progress)
        scroll.addSubview(art)
        languageButton.accessibilityIdentifier = "Nebula.Welcome.Language"
        languageButton.addTarget(self, action: #selector(changeLanguage), for: .touchUpInside)
        view.addSubview(languageButton)
        skipButton.accessibilityIdentifier = "Nebula.Welcome.Skip"
        skipButton.addTarget(self, action: #selector(skipTour), for: .touchUpInside)
        skipButton.tintColor = accent
        view.addSubview(skipButton)
        updateCopy()
    }
    private func updateCopy() {
        let copy = NebulaAuthCopy(selectedLanguage)
        let titleText = copy.tourTitle(page)
        let title = NSMutableAttributedString(string: titleText)
        let brand = page == 4 ? "NebulaLink" : "NebulaGram"
        let range = (titleText as NSString).range(of: brand)
        if range.location != NSNotFound { title.addAttribute(.foregroundColor, value: accent, range: range) }
        titleLabel.attributedText = title
        subtitleLabel.text = copy.tourSubtitle(page)
        languageButton.setTitle(copy.russian ? "Язык · Русский / English" : "Language · English / Русский", for: .normal)
        languageButton.tintColor = accent
        skipButton.setTitle(copy.skip, for: .normal)
        languageButton.isHidden = page != 0
        skipButton.isHidden = page == 0 || page == 4
        art.setPage(page)
        progress.setCurrent(page)
        languageChanged?(selectedLanguage)
        pageChanged?(page, selectedLanguage)
        view.setNeedsLayout()
    }
    @objc private func changeLanguage() {
        selectedLanguage = selectedLanguage == "ru" ? "en" : "ru"
        updateCopy()
    }
    @objc private func skipTour() { startMessaging?() }
    func advance() {
        if page < 4 {
            page += 1
            updateCopy()
            UIAccessibility.post(notification: .screenChanged, argument: titleLabel)
        } else {
            startMessaging?()
        }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        backdrop.frame = view.bounds
        let width = max(1, min(430, view.bounds.width - 48))
        let x = (view.bounds.width - width) / 2
        let progressWidth = NebulaAuthProgressView.width(steps: 5)
        progress.frame = CGRect(x: (view.bounds.width - progressWidth) / 2,
                                y: view.bounds.height - view.safeAreaInsets.bottom - 26,
                                width: progressWidth, height: 4)
        languageButton.frame = CGRect(x: x, y: progress.frame.minY - 52, width: width, height: 44)
        skipButton.frame = languageButton.frame
        if primary == nil, let button = createStartButton?(width) {
            primary = button
            view.addSubview(button)
        } else { _ = createStartButton?(width) }
        primary?.frame = CGRect(x: x, y: languageButton.frame.minY - 62, width: width, height: 50)
        let scrollTop = view.safeAreaInsets.top + 12
        let contentBottom = (primary?.frame.minY ?? languageButton.frame.minY) - 24
        scroll.frame = CGRect(x: 0, y: scrollTop, width: view.bounds.width,
                              height: max(1, contentBottom - scrollTop))
        titleLabel.font = UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: .systemFont(ofSize: 32, weight: .bold))
        subtitleLabel.font = .preferredFont(forTextStyle: .body)
        let artWidth = min(320, view.bounds.width - 40)
        art.frame = CGRect(x: (view.bounds.width - artWidth) / 2, y: 10,
                           width: artWidth, height: 250)
        var y: CGFloat = art.frame.maxY + 24
        for label in [titleLabel, subtitleLabel] {
            let height = label.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height
            label.frame = CGRect(x: x, y: y, width: width, height: height)
            y += height + 20
        }
        scroll.contentSize = CGSize(width: view.bounds.width, height: y)
    }
    func animateIn() {
        guard !UIAccessibility.isReduceMotionEnabled else { return }
        view.alpha = 0
        UIView.animate(withDuration: 0.25) { self.view.alpha = 1 }
    }
    func createAnimationSnapshot() -> UIView? {
        guard let snapshot = art.snapshotView(afterScreenUpdates: false) else { return nil }
        snapshot.frame = art.convert(art.bounds, to: view)
        return snapshot
    }
    func createTextSnapshot() -> UIView? {
        guard let snapshot = titleLabel.snapshotView(afterScreenUpdates: false) else { return nil }
        snapshot.frame = titleLabel.convert(titleLabel.bounds, to: view)
        return snapshot
    }
}

/// Original, scalable scenes shared in spirit with Android's native welcome art.
private final class NebulaWelcomeSceneView: UIView {
    private let accent: UIColor
    private let text: UIColor
    private let surface: UIColor
    private let cyan = UIColor(red: 0.24, green: 0.79, blue: 0.87, alpha: 1)
    private let violet = UIColor(red: 0.43, green: 0.44, blue: 0.94, alpha: 1)
    private var page = 0

    init(accent: UIColor, text: UIColor, surface: UIColor) {
        self.accent = accent
        self.text = text
        self.surface = surface
        super.init(frame: .zero)
        backgroundColor = .clear
        isOpaque = false
        isUserInteractionEnabled = false
        isAccessibilityElement = false
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func setPage(_ value: Int) { page = value; setNeedsDisplay() }

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        let scale = min(bounds.width / 320, bounds.height / 250)
        ctx.translateBy(x: (bounds.width - 320 * scale) / 2,
                        y: (bounds.height - 250 * scale) / 2)
        ctx.scaleBy(x: scale, y: scale)
        dot(160, 124, 104, cyan.withAlphaComponent(0.13))
        dot(252, 43, 27, violet.withAlphaComponent(0.12))
        switch page {
        case 1: design()
        case 2: privacy()
        case 3: ai()
        case 4: link()
        default: welcome()
        }
    }

    private func welcome() {
        box(52, 16, 268, 225, 26, text.withAlphaComponent(0.09))
        box(65, 30, 255, 65, 14, accent.withAlphaComponent(0.18))
        label("NebulaGram", 80, 39, 17, accent)
        for i in 0..<3 {
            let y = CGFloat(82 + i * 42)
            dot(86, y + 12, 14, i == 1 ? violet : cyan)
            box(110, y, 233, y + 10, 5, text.withAlphaComponent(0.25))
            box(110, y + 16, CGFloat(194 + i * 10), y + 23, 4, accent.withAlphaComponent(0.22))
        }
        box(74, 201, 246, 216, 8, accent.withAlphaComponent(0.2))
        for i in 0..<3 { dot(CGFloat(110 + i * 50), 208, 4, accent) }
        sparkle(264, 110, 14, cyan)
        sparkle(43, 172, 8, violet)
    }

    private func design() {
        box(42, 38, 228, 211, 23, text.withAlphaComponent(0.09))
        box(55, 51, 215, 87, 12, accent.withAlphaComponent(0.2))
        label("Aa", 72, 59, 22, accent)
        for (i, color) in [cyan, violet, accent].enumerated() {
            dot(CGFloat(77 + i * 48), 119, 17, color)
        }
        box(55, 157, 215, 190, 16, accent.withAlphaComponent(0.2))
        box(61, 162, 107, 185, 12, accent)
        for i in 0..<3 { dot(CGFloat(83 + i * 52), 174, 4, text) }
        box(191, 78, 281, 169, 21, violet.withAlphaComponent(0.85))
        box(202, 90, 270, 109, 9, cyan)
        box(202, 119, 254, 129, 5, UIColor.white.withAlphaComponent(0.7))
        box(202, 138, 264, 148, 5, UIColor.white.withAlphaComponent(0.4))
        sparkle(276, 52, 12, cyan)
    }

    private func privacy() {
        box(43, 38, 277, 207, 27, text.withAlphaComponent(0.09))
        box(60, 55, 230, 106, 18, accent.withAlphaComponent(0.18))
        box(73, 69, 193, 79, 5, text.withAlphaComponent(0.38))
        box(73, 86, 210, 93, 4, text.withAlphaComponent(0.23))
        box(90, 121, 259, 174, 18, violet)
        box(108, 136, 230, 146, 5, UIColor.white.withAlphaComponent(0.9))
        box(108, 154, 190, 161, 4, UIColor.white.withAlphaComponent(0.55))
        dot(58, 175, 29, cyan)
        UIImage(systemName: "lock.fill")?.withTintColor(.white, renderingMode: .alwaysOriginal)
            .draw(in: CGRect(x: 46, y: 162, width: 24, height: 24))
        sparkle(265, 78, 12, cyan)
    }

    private func ai() {
        box(51, 24, 269, 215, 28, text.withAlphaComponent(0.09))
        box(67, 39, 252, 86, 17, accent.withAlphaComponent(0.18))
        label("Nebula AI", 83, 50, 18, accent)
        box(89, 111, 240, 143, 15, accent.withAlphaComponent(0.2))
        box(75, 158, 220, 193, 16, violet)
        box(92, 171, 182, 179, 4, UIColor.white.withAlphaComponent(0.88))
        dot(54, 114, 27, cyan)
        sparkle(54, 114, 18, .white)
        sparkle(270, 179, 15, violet)
    }

    private func link() {
        box(43, 26, 277, 218, 26, text.withAlphaComponent(0.09))
        box(58, 40, 262, 89, 16, accent.withAlphaComponent(0.18))
        label("NebulaLink", 75, 52, 18, accent)
        let route = UIBezierPath()
        route.move(to: CGPoint(x: 92, y: 143))
        route.addCurve(to: CGPoint(x: 228, y: 143),
                       controlPoint1: CGPoint(x: 132, y: 111),
                       controlPoint2: CGPoint(x: 174, y: 194))
        route.lineWidth = 4
        accent.setStroke(); route.stroke()
        dot(91, 143, 17, cyan)
        dot(162, 154, 20, violet)
        dot(229, 143, 17, UIColor(red: 0.32, green: 0.85, blue: 0.76, alpha: 1))
        box(79, 191, 241, 204, 7, accent.withAlphaComponent(0.18))
        for i in 0..<3 { dot(CGFloat(105 + i * 55), 197, 3, accent) }
        sparkle(267, 108, 11, cyan)
    }

    private func box(_ left: CGFloat, _ top: CGFloat, _ right: CGFloat,
                     _ bottom: CGFloat, _ radius: CGFloat, _ color: UIColor) {
        color.setFill()
        UIBezierPath(roundedRect: CGRect(x: left, y: top, width: right - left,
                                        height: bottom - top), cornerRadius: radius).fill()
    }
    private func dot(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat, _ color: UIColor) {
        color.setFill()
        UIBezierPath(ovalIn: CGRect(x: x - radius, y: y - radius,
                                   width: radius * 2, height: radius * 2)).fill()
    }
    private func sparkle(_ x: CGFloat, _ y: CGFloat, _ radius: CGFloat, _ color: UIColor) {
        let shape = UIBezierPath()
        shape.move(to: CGPoint(x: x, y: y - radius))
        shape.addQuadCurve(to: CGPoint(x: x + radius, y: y),
                           controlPoint: CGPoint(x: x + radius * 0.2, y: y - radius * 0.2))
        shape.addQuadCurve(to: CGPoint(x: x, y: y + radius),
                           controlPoint: CGPoint(x: x + radius * 0.2, y: y + radius * 0.2))
        shape.addQuadCurve(to: CGPoint(x: x - radius, y: y),
                           controlPoint: CGPoint(x: x - radius * 0.2, y: y + radius * 0.2))
        shape.addQuadCurve(to: CGPoint(x: x, y: y - radius),
                           controlPoint: CGPoint(x: x - radius * 0.2, y: y - radius * 0.2))
        color.setFill(); shape.fill()
    }
    private func label(_ value: String, _ x: CGFloat, _ y: CGFloat,
                       _ size: CGFloat, _ color: UIColor) {
        (value as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: [
            .font: UIFont.systemFont(ofSize: size, weight: .semibold),
            .foregroundColor: color
        ])
    }
}
