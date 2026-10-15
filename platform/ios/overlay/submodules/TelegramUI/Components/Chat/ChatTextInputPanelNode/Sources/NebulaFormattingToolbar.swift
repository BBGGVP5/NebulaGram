import UIKit

/// Native editor actions are supplied by the input panel; this view never rewrites a draft.
final class NebulaFormattingToolbar: UIView {
    var action: ((Int) -> Void)?
    private let background = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterial))
    private let scroll = UIScrollView()
    private let stack = UIStackView()
    private var labelsLanguage = ""
    private let symbols = ["bold", "italic", "textformat.abc", "text.quote", "eye.slash", "link", "textformat"]

    override init(frame: CGRect) {
        super.init(frame: frame)
        background.clipsToBounds = true
        background.layer.cornerRadius = 16
        addSubview(background)
        scroll.showsHorizontalScrollIndicator = false
        scroll.delaysContentTouches = false
        addSubview(scroll)
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.spacing = 6
        scroll.addSubview(stack)
        for index in [6, 3, 4, 0, 1, 2, 5] {
            let symbol = symbols[index]
            let button = UIButton(type: .system)
            button.tag = index
            button.layer.cornerRadius = 10
            button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .medium)), for: .normal)
            button.addTarget(self, action: #selector(pressed(_:)), for: .touchUpInside)
            stack.addArrangedSubview(button)
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func update(color: UIColor, russian: Bool, hasSelection: Bool) {
        tintColor = color
        background.isHidden = UIAccessibility.isReduceTransparencyEnabled
        backgroundColor = UIAccessibility.isReduceTransparencyEnabled ? .secondarySystemBackground : .clear
        layer.cornerRadius = 16
        let language = russian ? "ru" : "en"
        if labelsLanguage != language {
            labelsLanguage = language
            let titles = russian ? ["Жирный", "Курсив", "Моноширинный", "Цитата", "Спойлер", "Ссылка", "Убрать форматирование"]
                : ["Bold", "Italic", "Monospace", "Quote", "Spoiler", "Link", "Clear formatting"]
            for case let button as UIButton in stack.arrangedSubviews { button.accessibilityLabel = titles[button.tag] }
        }
        for case let button as UIButton in stack.arrangedSubviews {
            button.isEnabled = hasSelection
            button.backgroundColor = color.withAlphaComponent(0.08)
        }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        background.frame = bounds
        scroll.frame = bounds.insetBy(dx: 6, dy: 4)
        let width = max(scroll.bounds.width, CGFloat(symbols.count) * 44 + CGFloat(symbols.count - 1) * 6)
        stack.frame = CGRect(x: 0, y: 0, width: width, height: scroll.bounds.height)
        scroll.contentSize = stack.bounds.size
    }
    @objc private func pressed(_ sender: UIButton) { action?(sender.tag) }
}
