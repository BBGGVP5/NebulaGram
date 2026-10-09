import UIKit
import NebulaSettingsContract

/// A drawing-only hint in the free space after the last glyph. Never writes to input state.
final class NebulaArithmeticHint: UILabel {
    init() { super.init(frame: .zero); isUserInteractionEnabled = false; isHidden = true; accessibilityTraits = .staticText }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(input: UIView?, container: UIView, color: UIColor) {
        isHidden = true
        guard NebulaSettingsStore.shared.inlineArithmetic, let input, let textView = findTextView(input),
              textView.markedTextRange == nil, let source = textView.text, let result = NebulaArithmetic.result(source) else { return }
        if superview !== container { container.addSubview(self) }
        text = (source.trimmingCharacters(in: .whitespacesAndNewlines).hasSuffix("=") ? " " : " = ") + result
        font = textView.font ?? .preferredFont(forTextStyle: .body); textColor = color
        let caret = textView.caretRect(for: textView.endOfDocument)
        let origin = textView.convert(CGPoint(x: caret.maxX + 4, y: caret.minY), to: container)
        let size = sizeThatFits(container.bounds.size)
        let candidate = CGRect(origin: origin, size: CGSize(width: size.width, height: max(caret.height, size.height)))
        let visibleInput = textView.convert(textView.bounds.inset(by: UIEdgeInsets(top: 0, left: 0, bottom: textView.textContainerInset.bottom, right: textView.textContainerInset.right)), to: container).intersection(container.bounds)
        guard !candidate.isEmpty, visibleInput.contains(candidate) else { return }
        frame = candidate; isHidden = false; container.bringSubviewToFront(self)
    }
    private func findTextView(_ view: UIView) -> UITextView? {
        if let textView = view as? UITextView { return textView }
        for child in view.subviews { if let result = findTextView(child) { return result } }
        return nil
    }
}
