import UIKit
import TelegramPresentationData

/// Recording choice only. The input-frame provider follows the native composer.
@MainActor
public func nebulaPresentRecordingCamera(from host: UIViewController, russian: Bool,
                                        theme: PresentationTheme, inputFrame: @escaping () -> CGRect?,
                                        choose: @escaping (Int) -> Void) {
    guard host.presentedViewController == nil, host.viewIfLoaded?.window != nil else { return }
    let controller = NebulaRoundCameraController(host: host, russian: russian, theme: theme,
        inputFrame: inputFrame) { [weak host] index in
        guard let host, host.viewIfLoaded?.window != nil else { return }
        choose(index)
    }
    controller.modalPresentationStyle = .overFullScreen
    host.present(controller, animated: false)
}

private final class NebulaRoundCameraController: UIViewController {
    private weak var host: UIViewController?
    private let ru: Bool
    private let theme: PresentationTheme
    private let inputFrame: () -> CGRect?
    private let choose: (Int) -> Void
    private let backdrop = UIButton(type: .custom)
    private let card = UIScrollView()
    private let stack = UIStackView()
    private var didChoose = false
    private var closing = false
    private var displayLink: CADisplayLink?
    private static let lastFrontKey = "nebula.round.last_front"

    init(host: UIViewController, russian: Bool, theme: PresentationTheme,
         inputFrame: @escaping () -> CGRect?, choose: @escaping (Int) -> Void) {
        self.host = host; ru = russian; self.theme = theme; self.inputFrame = inputFrame; self.choose = choose
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        overrideUserInterfaceStyle = theme.overallDarkAppearance ? .dark : .light
        view.backgroundColor = .clear; view.accessibilityViewIsModal = true
        backdrop.backgroundColor = UIColor.black.withAlphaComponent(0.55)
        backdrop.accessibilityLabel = ru ? "Закрыть выбор камеры" : "Close camera choice"
        backdrop.accessibilityIdentifier = "nebula.camera.backdrop"
        backdrop.addTarget(self, action: #selector(dismissChoice), for: .touchUpInside)
        view.addSubview(backdrop)
        card.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
        card.layer.cornerRadius = 24; card.clipsToBounds = true; card.showsVerticalScrollIndicator = false
        card.accessibilityIdentifier = "nebula.camera.card"; view.addSubview(card)
        stack.axis = .vertical; stack.spacing = 12; stack.translatesAutoresizingMaskIntoConstraints = false; card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: card.contentLayoutGuide.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.contentLayoutGuide.trailingAnchor, constant: -14),
            stack.topAnchor.constraint(equalTo: card.contentLayoutGuide.topAnchor, constant: 12),
            stack.bottomAnchor.constraint(equalTo: card.contentLayoutGuide.bottomAnchor, constant: -12),
            stack.widthAnchor.constraint(equalTo: card.frameLayoutGuide.widthAnchor, constant: -28)
        ])
        let glyph = UIImageView(image: UIImage(systemName: "camera", withConfiguration: UIImage.SymbolConfiguration(pointSize: 23, weight: .regular)))
        glyph.contentMode = .center; glyph.tintColor = theme.list.itemAccentColor
        glyph.backgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.13); glyph.layer.cornerRadius = 15
        glyph.widthAnchor.constraint(equalToConstant: 44).isActive = true; glyph.heightAnchor.constraint(equalToConstant: 44).isActive = true
        let title = label(ru ? "Камера кружка" : "Round video camera", size: 20, color: theme.list.itemPrimaryTextColor, bold: true)
        title.accessibilityTraits.insert(.header)
        let subtitle = label(ru ? "С какой стороны начать запись?" : "Which camera should start recording?", size: 12, color: theme.list.itemSecondaryTextColor)
        let labels = UIStackView(arrangedSubviews: [title, subtitle]); labels.axis = .vertical; labels.spacing = 5
        let close = UIButton(type: .system)
        var configuration = UIButton.Configuration.filled()
        configuration.image = UIImage(systemName: "xmark", withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .semibold))
        configuration.baseForegroundColor = theme.list.itemAccentColor
        configuration.baseBackgroundColor = theme.list.itemAccentColor.withAlphaComponent(0.13)
        configuration.cornerStyle = .capsule; close.configuration = configuration
        close.accessibilityLabel = ru ? "Закрыть" : "Close"; close.accessibilityIdentifier = "nebula.camera.close"
        close.addTarget(self, action: #selector(dismissChoice), for: .touchUpInside)
        close.widthAnchor.constraint(equalToConstant: 44).isActive = true; close.heightAnchor.constraint(equalToConstant: 44).isActive = true
        let header = UIStackView(arrangedSubviews: [glyph, labels, close]); header.alignment = .center; header.spacing = 12
        stack.addArrangedSubview(header)
        let choices = UIStackView(); choices.axis = .horizontal; choices.spacing = 8; choices.distribution = .fillEqually; choices.alignment = .fill
        let front = UserDefaults.standard.object(forKey: Self.lastFrontKey) as? Bool ?? true
        for index in 0..<2 {
            let name = ru ? (index == 0 ? "Фронтальная" : "Основная") : (index == 0 ? "Front camera" : "Rear camera")
            let checked = front == (index == 0)
            let button = UIButton(type: .system)
            var style = UIButton.Configuration.filled(); style.title = name
            style.baseBackgroundColor = checked ? theme.list.itemAccentColor : theme.list.itemAccentColor.withAlphaComponent(0.13)
            style.baseForegroundColor = checked ? (theme.overallDarkAppearance ? .black : .white) : theme.list.itemAccentColor
            style.cornerStyle = .capsule; style.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 10, bottom: 12, trailing: 10)
            button.configuration = style; button.titleLabel?.font = UIFontMetrics(forTextStyle: .subheadline).scaledFont(for: .systemFont(ofSize: 15, weight: .semibold))
            button.titleLabel?.adjustsFontForContentSizeCategory = true; button.titleLabel?.numberOfLines = 2; button.titleLabel?.textAlignment = .center
            button.isSelected = checked; button.accessibilityTraits = checked ? [.button, .selected] : [.button]
            button.tag = index; button.accessibilityIdentifier = index == 0 ? "nebula.camera.front" : "nebula.camera.rear"
            button.heightAnchor.constraint(greaterThanOrEqualToConstant: 56).isActive = true
            button.addTarget(self, action: #selector(pick(_:)), for: .touchUpInside); choices.addArrangedSubview(button)
        }
        stack.addArrangedSubview(choices)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        let observer = FrameObserver(); observer.controller = self
        let link = CADisplayLink(target: observer, selector: #selector(FrameObserver.update))
        link.add(to: .main, forMode: .common); displayLink = link
        positionCard()
        if !UIAccessibility.isReduceMotionEnabled {
            card.alpha = 0; backdrop.alpha = 0
            UIView.animate(withDuration: 0.18) { self.card.alpha = 1; self.backdrop.alpha = 1 }
        }
        UIAccessibility.post(notification: .screenChanged, argument: stack)
    }
    override func viewDidDisappear(_ animated: Bool) { displayLink?.invalidate(); displayLink = nil; super.viewDidDisappear(animated) }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); positionCard() }
    private func positionCard() {
        backdrop.frame = view.bounds
        guard !closing, let host, host.viewIfLoaded?.window != nil, let input = inputFrame() else {
            if isViewLoaded && view.window != nil && !closing { dismissChoice() }; return
        }
        let top = host.view.convert(input, to: view).minY - 8
        let left = view.safeAreaInsets.left + 12
        let width = max(0, view.bounds.width - left - view.safeAreaInsets.right - 12)
        let height = stack.systemLayoutSizeFitting(CGSize(width: max(0, width - 28), height: 0),
            withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel).height + 24
        let available = max(0, top - view.safeAreaInsets.top - 12)
        card.frame = CGRect(x: left, y: top - min(height, available), width: width, height: min(height, available))
    }
    @objc private func dismissChoice() { finish(nil) }
    override func accessibilityPerformEscape() -> Bool { dismissChoice(); return true }
    @objc private func pick(_ sender: UIControl) {
        guard !didChoose, !closing, view.window != nil, (0..<2).contains(sender.tag) else { return }
        didChoose = true; UserDefaults.standard.set(sender.tag == 0, forKey: Self.lastFrontKey); finish(sender.tag)
    }
    private func finish(_ index: Int?) {
        guard !closing else { return }; closing = true
        displayLink?.invalidate(); displayLink = nil
        let completion = { [weak self] in
            guard let self else { return }; let action = self.choose
            self.dismiss(animated: false) { if let index { action(index) } }
        }
        if UIAccessibility.isReduceMotionEnabled { completion() }
        else { UIView.animate(withDuration: 0.16, animations: { self.card.alpha = 0; self.backdrop.alpha = 0 }, completion: { _ in completion() }) }
    }
    private func label(_ text: String, size: CGFloat, color: UIColor, bold: Bool = false) -> UILabel {
        let label = UILabel(); label.text = text; label.numberOfLines = 0; label.textColor = color
        label.font = UIFontMetrics(forTextStyle: .body).scaledFont(for: .systemFont(ofSize: size, weight: bold ? .semibold : .regular))
        label.adjustsFontForContentSizeCategory = true; return label
    }
    @MainActor private final class FrameObserver: NSObject {
        weak var controller: NebulaRoundCameraController?
        @objc func update() { controller?.positionCard() }
    }
}
