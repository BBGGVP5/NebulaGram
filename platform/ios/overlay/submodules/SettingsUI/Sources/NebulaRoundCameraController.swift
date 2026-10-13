import UIKit
import TelegramPresentationData

/// Recording choice only. Camera ownership and permission handling remain native.
@MainActor
public func nebulaPresentRecordingCamera(from host: UIViewController, russian: Bool,
                                        theme: PresentationTheme, choose: @escaping (Int) -> Void) {
    guard host.presentedViewController == nil else { return }
    let controller = NebulaRoundCameraController(russian: russian, theme: theme) { [weak host] index in
        guard let host, host.viewIfLoaded?.window != nil else { return }
        choose(index)
    }
    controller.modalPresentationStyle = .pageSheet
    controller.preferredContentSize = CGSize(width: 390, height: 360)
    controller.preferredWidth = host.view.bounds.width
    if let sheet = controller.sheetPresentationController {
        if #available(iOS 16.0, *) {
            sheet.detents = [.custom(identifier: .init("nebula.camera")) { [weak controller] context in
                min(context.maximumDetentValue, controller?.panelHeight() ?? 360)
            }, .large()]
        } else {
            sheet.detents = [.medium(), .large()]
        }
        sheet.prefersGrabberVisible = true
        sheet.preferredCornerRadius = 28
        sheet.prefersEdgeAttachedInCompactHeight = true
        sheet.widthFollowsPreferredContentSizeWhenEdgeAttached = true
    }
    host.present(controller, animated: true)
}

private final class NebulaRoundCameraController: UIViewController {
    private let ru: Bool
    private let theme: PresentationTheme
    private let choose: (Int) -> Void
    private let stack = UIStackView()
    private var didChoose = false
    private var measuredHeight: CGFloat = 0
    var preferredWidth: CGFloat = 390
    private static let lastFrontKey = "nebula.round.last_front"

