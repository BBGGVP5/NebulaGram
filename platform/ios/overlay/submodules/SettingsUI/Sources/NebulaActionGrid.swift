import UIKit

/// Equal tiles, at most three per row. A partial final row stays centered.
final class NebulaActionGrid: UIView {
    private let buttons: [UIButton]
    private var height: NSLayoutConstraint!
    init(buttons: [UIButton]) {
        self.buttons = buttons
        super.init(frame: .zero)
        for button in buttons {
            button.titleLabel?.font = .preferredFont(forTextStyle: .subheadline)
            button.titleLabel?.adjustsFontForContentSizeCategory = true
            button.titleLabel?.numberOfLines = 2
            button.titleLabel?.textAlignment = .center
            button.layer.cornerRadius = 18
            addSubview(button)
        }
        height = heightAnchor.constraint(equalToConstant: 200)
        height.isActive = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, !buttons.isEmpty else { return }
        let gap: CGFloat = 10
        let font = UIFont.preferredFont(forTextStyle: .subheadline)
        let required = max(96, buttons.map { (($0.title(for: .normal) ?? "") as NSString).size(withAttributes: [.font: font]).width + 24 }.max() ?? 96)
        let columns = min(3, max(1, Int((bounds.width + gap) / (required + gap))))
        let width = (bounds.width - gap * CGFloat(columns - 1)) / CGFloat(columns)
        let rowHeight = max(90, font.lineHeight * 2 + 46)
        let rows = (buttons.count + columns - 1) / columns
        let total = CGFloat(rows) * rowHeight + CGFloat(rows - 1) * gap
        if abs(height.constant - total) > 0.5 { height.constant = total }
        for (index, button) in buttons.enumerated() {
            let row = index / columns
            let count = min(columns, buttons.count - row * columns)
            let rowWidth = CGFloat(count) * width + CGFloat(count - 1) * gap
            var column = index % columns
            if effectiveUserInterfaceLayoutDirection == .rightToLeft { column = count - 1 - column }
            button.frame = CGRect(x: (bounds.width - rowWidth) / 2 + CGFloat(column) * (width + gap), y: CGFloat(row) * (rowHeight + gap), width: width, height: rowHeight)
        }
    }
}
