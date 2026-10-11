import UIKit

/// A selectable, naturally sized user message; the row owns trailing alignment.
final class NebulaAiChatBubble: UIView {
    private let text = UITextView()
    private var preferredWidth: NSLayoutConstraint!
    init(value: String, foreground: UIColor, surface: UIColor) {
        super.init(frame: .zero)
        text.text = value; text.font = .preferredFont(forTextStyle: .body); text.adjustsFontForContentSizeCategory = true
        text.isEditable = false; text.isSelectable = true; text.isScrollEnabled = false
        text.textColor = foreground; text.backgroundColor = surface; text.layer.cornerRadius = 22
        text.textContainerInset = UIEdgeInsets(top: 12, left: 14, bottom: 12, right: 14)
        text.textContainer.lineFragmentPadding = 0; text.translatesAutoresizingMaskIntoConstraints = false; addSubview(text)
        preferredWidth = text.widthAnchor.constraint(equalToConstant: 80); preferredWidth.priority = .defaultHigh
        NSLayoutConstraint.activate([text.topAnchor.constraint(equalTo: topAnchor), text.bottomAnchor.constraint(equalTo: bottomAnchor),
            text.trailingAnchor.constraint(equalTo: trailingAnchor), text.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor),
            text.widthAnchor.constraint(lessThanOrEqualTo: widthAnchor, multiplier: 0.84), preferredWidth])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }
        let maximum = bounds.width * 0.84
        let measured = (text.text as NSString).boundingRect(with: CGSize(width: max(1, maximum - 28), height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: [.font: text.font ?? UIFont.preferredFont(forTextStyle: .body)], context: nil)
        let width = min(maximum, max(52, ceil(measured.width) + 28))
        if abs(preferredWidth.constant - width) > 0.5 { preferredWidth.constant = width }
    }
}
