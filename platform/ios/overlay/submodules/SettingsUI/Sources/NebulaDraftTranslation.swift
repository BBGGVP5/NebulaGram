import UIKit
import Display
import NebulaSettingsContract
import TelegramPresentationData

/// Preview stays above the native composer; tapping Apply is the only write path.
public final class NebulaDraftTranslation: NSObject {


    public let button = UIButton(type: .system)
    public let preview = UIView()
    public var openTools: (() -> Void)?
    public var openSettings: (() -> Void)?
    public var apply: ((NSAttributedString, NSAttributedString) -> Void)?
    private let label = UILabel()
    private let use = UIButton(type: .system)
    private let close = UIButton(type: .system)
    private let settings = UIButton(type: .system)
    private var task: Task<Void, Never>?
    private var state = NebulaDraftRevision()
    private var source = NSAttributedString(string: "")
    private var attributesRevision = 0
    private var result = NSAttributedString(string: "")
    private var identityPrefix = ""
    private var russian = false
    public override init() {
        super.init()
        button.setImage(NebulaAIOutline.image, for: .normal)
        button.contentEdgeInsets = UIEdgeInsets(top: 0, left: 8, bottom: 0, right: 0)
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
    private var cache: [String: (NSAttributedString, NSAttributedString)] = [:]
    public func update(source: NSAttributedString, options: NebulaTranslationOptions, allowed: Bool) {
        guard allowed, options.draft, NebulaLiveTranslation.ready, !source.string.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, source.length <= 12000 else { stop(); return }
        identityPrefix = options.draftLanguage + ":" + NebulaLiveTranslation.connectionIdentity + ":"
        if !self.source.isEqual(to: source) { attributesRevision += 1 }
        let cacheKey = identityPrefix + source.string
        let identity = cacheKey + ":\(attributesRevision)"
        guard let version = state.begin(identity) else { return }
        task?.cancel(); preview.isHidden = true; self.source = source
        if let cached = cache[cacheKey], cached.0.isEqual(to: source) {
            self.result = cached.1; label.text = cached.1.string; use.isHidden = false; revealPreview(); return
        }
        task = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: UInt64(options.delay * 1_000_000_000))
                guard let self = self, !Task.isCancelled, self.state.accepts(version) else { return }
                self.label.text = self.russian ? "Nebula AI · переводим…" : "Nebula AI · translating…"
                self.use.isHidden = true; self.revealPreview()
                let result = try await NebulaRichEditorTransform.generate(source, instruction: "Translate into language code \(options.draftLanguage).")
                guard !Task.isCancelled, self.state.accepts(version) else { return }
                if self.cache.count >= 16 { self.cache.removeAll() }; self.cache[cacheKey] = (source, result)
                self.result = result; self.label.text = result.string; self.use.isHidden = false; self.revealPreview()
            } catch {
                guard let self = self, !Task.isCancelled, self.state.accepts(version) else { return }
                self.label.text = NebulaAiService.message(for: error, russian: self.russian)
                self.use.isHidden = true; self.revealPreview()
            }
        }
    }
    private func revealPreview() {
        preview.layer.removeAllAnimations(); preview.alpha = 1; preview.isHidden = false
        guard !UIAccessibility.isReduceMotionEnabled else { return }
        preview.alpha = 0
        UIView.animate(withDuration: 0.18) { self.preview.alpha = 1 }
    }
    public func stop() { state.cancel(); task?.cancel(); task = nil; preview.isHidden = true }
    @objc private func tools() { stop(); openTools?() }
    @objc private func configure() { stop(); openSettings?() }
    @objc private func held(_ gesture: UILongPressGestureRecognizer) { if gesture.state == .began { configure() } }
    @objc private func applyResult() { let source = self.source, result = self.result; stop(); state.suppress(identityPrefix + result.string + ":\(attributesRevision + 1)"); apply?(source, result) }
    @objc private func hide() { stop(); state.suppress(identityPrefix + source.string + ":\(attributesRevision)") }
    deinit { task?.cancel() }
}
