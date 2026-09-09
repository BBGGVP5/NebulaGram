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
    private let art: NebulaAuthArtView
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let featureLabel = UILabel()
    private let scroll = UIScrollView()
    private let languageButton = UIButton(type: .system)
    private var primary: UIView?
    private let accent: UIColor
    private let textColor: UIColor

    init(backgroundColor: UIColor, primaryColor: UIColor, accentColor: UIColor) {
        accent = accentColor
        textColor = primaryColor
        backdrop = NebulaAuthBackdropView(accent: accentColor, surface: backgroundColor)
        art = NebulaAuthArtView(kind: .welcome, accent: accentColor)
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func loadView() {
        view = UIView(frame: defaultFrame)
        view.addSubview(backdrop)
        view.addSubview(scroll)
        scroll.alwaysBounceVertical = false
        for label in [titleLabel, subtitleLabel, featureLabel] {
            label.numberOfLines = 0
            label.textAlignment = .center
            label.textColor = textColor
            label.adjustsFontForContentSizeCategory = true
            scroll.addSubview(label)
        }
        titleLabel.accessibilityTraits = .header
        subtitleLabel.alpha = 0.8
        featureLabel.layer.cornerRadius = 24
        featureLabel.clipsToBounds = true
        featureLabel.backgroundColor = accent.withAlphaComponent(0.08)
        scroll.addSubview(art)
        languageButton.accessibilityIdentifier = "Nebula.Welcome.Language"
        languageButton.addTarget(self, action: #selector(changeLanguage), for: .touchUpInside)
        view.addSubview(languageButton)
        updateCopy()
    }
    private func updateCopy() {
        let copy = NebulaAuthCopy(selectedLanguage)
        let title = NSMutableAttributedString(string: copy.welcome)
        title.addAttribute(.foregroundColor, value: accent, range: (copy.welcome as NSString).range(of: "NebulaGram"))
        titleLabel.attributedText = title
        subtitleLabel.text = copy.subtitle
        featureLabel.text = "\(copy.linkTitle)\n\n\(copy.linkSubtitle)"
        languageButton.setTitle(copy.russian ? "Язык · Русский / English" : "Language · English / Русский", for: .normal)
        languageButton.tintColor = accent
        languageChanged?(selectedLanguage)
        view.setNeedsLayout()
    }
    @objc private func changeLanguage() {
        selectedLanguage = selectedLanguage == "ru" ? "en" : "ru"
        updateCopy()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        backdrop.frame = view.bounds
        let width = max(1, min(430, view.bounds.width - 48))
        let x = (view.bounds.width - width) / 2
        languageButton.frame = CGRect(x: x, y: view.bounds.height - view.safeAreaInsets.bottom - 52, width: width, height: 44)
        if primary == nil, let button = createStartButton?(width) {
            primary = button
            view.addSubview(button)
        } else { _ = createStartButton?(width) }
        primary?.frame = CGRect(x: x, y: languageButton.frame.minY - 62, width: width, height: 50)
        scroll.frame = CGRect(x: 0, y: view.safeAreaInsets.top + 12, width: view.bounds.width,
                              height: max(1, languageButton.frame.minY - 86 - view.safeAreaInsets.top))
        titleLabel.font = UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: .systemFont(ofSize: 32, weight: .bold))
        subtitleLabel.font = .preferredFont(forTextStyle: .body)
        featureLabel.font = .preferredFont(forTextStyle: .subheadline)
        art.frame = CGRect(x: (view.bounds.width - 144) / 2, y: 12, width: 144, height: 144)
        var y: CGFloat = art.frame.maxY + 28
        for label in [titleLabel, subtitleLabel, featureLabel] {
            let inset: CGFloat = label === featureLabel ? 32 : 0
            let height = label.sizeThatFits(CGSize(width: width - inset, height: .greatestFiniteMagnitude)).height + inset
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
