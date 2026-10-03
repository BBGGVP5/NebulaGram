import UIKit
import NebulaSettingsContract
import TelegramPresentationData

/// Preview stays above the native composer; tapping Apply is the only write path.
public final class NebulaDraftTranslation: NSObject {
    private static let outlineIcon: UIImage = {
        UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24)).image { _ in
            UIColor.white.setStroke()
            let path = UIBezierPath()
            path.lineWidth = 1.8
            path.lineCapStyle = .round
            path.lineJoinStyle = .round
            path.move(to: CGPoint(x: 10, y: 3))
            path.addCurve(to: CGPoint(x: 18, y: 11), controlPoint1: CGPoint(x: 11.1, y: 8.2), controlPoint2: CGPoint(x: 12.8, y: 9.9))
            path.addCurve(to: CGPoint(x: 10, y: 19), controlPoint1: CGPoint(x: 12.8, y: 12.1), controlPoint2: CGPoint(x: 11.1, y: 13.8))
            path.addCurve(to: CGPoint(x: 2, y: 11), controlPoint1: CGPoint(x: 8.9, y: 13.8), controlPoint2: CGPoint(x: 7.2, y: 12.1))
            path.addCurve(to: CGPoint(x: 10, y: 3), controlPoint1: CGPoint(x: 7.2, y: 9.9), controlPoint2: CGPoint(x: 8.9, y: 8.2))
            path.close()
            for (start, end) in [(CGPoint(x: 19, y: 2), CGPoint(x: 19, y: 6)),
                                 (CGPoint(x: 17, y: 4), CGPoint(x: 21, y: 4)),
                                 (CGPoint(x: 20, y: 16), CGPoint(x: 20, y: 20)),
                                 (CGPoint(x: 18, y: 18), CGPoint(x: 22, y: 18))] {
                path.move(to: start); path.addLine(to: end)
            }
            path.stroke()
        }.withRenderingMode(.alwaysTemplate)
    }()

    public let button = UIButton(type: .system)
    public let preview = UIView()
    public var openTools: (() -> Void)?
    public var openSettings: (() -> Void)?
    public var apply: ((String, String) -> Void)?
    private let label = UILabel()
    private let use = UIButton(type: .system)
    private let close = UIButton(type: .system)
    private let settings = UIButton(type: .system)
    private var task: Task<Void, Never>?
    private var state = NebulaDraftRevision()
    private var source = ""
    private var result = ""
    private var identityPrefix = ""
    private var russian = false
    public override init() {
        super.init()
        button.setImage(Self.outlineIcon, for: .normal)
        button.addTarget(self, action: #selector(tools), for: .touchUpInside)
        button.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(held(_:))))
        preview.layer.cornerRadius = 16; preview.clipsToBounds = true; preview.isHidden = true
        label.font = .preferredFont(forTextStyle: .subheadline); label.numberOfLines = 4
        label.adjustsFontForContentSizeCategory = true
        for v in [label as UIView, use, close, settings] { preview.addSubview(v) }
        use.addTarget(self, action: #selector(applyResult), for: .touchUpInside)
        close.setImage(UIImage(systemName: "xmark"), for: .normal); close.addTarget(self, action: #selector(hide), for: .touchUpInside)
        settings.setImage(UIImage(systemName: "slider.horizontal.3"), for: .normal); settings.addTarget(self, action: #selector(configure), for: .touchUpInside)
    }
    public func style(theme: PresentationTheme, russian: Bool) {
        self.russian = russian
        button.tintColor = theme.chat.inputPanel.panelControlAccentColor
        button.accessibilityLabel = russian ? "Инструменты ИИ" : "AI tools"
        preview.backgroundColor = theme.list.itemBlocksBackgroundColor.withAlphaComponent(1)
        label.textColor = theme.list.itemPrimaryTextColor
        for v in [use, close, settings] { v.tintColor = theme.list.itemAccentColor }
        use.setTitle(russian ? "Применить" : "Apply", for: .normal)
        settings.accessibilityLabel = russian ? "Настройки перевода" : "Translation settings"
        close.accessibilityLabel = russian ? "Закрыть" : "Close"
    }
    public func layout(above frame: CGRect, in host: UIView) {
        guard !preview.isHidden else { return }
        if preview.superview !== host { host.addSubview(preview) }
        let width = max(100, min(420, host.bounds.width - 24))
        let labelHeight = min(120, label.sizeThatFits(CGSize(width: width - 24, height: .greatestFiniteMagnitude)).height)
        let height = labelHeight + 58
        preview.frame = CGRect(x: (host.bounds.width - width) / 2, y: max(host.safeAreaInsets.top, frame.minY - height - 6), width: width, height: height)
        label.frame = CGRect(x: 12, y: 10, width: width - 24, height: labelHeight)
        settings.frame = CGRect(x: 4, y: height - 44, width: 44, height: 44)
        close.frame = CGRect(x: 48, y: height - 44, width: 44, height: 44)
        use.frame = CGRect(x: width - 140, y: height - 44, width: 132, height: 44)
        host.bringSubviewToFront(preview)
    }
    public func update(source: String, options: NebulaTranslationOptions, allowed: Bool) {
        guard allowed, options.draft, NebulaLiveTranslation.ready, !source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, source.count <= 12000 else { stop(); return }
        identityPrefix = options.draftLanguage + ":" + NebulaLiveTranslation.connectionIdentity + ":"
        let identity = identityPrefix + source
        guard let version = state.begin(identity) else { return }
        task?.cancel(); preview.isHidden = true; self.source = source
        task = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: UInt64(options.delay * 1_000_000_000))
                let result = try await NebulaLiveTranslation.translate(source, language: options.draftLanguage)
                guard let self = self, !Task.isCancelled, self.state.accepts(version) else { return }
                self.result = result; self.label.text = result; self.use.isHidden = false; self.preview.isHidden = false
            } catch {
                guard let self = self, !Task.isCancelled, self.state.accepts(version) else { return }
                self.label.text = NebulaAiService.message(for: error, russian: self.russian)
                self.use.isHidden = true; self.preview.isHidden = false
            }
        }
    }
    public func stop() { state.cancel(); task?.cancel(); task = nil; preview.isHidden = true }
    @objc private func tools() { stop(); openTools?() }
    @objc private func configure() { stop(); openSettings?() }
    @objc private func held(_ gesture: UILongPressGestureRecognizer) { if gesture.state == .began { configure() } }
    @objc private func applyResult() { let source = self.source, result = self.result; stop(); state.suppress(identityPrefix + result); apply?(source, result) }
    @objc private func hide() { stop(); state.suppress(identityPrefix + source) }
    deinit { task?.cancel() }
}
