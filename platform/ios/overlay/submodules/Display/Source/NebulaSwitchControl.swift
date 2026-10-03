import UIKit
import NebulaSettingsContract

@objc public protocol NebulaSwitchCompatible: AnyObject {
    var isOn: Bool { get set }
    func setOn(_ on: Bool, animated: Bool)
}

extension UISwitch: NebulaSwitchCompatible { }
public typealias NebulaSwitchView = UIControl & NebulaSwitchCompatible

/// Keeps the native switch for style zero and uses themed geometry for the alternatives.
public final class NebulaSwitchControl: UIControl, NebulaSwitchCompatible {
    private let native = UISwitch()
    private let track = CALayer()
    private let thumb = CALayer()
    private var observation: SettingsObservation?
    private var style = NebulaSettingsStore.shared.switchStyle
    private var value = false
    private var touchStart: CGFloat?
    private var dragValue: Bool?
    public var isOn: Bool {
        get { value }
        set { setOn(newValue, animated: false) }
    }
    public var onTintColor: UIColor? { didSet { native.onTintColor = onTintColor; updateAppearance(animated: false) } }
    public var thumbTintColor: UIColor? { didSet { native.thumbTintColor = thumbTintColor; updateAppearance(animated: false) } }
    public override var isEnabled: Bool { didSet { native.isEnabled = isEnabled; alpha = isEnabled ? 1.0 : 0.5 } }

    public override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(native)
        layer.addSublayer(track)
        layer.addSublayer(thumb)
        native.addTarget(self, action: #selector(nativeChanged), for: .valueChanged)
        native.isAccessibilityElement = false
        isAccessibilityElement = true
        observation = NebulaSettingsStore.shared.observe { [weak self] in
            guard let self else { return }
            let next = NebulaSettingsStore.shared.switchStyle
            guard next != self.style else { return }
            self.style = next
            self.invalidateIntrinsicContentSize()
            self.setNeedsLayout()
            self.updateAppearance(animated: false)
        }
        updateAppearance(animated: false)
    }
    public convenience init() { self.init(frame: .zero); sizeToFit() }
    public required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    public override var intrinsicContentSize: CGSize {
        style == 0 ? native.intrinsicContentSize : CGSize(width: 52, height: 32)
    }
    public override func sizeThatFits(_ size: CGSize) -> CGSize { intrinsicContentSize }
    public override func layoutSubviews() {
        super.layoutSubviews()
        native.frame = CGRect(origin: .zero, size: native.intrinsicContentSize)
        updateAppearance(animated: false)
    }
    public override func tintColorDidChange() {
        super.tintColorDidChange()
        native.tintColor = tintColor
        updateAppearance(animated: false)
    }
    public func setOn(_ on: Bool, animated: Bool) {
        guard value != on else { return }
        value = on
        native.setOn(on, animated: animated)
        updateAppearance(animated: animated)
    }
    @objc private func nativeChanged() {
        value = native.isOn
        updateAppearance(animated: false)
        sendActions(for: .valueChanged)
    }
    public override func accessibilityActivate() -> Bool {
        guard isEnabled else { return false }
        setOn(!value, animated: !UIAccessibility.isReduceMotionEnabled)
        sendActions(for: .valueChanged)
        return true
    }
    public override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        bounds.insetBy(dx: min(0, (bounds.width - 44) / 2), dy: min(0, (bounds.height - 44) / 2)).contains(point)
    }
    public override func beginTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        guard isEnabled && style != 0 else { return false }
        touchStart = touch.location(in: self).x
        dragValue = nil
        return true
    }
    public override func continueTracking(_ touch: UITouch, with event: UIEvent?) -> Bool {
        guard let start = touchStart else { return false }
        let x = touch.location(in: self).x
        if abs(x - start) > 6 {
            dragValue = effectiveUserInterfaceLayoutDirection == .rightToLeft ? x < bounds.midX : x > bounds.midX
            updateAppearance(animated: true)
        }
        return true
    }
    public override func endTracking(_ touch: UITouch?, with event: UIEvent?) {
        let next = dragValue ?? !value
        touchStart = nil
        dragValue = nil
        let changed = value != next
        setOn(next, animated: true)
        updateAppearance(animated: true)
        if changed { sendActions(for: .valueChanged) }
    }
    public override func cancelTracking(with event: UIEvent?) {
        touchStart = nil
        dragValue = nil
        updateAppearance(animated: true)
    }
    private func updateAppearance(animated: Bool) {
        native.isHidden = style != 0
        track.isHidden = style == 0
        thumb.isHidden = style == 0
        accessibilityTraits = value ? [.button, .selected] : [.button]
        accessibilityValue = native.accessibilityValue
        guard style != 0 else { return }
        let displayedValue = dragValue ?? value
        let accent = onTintColor ?? tintColor ?? .systemBlue
        let off = tintColor ?? UIColor.secondaryLabel.withAlphaComponent(0.3)
        let height: CGFloat = style == 1 ? 32 : style == 2 ? 28 : 18
        let width: CGFloat = style == 3 ? 44 : 52
        let radius: CGFloat = style == 2 ? 11 : 13
        let onRight = effectiveUserInterfaceLayoutDirection == .rightToLeft ? !displayedValue : displayedValue
        let cx: CGFloat = onRight ? 52 - radius - 3 : radius + 3
        let offsetX = max(0, (bounds.width - 52) / 2)
        let offsetY = (bounds.height - 32) / 2
        CATransaction.begin()
        CATransaction.setDisableActions(!animated || UIAccessibility.isReduceMotionEnabled)
        CATransaction.setAnimationDuration(style == 1 ? 0.3 : style == 2 ? 0.24 : 0.18)
        CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(name: style == 2 ? .easeInEaseOut : .easeOut))
        track.frame = CGRect(x: offsetX + (52 - width) / 2, y: offsetY + (32 - height) / 2, width: width, height: height)
        track.cornerRadius = height / 2
        track.backgroundColor = (displayedValue ? accent.withAlphaComponent(style == 3 ? 0.5 : 1) : off).cgColor
        thumb.frame = CGRect(x: offsetX + cx - radius, y: offsetY + 16 - radius, width: radius * 2, height: radius * 2)
        thumb.cornerRadius = radius
        thumb.backgroundColor = (style == 3 ? (displayedValue ? accent : .secondaryLabel) : (thumbTintColor ?? .white)).cgColor
        thumb.shadowColor = UIColor.black.cgColor
        thumb.shadowOpacity = 0.12
        thumb.shadowRadius = 0.8
        thumb.shadowOffset = CGSize(width: 0, height: 0.6)
        CATransaction.commit()
    }
}
