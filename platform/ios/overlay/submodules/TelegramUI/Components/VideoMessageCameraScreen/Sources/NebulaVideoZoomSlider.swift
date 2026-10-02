import UIKit

/// Active-session optical stops and a bounded logarithmic ruler.
final class NebulaVideoZoomSlider: UIView {
    var onZoomChanged: ((CGFloat) -> Void)?

    private let capsule = UIView()
    private let material = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialDark))
    private let compact = CompactView()
    private let ruler = RulerView()
    private var minimum: CGFloat = 1
    private var stops: [CGFloat] = [1]
    private var maximum: CGFloat = 2
    private var current: CGFloat = 1
    private var expanded = false
    private var collapseWork: DispatchWorkItem?
    private var expandWork: DispatchWorkItem?
    private var dragStartX: CGFloat?
    private var dragStartZoom: CGFloat = 1
    private var dragged = false
    private var beganExpanded = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityLabel = Locale.current.languageCode == "ru" ? "Увеличение видео" : "Video zoom"
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.35
        layer.shadowRadius = 8
        layer.shadowOffset = CGSize(width: 0, height: 3)
        capsule.layer.cornerRadius = 25
        capsule.layer.cornerCurve = .continuous
        capsule.layer.borderWidth = 0.8
        capsule.layer.borderColor = UIColor(white: 1, alpha: 0.35).cgColor
        capsule.clipsToBounds = true
        capsule.isUserInteractionEnabled = false
        addSubview(capsule)
        capsule.addSubview(material)
        capsule.addSubview(compact)
        capsule.addSubview(ruler)
        ruler.alpha = 0
        compact.accent = tintColor
        ruler.accent = tintColor
        setRange(minimum: 1, maximum: 1, stops: [1], current: 1)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func tintColorDidChange() {
        super.tintColorDidChange()
        compact.accent = tintColor
        ruler.accent = tintColor
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layoutCapsule()
    }

    private func layoutCapsule() {
        let width = expanded ? min(280, bounds.width) : min(bounds.width, CGFloat(stops.count) * 48 + 8)
        capsule.frame = CGRect(x: (bounds.width - width) / 2, y: 2, width: width, height: 48)
        material.frame = capsule.bounds
        compact.frame = capsule.bounds
        ruler.frame = capsule.bounds
        layer.shadowPath = UIBezierPath(roundedRect: capsule.frame, cornerRadius: 25).cgPath
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        capsule.frame.insetBy(dx: -4, dy: -4).contains(point)
    }

    func setColors(background: UIColor, foreground: UIColor, selected: UIColor) {
        capsule.backgroundColor = background.withAlphaComponent(0.88)
        material.effect = UIBlurEffect(style: .systemMaterial)
        compact.foreground = foreground
        compact.selectedForeground = selected
        ruler.foreground = foreground
        capsule.layer.borderColor = foreground.withAlphaComponent(0.18).cgColor
    }

    func setRange(minimum: CGFloat, maximum: CGFloat, stops: [CGFloat], current: CGFloat) {
        guard minimum.isFinite, maximum.isFinite, minimum > 0, maximum >= minimum else { return }
        self.minimum = minimum
        self.maximum = maximum
        var values: [CGFloat] = []
        for value in ([minimum, 1, 2] + stops + [maximum]).sorted() where value >= minimum && value <= maximum {
            if values.last.map({ abs($0 - value) > 0.04 }) ?? true { values.append(value) }
        }
        self.stops = values
        ruler.minimum = minimum
        ruler.maximum = maximum
        ruler.marks = values
        compact.values = values
        updateZoom(current, notify: false)
        setExpanded(false, animated: false)
        setNeedsLayout()
    }

    func setCurrent(_ factor: CGFloat) { updateZoom(factor, notify: false) }

    func reflectPinch(_ scale: CGFloat) {
        updateZoom(current * scale, notify: true)
        setExpanded(true)
        scheduleCollapse()
    }

    private func updateZoom(_ factor: CGFloat, notify: Bool) {
        guard factor.isFinite else { return }
        current = max(minimum, min(maximum, factor))
        ruler.current = current
        compact.current = current
        accessibilityValue = String(format: "%.1f×", Double(current))
        if notify { onZoomChanged?(current) }
    }

    private var pixelsPerOctave: CGFloat { 88 }

    private func setExpanded(_ value: Bool, animated: Bool = true) {
        collapseWork?.cancel()
        guard expanded != value else { return }
        expanded = value
        let changes = {
            self.layoutCapsule()
            self.compact.alpha = value ? 0 : 1
            self.ruler.alpha = value ? 1 : 0
        }
        if animated && !UIAccessibility.isReduceMotionEnabled {
            UIView.animate(withDuration: 0.18, delay: 0, options: [.beginFromCurrentState, .allowUserInteraction], animations: changes)
        } else {
            changes()
        }
    }

    private func scheduleCollapse() {
        collapseWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.setExpanded(false) }
        collapseWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4, execute: work)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        collapseWork?.cancel()
        dragStartX = touch.location(in: self).x
        dragStartZoom = current
        dragged = false
        beganExpanded = expanded
        if !expanded && capsule.frame.contains(touch.location(in: self)) {
            let work = DispatchWorkItem { [weak self] in self?.setExpanded(true) }
            expandWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: work)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, let start = dragStartX else { return }
        let x = touch.location(in: self).x
        if abs(x - start) > 6 { dragged = true }
        if dragged && capsule.frame.contains(CGPoint(x: start, y: bounds.midY)) {
            expandWork?.cancel()
            setExpanded(true)
            updateZoom(dragStartZoom * CGFloat(pow(2.0, Double((start - x) / pixelsPerOctave))), notify: true)
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        expandWork?.cancel()
        if !dragged, let touch = touches.first {
            let x = touch.location(in: self).x
            if beganExpanded {
                updateZoom(current * CGFloat(pow(2.0, Double((x - bounds.midX) / pixelsPerOctave))), notify: true)
            } else if !expanded {
                let index = min(stops.count - 1, max(0, Int((x - capsule.frame.minX) * CGFloat(stops.count) / capsule.frame.width)))
                updateZoom(stops[index], notify: true)
            }
        }
        dragStartX = nil
        scheduleCollapse()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        expandWork?.cancel()
        dragStartX = nil
        scheduleCollapse()
    }

    private final class CompactView: UIView {
        var selectedForeground: UIColor = .white { didSet { setNeedsDisplay() } }
        var values: [CGFloat] = [1] { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemBlue { didSet { setNeedsDisplay() } }
        var foreground: UIColor = .label { didSet { setNeedsDisplay() } }

        override func draw(_ rect: CGRect) {
            let segment = bounds.width / CGFloat(values.count)
            let selected = values.indices.min(by: { abs(values[$0] - current) < abs(values[$1] - current) }) ?? 0
            let diameter = min(bounds.height - 6, segment - 4)
            let active = CGRect(x: segment * (CGFloat(selected) + 0.5) - diameter / 2,
                                y: (bounds.height - diameter) / 2, width: diameter, height: diameter)
            accent.setFill()
            UIBezierPath(roundedRect: active, cornerRadius: active.height / 2).fill()
            var style: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .bold),
                .foregroundColor: foreground
            ]
            for (index, factor) in values.enumerated() {
                style[.foregroundColor] = index == selected ? selectedForeground : foreground
                let shown = factor
                let label = abs(shown - shown.rounded()) < 0.04 ? String(format: "%.0f×", Double(shown)) : String(format: "%.1f×", Double(shown))
                let text = label as NSString
                let size = text.size(withAttributes: style)
                text.draw(at: CGPoint(x: segment * (CGFloat(index) + 0.5) - size.width / 2,
                                      y: (bounds.height - size.height) / 2), withAttributes: style)
            }
        }
    }

    private final class RulerView: UIView {
        var minimum: CGFloat = 1
        var marks: [CGFloat] = [1]
        var maximum: CGFloat = 2 { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemBlue { didSet { setNeedsDisplay() } }
        var foreground: UIColor = .label { didSet { setNeedsDisplay() } }

        private var pixelsPerOctave: CGFloat {
            88
        }

        private func x(for zoom: CGFloat) -> CGFloat {
            bounds.midX + CGFloat(log2(Double(zoom / current))) * pixelsPerOctave
        }

        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            let center = bounds.midX
            context.saveGState()
            context.clip(to: bounds.insetBy(dx: 15, dy: 3))
            let ticks = Int(ceil(log2(Double(maximum / minimum)) * 8))
            for index in 0...ticks {
                let zoom = min(maximum, minimum * CGFloat(pow(2.0, Double(index) / 8.0)))
                let position = x(for: zoom)
                if position < 16 || position > bounds.width - 16 { continue }
                let major = false // Labeled stops are drawn at their exact positions below.
                context.setStrokeColor((major ? accent : foreground.withAlphaComponent(0.62)).cgColor)
                context.setLineWidth(major ? 2 : 1)
                context.move(to: CGPoint(x: position, y: 9))
                context.addLine(to: CGPoint(x: position, y: 23))
                context.strokePath()
            }
            let style: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedDigitSystemFont(ofSize: 11, weight: .bold),
                .foregroundColor: accent
            ]
            for mark in marks {
                let label = (abs(mark.rounded() - mark) < 0.05
                    ? String(format: "%.0f", Double(mark))
                    : String(format: "%.1f", Double(mark))) as NSString
                let position = x(for: mark)
                if position >= 20 && position <= bounds.width - 20 {
                    context.setStrokeColor(accent.cgColor)
                    context.setLineWidth(2)
                    context.move(to: CGPoint(x: position, y: 8))
                    context.addLine(to: CGPoint(x: position, y: 25))
                    context.strokePath()
                    label.draw(at: CGPoint(x: position - label.size(withAttributes: style).width / 2,
                                           y: 31), withAttributes: style)
                }
            }
            context.restoreGState()
            context.setStrokeColor(accent.cgColor)
            context.setLineWidth(5)
            context.setLineCap(.round)
            context.move(to: CGPoint(x: center, y: 7))
            context.addLine(to: CGPoint(x: center, y: 25))
            context.strokePath()
        }
    }
}
