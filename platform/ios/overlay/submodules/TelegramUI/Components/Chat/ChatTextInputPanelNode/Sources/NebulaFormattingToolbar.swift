import UIKit

/// Independent implementation of Swiftgram's select-at-caret behavior, using native UTF-16 ranges.
enum NebulaFormattingSelection {
    static func range(_ text: String, selection: Range<Int>, selectWord: Bool) -> Range<Int>? {
        let string = text as NSString
        guard selection.lowerBound >= 0, selection.upperBound <= string.length else { return nil }
        var start = selection.lowerBound, end = selection.upperBound
        func word(_ index: Int) -> Bool {
            let part = string.substring(with: string.rangeOfComposedCharacterSequence(at: index))
            return part.unicodeScalars.contains { CharacterSet.alphanumerics.contains($0) || CharacterSet.nonBaseCharacters.contains($0) || $0 == "_" }
        }
        if start == end && selectWord {
            var cursor = start
            if cursor < string.length { cursor = string.rangeOfComposedCharacterSequence(at: cursor).location }
            if cursor == string.length || !word(cursor) {
                while cursor > 0 && !word(cursor - 1) { cursor = string.rangeOfComposedCharacterSequence(at: cursor - 1).location }
            }
            start = cursor; end = cursor
            while start > 0 && word(start - 1) { start = string.rangeOfComposedCharacterSequence(at: start - 1).location }
            while end < string.length && word(end) { end = NSMaxRange(string.rangeOfComposedCharacterSequence(at: end)) }
            guard start != end else { return nil }
        }
        if start != end {
            let range = string.rangeOfComposedCharacterSequences(for: NSRange(location: start, length: end - start))
            return range.location ..< NSMaxRange(range)
        }
        if start < string.length { start = string.rangeOfComposedCharacterSequence(at: start).location }
        return start ..< start
    }
}

enum NebulaFormattingFeedback {
    static func values(enabled: Bool, highlighted: Bool, reduceMotion: Bool) -> (scale: Double, alpha: Double) {
        let pressed = enabled && highlighted
        return (pressed && !reduceMotion ? 0.92 : 1, enabled ? (pressed ? 0.7 : 1) : 0.35)
    }
}

/// Native editor actions are supplied by the input panel; this view never rewrites a draft.
final class NebulaFormattingToolbar: UIView {
    var action: ((Int) -> Void)?
    /// Width at the right end kept clear of tiles: the floating "A" sits over it.
    var trailingReserve: CGFloat = 0 { didSet { if trailingReserve != oldValue { setNeedsLayout() } } }
    private var presented = false
    /// `origin` is the "A" centre in the superview: the toolbar unfolds sideways out of it
    /// (leftwards, Android 0297) and folds back into it.
    func setPresented(_ value: Bool, from origin: CGPoint? = nil) {
        guard presented != value else { return }
        presented = value
        isUserInteractionEnabled = value
        accessibilityElementsHidden = !value
        var folded = CGAffineTransform(translationX: 0, y: 8)
        if let origin, bounds.width > 0 {
            // A strip as wide as the "A", centred on it, grows to the full row.
            let scale = min(1, max(0.05, 40 / bounds.width))
            folded = CGAffineTransform(translationX: origin.x - center.x, y: origin.y - center.y).scaledBy(x: scale, y: 1)
        }
        let foldedTransform = folded
        if value {
            isHidden = false
            if !UIAccessibility.isReduceMotionEnabled { transform = foldedTransform }
        }
        let changes = {
            self.alpha = value ? 1 : 0
            self.transform = value ? .identity : foldedTransform
        }
        if UIAccessibility.isReduceMotionEnabled || !UIView.areAnimationsEnabled {
            layer.removeAllAnimations()
            changes()
            isHidden = !value
        } else if value && origin != nil {
            UIView.animate(withDuration: 0.34, delay: 0, usingSpringWithDamping: 0.82, initialSpringVelocity: 0, options: [.beginFromCurrentState], animations: changes)
        } else {
            UIView.animate(withDuration: origin == nil ? 0.18 : 0.24, delay: 0, options: [.beginFromCurrentState, .curveEaseInOut], animations: changes) { _ in
                if !self.presented { self.isHidden = true; self.transform = .identity }
            }
        }
    }
    private let background = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterial))
    private let scroll = UIScrollView()
    private let stack = UIStackView()
    private var labelsLanguage = ""
    private var tileColor: UIColor?
    private let symbols = ["bold", "italic", "m.square", "text.quote", "eye.slash", "link", "pencil.slash", "return", "underline", "strikethrough", "chevron.left.forwardslash.chevron.right"]

    override init(frame: CGRect) {
        super.init(frame: frame)
        alpha = 0
        isHidden = true
        transform = CGAffineTransform(translationX: 0, y: 8)
        background.clipsToBounds = true
        background.layer.cornerRadius = 18
        addSubview(background)
        scroll.showsHorizontalScrollIndicator = false
        scroll.delaysContentTouches = false
        addSubview(scroll)
        stack.axis = .horizontal
        stack.distribution = .fill
        stack.spacing = 4
        scroll.addSubview(stack)
        for index in [7, 6, 3, 4, 0, 1, 2, 5, 8, 9, 10] {
            let symbol = symbols[index]
            let button = NebulaFormattingButton(type: .system)
            button.tag = index
            button.widthAnchor.constraint(equalToConstant: 44).isActive = true
            button.layer.cornerRadius = 11
            button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .medium)), for: .normal)
            if index == 2 {
                button.setImage(nil, for: .normal)
                button.setTitle("M", for: .normal)
                button.titleLabel?.font = .monospacedSystemFont(ofSize: 19, weight: .regular)
            }
            button.addTarget(self, action: #selector(pressed(_:)), for: .touchUpInside)
            stack.addArrangedSubview(button)
            if index == 6 { stack.setCustomSpacing(12, after: button) }
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(color: UIColor, russian: Bool, hasSelection: Bool) {
        tintColor = color
        background.isHidden = UIAccessibility.isReduceTransparencyEnabled
        backgroundColor = UIAccessibility.isReduceTransparencyEnabled ? .secondarySystemBackground : .clear
        layer.cornerRadius = 18
        let language = russian ? "ru" : "en"
        if labelsLanguage != language {
            labelsLanguage = language
            let titles = russian ? ["Жирный", "Курсив", "Моноширинный", "Цитата", "Спойлер", "Ссылка", "Убрать форматирование", "Новая строка", "Подчёркнутый", "Зачёркнутый", "Блок кода"]
                : ["Bold", "Italic", "Monospace", "Quote", "Spoiler", "Link", "Clear formatting", "New line", "Underline", "Strikethrough", "Code block"]
            for case let button as UIButton in stack.arrangedSubviews { button.accessibilityLabel = titles[button.tag] }
        }
        if tileColor?.isEqual(color) != true {
            tileColor = color
            let tile = UIGraphicsImageRenderer(size: CGSize(width: 44, height: 44)).image { _ in
                color.withAlphaComponent(0.1).setFill()
                UIBezierPath(roundedRect: CGRect(x: 2.5, y: 2.5, width: 39, height: 39), cornerRadius: 11).fill()
            }
            for case let button as UIButton in stack.arrangedSubviews { button.setBackgroundImage(tile, for: .normal) }
        }
        for case let button as UIButton in stack.arrangedSubviews {
            button.isEnabled = button.tag == 7 || hasSelection
        }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        background.frame = bounds
        scroll.frame = CGRect(x: 8, y: 0, width: max(0, bounds.width - 16 - trailingReserve), height: bounds.height)
        let width = CGFloat(symbols.count) * 44 + CGFloat(symbols.count - 1) * 4 + 8
        stack.frame = CGRect(x: 0, y: 0, width: width, height: scroll.bounds.height)
        scroll.contentSize = stack.bounds.size
    }
    @objc private func pressed(_ sender: UIButton) { action?(sender.tag) }
}

