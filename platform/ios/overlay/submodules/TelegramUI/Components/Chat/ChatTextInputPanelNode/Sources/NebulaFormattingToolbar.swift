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

/// Native editor actions are supplied by the input panel; this view never rewrites a draft.
final class NebulaFormattingToolbar: UIView {
    var action: ((Int) -> Void)?
    private let background = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterial))
    private let scroll = UIScrollView()
    private let stack = UIStackView()
    private var labelsLanguage = ""
    private var tileColor: UIColor?
    private let symbols = ["bold", "italic", "m.square", "text.quote", "eye.slash", "link", "pencil.slash", "return", "underline", "strikethrough", "chevron.left.forwardslash.chevron.right"]

    override init(frame: CGRect) {
        super.init(frame: frame)
        background.clipsToBounds = true
        background.layer.cornerRadius = 18
        addSubview(background)
        scroll.showsHorizontalScrollIndicator = false
        scroll.delaysContentTouches = false
        addSubview(scroll)
        stack.axis = .horizontal
        stack.distribution = .fill
        stack.spacing = 7
        scroll.addSubview(stack)
        for index in [7, 6, 3, 4, 0, 1, 2, 5, 8, 9, 10] {
            let symbol = symbols[index]
            let button = UIButton(type: .system)
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
            if index == 6 { stack.setCustomSpacing(19, after: button) }
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
        scroll.frame = bounds.insetBy(dx: 8, dy: 0)
        let width = CGFloat(symbols.count) * 44 + CGFloat(symbols.count - 1) * 7 + 12
        stack.frame = CGRect(x: 0, y: 0, width: width, height: scroll.bounds.height)
        scroll.contentSize = stack.bounds.size
    }
    @objc private func pressed(_ sender: UIButton) { action?(sender.tag) }
}
