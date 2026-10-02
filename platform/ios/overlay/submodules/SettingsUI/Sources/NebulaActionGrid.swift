import UIKit
import NebulaSettingsContract

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
        let font = UIFont.preferredFont(forTextStyle: .subheadline)
        let required = max(96, buttons.map { button in
            (button.title(for: .normal) ?? "").split(separator: " ").map { (String($0) as NSString).size(withAttributes: [.font: font]).width + 24 }.max() ?? 96
        }.max() ?? 96)
        let layout = NebulaToolLayout(width: Double(bounds.width), count: buttons.count,
            minimumTileWidth: Double(required), rowHeight: Double(max(96, font.lineHeight * 2 + 62)),
            rtl: effectiveUserInterfaceLayoutDirection == .rightToLeft)
        if abs(height.constant - CGFloat(layout.height)) > 0.5 { height.constant = CGFloat(layout.height) }
        for (button, tile) in zip(buttons, layout.tiles) {
            button.frame = CGRect(x: tile.x, y: tile.y, width: tile.width, height: tile.height)
        }
    }
}