/// Press feedback only; formatting state never turns a tile into a selected chip.
private final class NebulaFormattingButton: UIButton {
    override var isHighlighted: Bool { didSet { updateFeedback() } }
    override var isEnabled: Bool { didSet { updateFeedback() } }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { layer.removeAllAnimations(); transform = .identity }
    }

    private func updateFeedback() {
        let pressed = isEnabled && isHighlighted
        let feedback = NebulaFormattingFeedback.values(enabled: isEnabled, highlighted: isHighlighted,
            reduceMotion: UIAccessibility.isReduceMotionEnabled)
        let changes = {
            self.transform = CGAffineTransform(scaleX: CGFloat(feedback.scale), y: CGFloat(feedback.scale))
            self.alpha = CGFloat(feedback.alpha)
        }
        if window == nil || UIAccessibility.isReduceMotionEnabled || !UIView.areAnimationsEnabled {
            layer.removeAllAnimations()
            changes()
        } else {
            UIView.animate(withDuration: pressed ? 0.08 : 0.16, delay: 0,
                options: [.beginFromCurrentState, .allowUserInteraction, .curveEaseOut], animations: changes)
        }
    }
}

/// Floating "A" above the round voice/send button (Android `registerFormatToggle`): opens and
/// closes the formatting toolbar, which unfolds sideways out of it.
final class NebulaFormattingToggleButton: UIButton {
    private var expanded = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.cornerRadius = 20
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.12
        layer.shadowRadius = 6
        layer.shadowOffset = CGSize(width: 0, height: 2)
        setImage(UIImage(systemName: "textformat", withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .medium)), for: .normal)
        accessibilityLabel = Locale.preferredLanguages.first?.hasPrefix("ru") == true ? "Форматирование" : "Formatting"
        accessibilityTraits.insert(.button)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(fill: UIColor) {
        if backgroundColor?.isEqual(fill) != true { backgroundColor = fill }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.shadowPath = UIBezierPath(ovalIn: bounds).cgPath
    }

    func setExpanded(_ value: Bool) {
        guard expanded != value else { return }
        expanded = value
        accessibilityValue = value ? (Locale.preferredLanguages.first?.hasPrefix("ru") == true ? "открыто" : "open") : nil
        let changes = { self.imageView?.transform = value ? CGAffineTransform(scaleX: 1.12, y: 1.12) : .identity; self.alpha = value ? 1 : 0.85 }
        if UIAccessibility.isReduceMotionEnabled { changes() } else { UIView.animate(withDuration: 0.2, animations: changes) }
    }
}
