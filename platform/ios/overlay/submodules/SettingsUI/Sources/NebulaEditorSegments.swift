import UIKit

/// The material moves continuously under a horizontal drag. Selection commits
/// on release, so scrubbing never starts network work or swaps the source text.
final class NebulaEditorSegments: UIView, UIGestureRecognizerDelegate {
    var changed: ((Int) -> Void)?
    private var selected = 0
    private var position: CGFloat = 0
    private var motion: UIViewPropertyAnimator?
    private let lens = UIVisualEffectView()
    private let buttons: [UIButton]
    private let accent: UIColor
    init(titles: [String], accent: UIColor, selected: Int = 0) {
        self.accent = accent; self.selected = selected; self.position = CGFloat(selected)
        buttons = titles.map { title in let b = UIButton(type: .system); b.setTitle(title, for: .normal); return b }
        super.init(frame: .zero)
        backgroundColor = UIColor.secondarySystemBackground.withAlphaComponent(0.32)
        layer.cornerRadius = 28
        if #available(iOS 26.0, *), !UIAccessibility.isReduceTransparencyEnabled {
            let effect = UIGlassEffect(style: .regular); effect.isInteractive = !UIAccessibility.isReduceMotionEnabled; lens.effect = effect
        } else { lens.effect = UIBlurEffect(style: .systemThinMaterial) }
        lens.contentView.backgroundColor = accent.withAlphaComponent(0.14)
        lens.isUserInteractionEnabled = false; lens.layer.cornerRadius = 24; lens.clipsToBounds = true
        addSubview(lens)
        for (index, button) in buttons.enumerated() {
            button.tag = index; button.titleLabel?.font = .preferredFont(forTextStyle: .subheadline)
            button.titleLabel?.adjustsFontForContentSizeCategory = true; button.titleLabel?.numberOfLines = 2
            button.titleLabel?.textAlignment = .center; button.tintColor = accent
            button.addTarget(self, action: #selector(tapped(_:)), for: .touchUpInside); addSubview(button)
        }
        let pan = UIPanGestureRecognizer(target: self, action: #selector(dragged(_:))); pan.delegate = self; addGestureRecognizer(pan)
        updateAccessibility()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override var intrinsicContentSize: CGSize { CGSize(width: UIView.noIntrinsicMetric, height: max(64, UIFont.preferredFont(forTextStyle: .subheadline).lineHeight * 2 + 24)) }
    override func layoutSubviews() {
        super.layoutSubviews()
        let width = max(0, bounds.width - 8) / CGFloat(max(1, buttons.count))
        for (index, button) in buttons.enumerated() {
            let visual = effectiveUserInterfaceLayoutDirection == .rightToLeft ? buttons.count - 1 - index : index
            button.frame = CGRect(x: 4 + CGFloat(visual) * width, y: 4, width: width, height: bounds.height - 8)
        }
        layoutLens()
    }
    private func layoutLens() {
        let width = max(0, bounds.width - 8) / CGFloat(max(1, buttons.count))
        let visual = effectiveUserInterfaceLayoutDirection == .rightToLeft ? CGFloat(buttons.count - 1) - position : position
        lens.frame = CGRect(x: 4 + visual * width, y: 4, width: width, height: bounds.height - 8)
    }
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let pan = gestureRecognizer as? UIPanGestureRecognizer else { return true }
        let v = pan.velocity(in: self); return abs(v.x) > abs(v.y)
    }
    @objc private func tapped(_ button: UIButton) { settle(button.tag) }
    @objc private func dragged(_ pan: UIPanGestureRecognizer) {
        if pan.state == .began { motion?.stopAnimation(true) }
        if pan.state == .began || pan.state == .changed || pan.state == .ended {
            let width = max(1, bounds.width - 8) / CGFloat(max(1, buttons.count))
            let value = min(CGFloat(buttons.count - 1), max(0, (pan.location(in: self).x - 4) / width - 0.5))
            position = effectiveUserInterfaceLayoutDirection == .rightToLeft ? CGFloat(buttons.count - 1) - value : value
            layoutLens()
        }
        if pan.state == .ended { settle(Int(position.rounded())) }
        else if pan.state == .cancelled || pan.state == .failed { settle(selected) }
    }
    private func settle(_ index: Int) {
        let previous = selected; selected = index; updateAccessibility()
        motion?.stopAnimation(true)
        let update = { self.position = CGFloat(index); self.layoutLens() }
        if UIAccessibility.isReduceMotionEnabled { update() }
        else { let animation = UIViewPropertyAnimator(duration: 0.26, dampingRatio: 0.82, animations: update); motion = animation; animation.startAnimation() }
        if previous != index { changed?(index) }
    }
    private func updateAccessibility() {
        for (index, button) in buttons.enumerated() { button.accessibilityTraits = index == selected ? [.button, .selected] : .button }
    }
}