    init(russian: Bool, theme: PresentationTheme, choose: @escaping (Int) -> Void) {
        ru = russian; self.theme = theme; self.choose = choose
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func viewDidLoad() {
        super.viewDidLoad()
        overrideUserInterfaceStyle = theme.overallDarkAppearance ? .dark : .light
        view.backgroundColor = theme.list.blocksBackgroundColor.withAlphaComponent(1)
        view.tintColor = theme.list.itemAccentColor
        let scroll = UIScrollView(); scroll.alwaysBounceVertical = false
        scroll.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(scroll)
        stack.axis = .vertical; stack.spacing = 8; stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: view.topAnchor), scroll.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -20),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 24),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -40)
        ])
        let title = label(ru ? "Камера кружка" : "Round video camera", size: 22, color: theme.list.itemPrimaryTextColor, bold: true)
        title.accessibilityTraits.insert(.header)
        let close = UIButton(type: .system)
        var configuration = UIButton.Configuration.plain()
        configuration.image = UIImage(systemName: "xmark", withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .medium))
        configuration.baseForegroundColor = theme.list.itemPrimaryTextColor
        close.configuration = configuration; close.accessibilityLabel = ru ? "Закрыть" : "Close"
        close.accessibilityIdentifier = "nebula.camera.close"
        close.addTarget(self, action: #selector(dismissChoice), for: .touchUpInside)
        close.widthAnchor.constraint(equalToConstant: 48).isActive = true
        close.heightAnchor.constraint(equalToConstant: 48).isActive = true
        let header = UIStackView(arrangedSubviews: [title, close]); header.alignment = .center; header.spacing = 8
        stack.addArrangedSubview(header)
        let subtitle = label(ru ? "С какой стороны начать запись?" : "Which camera should start recording?", size: 14, color: theme.list.itemSecondaryTextColor)
        stack.addArrangedSubview(subtitle); stack.setCustomSpacing(18, after: subtitle)
        let front = UserDefaults.standard.object(forKey: Self.lastFrontKey) as? Bool ?? true
        for index in 0..<2 {
            let name = ru ? (index == 0 ? "Фронтальная" : "Основная") : (index == 0 ? "Front camera" : "Rear camera")
            let detail = ru ? (index == 0 ? "Снимайте себя" : "Снимайте то, что перед вами") : (index == 0 ? "Record yourself" : "Record what's in front of you")
            let row = CameraChoice(front: index == 0, checked: front == (index == 0), name: name, detail: detail, theme: theme)
            row.tag = index; row.accessibilityIdentifier = index == 0 ? "nebula.camera.front" : "nebula.camera.rear"
            row.addTarget(self, action: #selector(pick(_:)), for: .touchUpInside)
            stack.addArrangedSubview(row)
        }
    }
    func panelHeight() -> CGFloat {
        loadViewIfNeeded()
        let width = max(240, (view.bounds.width > 0 ? view.bounds.width : preferredWidth) - 40)
        return stack.systemLayoutSizeFitting(CGSize(width: width, height: 0), withHorizontalFittingPriority: .required,
                                           verticalFittingPriority: .fittingSizeLevel).height + 48 + max(16, view.safeAreaInsets.bottom)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let height = panelHeight()
        if abs(height - measuredHeight) > 1 {
            measuredHeight = height; preferredContentSize.height = height
            if #available(iOS 16.0, *) { sheetPresentationController?.invalidateDetents() }
        }
    }
    @objc private func dismissChoice() { dismiss(animated: true) }
    @objc private func pick(_ sender: UIControl) {
        guard !didChoose, view.window != nil, (0..<2).contains(sender.tag) else { return }
        didChoose = true
        let index = sender.tag
        UserDefaults.standard.set(index == 0, forKey: Self.lastFrontKey)
        let action = choose
        dismiss(animated: true) { action(index) }
    }
    private func label(_ text: String, size: CGFloat, color: UIColor, bold: Bool = false) -> UILabel {
        let label = UILabel(); label.text = text; label.numberOfLines = 0; label.textColor = color
        label.font = UIFontMetrics(forTextStyle: .body).scaledFont(for: .systemFont(ofSize: size, weight: bold ? .semibold : .regular))
        label.adjustsFontForContentSizeCategory = true; return label
    }
    private final class CameraChoice: UIControl {
        init(front: Bool, checked: Bool, name: String, detail: String, theme: PresentationTheme) {
            super.init(frame: .zero)
            backgroundColor = checked ? theme.list.itemAccentColor.withAlphaComponent(0.15) : .clear
            layer.cornerRadius = 20; isSelected = checked
            isAccessibilityElement = true; accessibilityLabel = name + ". " + detail
            accessibilityTraits = checked ? [.button, .selected] : [.button]
            let color = checked ? theme.list.itemAccentColor : theme.list.itemPrimaryTextColor
            let drawing = CameraDrawing(front: front, color: color)
            drawing.widthAnchor.constraint(equalToConstant: 48).isActive = true
            drawing.heightAnchor.constraint(equalToConstant: 52).isActive = true
            let title = UILabel(); title.text = name; title.textColor = color; title.numberOfLines = 0
            title.font = UIFontMetrics(forTextStyle: .body).scaledFont(for: .systemFont(ofSize: 17, weight: .semibold))
            title.adjustsFontForContentSizeCategory = true
            let subtitle = UILabel(); subtitle.text = detail; subtitle.textColor = theme.list.itemSecondaryTextColor; subtitle.numberOfLines = 0
            subtitle.font = UIFontMetrics(forTextStyle: .caption1).scaledFont(for: .systemFont(ofSize: 13)); subtitle.adjustsFontForContentSizeCategory = true
            let labels = UIStackView(arrangedSubviews: [title, subtitle]); labels.axis = .vertical; labels.spacing = 5
            let row = UIStackView(arrangedSubviews: [drawing, labels]); row.alignment = .center; row.spacing = 14
            row.isUserInteractionEnabled = false; row.translatesAutoresizingMaskIntoConstraints = false; addSubview(row)
            NSLayoutConstraint.activate([heightAnchor.constraint(greaterThanOrEqualToConstant: 88),
                row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 14), row.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -14),
                row.topAnchor.constraint(equalTo: topAnchor, constant: 14), row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -14)])
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override var isHighlighted: Bool { didSet { alpha = isHighlighted ? 0.65 : 1 } }
    }
    private final class CameraDrawing: UIView {
        private let front: Bool, color: UIColor
        init(front: Bool, color: UIColor) {
            self.front = front; self.color = color; super.init(frame: .zero)
            backgroundColor = .clear; isAccessibilityElement = false
        }
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            context.saveGState(); defer { context.restoreGState() }
            let scale = min(rect.width / 48, rect.height / 52)
            context.translateBy(x: (rect.width - 48 * scale) / 2, y: (rect.height - 52 * scale) / 2); context.scaleBy(x: scale, y: scale)
            let path = UIBezierPath(roundedRect: CGRect(x: 10, y: 3, width: 28, height: 46), cornerRadius: 7)
            if front {
                path.move(to: CGPoint(x: 20, y: 8)); path.addLine(to: CGPoint(x: 28, y: 8))
                path.append(UIBezierPath(ovalIn: CGRect(x: 19.5, y: 18.5, width: 9, height: 9)))
                path.move(to: CGPoint(x: 17, y: 35)); path.addCurve(to: CGPoint(x: 31, y: 35), controlPoint1: CGPoint(x: 17, y: 28), controlPoint2: CGPoint(x: 31, y: 28))
            } else {
                path.append(UIBezierPath(roundedRect: CGRect(x: 14, y: 7, width: 14, height: 20), cornerRadius: 5))
                for y: CGFloat in [10.2, 18.2] { path.append(UIBezierPath(ovalIn: CGRect(x: 18.2, y: y, width: 5.6, height: 5.6))) }
            }
            color.setStroke(); path.lineWidth = 1.8; path.lineCapStyle = .round; path.lineJoinStyle = .round; path.stroke()
        }
    }
}
