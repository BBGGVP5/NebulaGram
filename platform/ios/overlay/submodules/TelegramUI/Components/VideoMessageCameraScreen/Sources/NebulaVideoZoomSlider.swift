import UIKit
import AVFoundation

/// A compact 1×/2× control that opens into a scrollable ruler while recording.
final class NebulaVideoZoomSlider: UIView {
    var onZoomChanged: ((CGFloat) -> Void)?

    private let capsule = UIView()
    private let material = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialDark))
    private let compact = CompactView()
    private let ruler = RulerView()
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
        setRange(front: false)
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
        let width = expanded ? bounds.width - 4 : min(bounds.width - 4, 156)
        capsule.frame = CGRect(x: (bounds.width - width) / 2, y: 3, width: width, height: bounds.height - 6)
        material.frame = capsule.bounds
        compact.frame = capsule.bounds
        ruler.frame = capsule.bounds
        layer.shadowPath = UIBezierPath(roundedRect: capsule.frame, cornerRadius: 25).cgPath
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        capsule.frame.insetBy(dx: -8, dy: 0).contains(point)
    }

    static func availableMaximum(front: Bool) -> CGFloat {
        let position: AVCaptureDevice.Position = front ? .front : .back
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else { return 2 }
        return max(1.05, min(8, device.maxAvailableVideoZoomFactor))
    }

    func setRange(front: Bool) {
        maximum = Self.availableMaximum(front: front)
        ruler.maximum = maximum
        compact.maximum = maximum
        updateZoom(1, notify: false)
        setExpanded(false, animated: false)
    }

    func setCurrent(_ factor: CGFloat) { updateZoom(factor, notify: false) }

    func reflectPinch(_ scale: CGFloat) {
        updateZoom(current * scale, notify: false)
        setExpanded(true)
        scheduleCollapse()
    }

    private func updateZoom(_ factor: CGFloat, notify: Bool) {
        current = max(1, min(maximum, factor))
        ruler.current = current
        compact.current = current
        accessibilityValue = String(format: "%.1f×", Double(current))
        if notify { onZoomChanged?(current) }
    }

    private var pixelsPerOctave: CGFloat {
        max(1, ((bounds.width - 4) / 2 - 27) / CGFloat(log2(Double(maximum))))
    }

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
        if !expanded {
            let work = DispatchWorkItem { [weak self] in self?.setExpanded(true) }
            expandWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: work)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, let start = dragStartX else { return }
        let x = touch.location(in: self).x
        if abs(x - start) > 6 { dragged = true }
        if dragged {
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
                updateZoom(x < bounds.midX ? 1 : min(2, maximum), notify: true)
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
        var maximum: CGFloat = 2 { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemTeal { didSet { setNeedsDisplay() } }

        override func draw(_ rect: CGRect) {
            let half = bounds.width / 2
            let active = current < min(1.5, maximum) ? CGRect(x: 3, y: 3, width: half - 6, height: bounds.height - 6)
                : CGRect(x: half + 3, y: 3, width: half - 6, height: bounds.height - 6)
            accent.setFill()
            UIBezierPath(roundedRect: active, cornerRadius: active.height / 2).fill()
            let style: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            let labels = ["1×", maximum >= 1.95 ? "2×" : String(format: "%.1f×", Double(maximum))]
            for (index, label) in labels.enumerated() {
                let text = label as NSString
                let size = text.size(withAttributes: style)
                text.draw(at: CGPoint(x: half * (CGFloat(index) + 0.5) - size.width / 2,
                                      y: (bounds.height - size.height) / 2), withAttributes: style)
            }
        }
    }

    private final class RulerView: UIView {
        var maximum: CGFloat = 2 { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemTeal { didSet { setNeedsDisplay() } }

        private var pixelsPerOctave: CGFloat {
            max(1, (bounds.width / 2 - 25) / CGFloat(log2(Double(maximum))))
        }

        private func x(for zoom: CGFloat) -> CGFloat {
            bounds.midX + CGFloat(log2(Double(zoom / current))) * pixelsPerOctave
        }

        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            let center = bounds.midX
            let middle = bounds.midY
            context.saveGState()
            context.clip(to: bounds.insetBy(dx: 15, dy: 3))
            let ticks = Int(ceil(log2(Double(maximum)) * 5))
            for index in 0...ticks {
                let zoom = min(maximum, CGFloat(pow(2.0, Double(index) / 5.0)))
                let position = x(for: zoom)
                if position < 16 || position > bounds.width - 16 { continue }
                let major = index % 5 == 0 || index == ticks
                context.setStrokeColor((major ? accent : UIColor(white: 0.85, alpha: 0.62)).cgColor)
                context.setLineWidth(major ? 2 : 1)
                context.move(to: CGPoint(x: position, y: middle - 13))
                context.addLine(to: CGPoint(x: position, y: middle + (major ? 5 : 1)))
                context.strokePath()
            }
            let style: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedDigitSystemFont(ofSize: 11, weight: .bold),
                .foregroundColor: accent
            ]
            var marks: [CGFloat] = [1]
            if maximum >= 1.95 { marks.append(2) }
            if maximum >= 5 { marks.append(5) }
            if maximum >= 7.95 { marks.append(8) }
            else if maximum < 1.95 || maximum > 2.4 && abs(maximum - 5) > 0.3 { marks.append(maximum) }
            for mark in marks {
                let label = (abs(mark.rounded() - mark) < 0.05
                    ? String(format: "%.0f", Double(mark))
                    : String(format: "%.1f", Double(mark))) as NSString
                let position = x(for: mark)
                if position >= 20 && position <= bounds.width - 20 {
                    label.draw(at: CGPoint(x: position - label.size(withAttributes: style).width / 2,
                                           y: middle + 7), withAttributes: style)
                }
            }
            context.restoreGState()
            context.setStrokeColor(accent.cgColor)
            context.setLineWidth(5)
            context.setLineCap(.round)
            context.move(to: CGPoint(x: center, y: middle - 16))
            context.addLine(to: CGPoint(x: center, y: middle + 5))
            context.strokePath()
        }
    }
}
