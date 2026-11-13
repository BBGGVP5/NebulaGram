import UIKit
import NebulaSettingsContract

/// A drawing-only hint in the free space after the last glyph. Never writes to input state.
final class NebulaArithmeticHint: UILabel {
    private weak var lastInput: UIView?
    private weak var lastContainer: UIView?
    private var lastColor: UIColor = .gray

    init() {
        super.init(frame: .zero); isUserInteractionEnabled = false; isHidden = true; accessibilityTraits = .staticText
        NotificationCenter.default.addObserver(self, selector: #selector(ratesUpdated), name: NebulaInlineRates.didUpdate, object: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(input: UIView?, container: UIView, color: UIColor) {
        isHidden = true
        lastInput = input; lastContainer = container; lastColor = color
        let store = NebulaSettingsStore.shared
        guard store.inlineArithmetic || store.inlineUnits || store.inlineCurrency || store.inlineTimezones, let input, let textView = findTextView(input),
              textView.markedTextRange == nil, let source = textView.text else { return }
        guard let result = Self.result(source, store: store) else { return }
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

    /// Android `NebulaInlineResults.compute` order: arithmetic, units with a named target,
    /// currency, time zones, then units moved to the other measurement system.
    private static func result(_ source: String, store: NebulaSettingsStore) -> String? {
        if store.inlineArithmetic, let value = NebulaArithmetic.result(source) { return value }
        guard let body = NebulaInlineUnits.question(source) else { return nil }
        let russian = Locale.preferredLanguages.first?.hasPrefix("ru") == true
        if store.inlineUnits, let value = NebulaInlineUnits.explicit(body, russian: russian) { return value }
        if store.inlineCurrency {
            var wanted = false
            let target = store.inlineDefaultCurrency.isEmpty ? ((Locale.current as NSLocale).currencyCode ?? "USD") : store.inlineDefaultCurrency
            if let value = NebulaInlineCurrency.convert(body, rates: NebulaInlineRates.shared.current(), defaultCurrency: target, ratesWanted: &wanted) { return value }
            if wanted { NebulaInlineRates.shared.load(nil) }
        }
        if store.inlineTimezones, let value = NebulaInlineTime.convert(body) { return value }
        if store.inlineUnits {
            let metric = (Locale.current as NSLocale).object(forKey: .usesMetricSystem) as? Bool ?? true
            return NebulaInlineUnits.implicit(body, russian: russian, imperial: !metric)
        }
        return nil
    }

    @objc private func ratesUpdated() {
        guard let container = lastContainer else { return }
        update(input: lastInput, container: container, color: lastColor)
    }

    private func findTextView(_ view: UIView) -> UITextView? {
        if let textView = view as? UITextView { return textView }
        for child in view.subviews { if let result = findTextView(child) { return result } }
        return nil
    }
}
