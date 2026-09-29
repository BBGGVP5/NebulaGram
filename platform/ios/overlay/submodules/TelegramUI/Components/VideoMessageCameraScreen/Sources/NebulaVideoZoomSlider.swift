import UIKit
import AVFoundation

/// Camera-limited zoom ruler shown throughout round-video recording.
final class NebulaVideoZoomSlider: UIView {
    var onZoomChanged: ((CGFloat) -> Void)?

    private let capsule = UIView()
    private let material = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialDark))
    private let ruler = RulerView()
    private var maximum: CGFloat = 2
    private var current: CGFloat = 1

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isAccessibilityElement = true
        accessibilityLabel = Locale.current.languageCode == "ru" ? "Шкала увеличения" : "Zoom ruler"
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.35
        layer.shadowRadius = 8
        layer.shadowOffset = CGSize(width: 0, height: 3)
        capsule.layer.cornerRadius = 25
        capsule.layer.cornerCurve = .continuous
        capsule.layer.borderWidth = 0.8
        capsule.layer.borderColor = UIColor(white: 1, alpha: 0.35).cgColor
        capsule.clipsToBounds = true
        addSubview(capsule)
        capsule.addSubview(material)
        ruler.backgroundColor = .clear
        ruler.contentMode = .redraw
        ruler.accent = tintColor
        ruler.onChange = { [weak self] factor in self?.updateZoom(factor, notify: true) }
        capsule.addSubview(ruler)
        setRange(front: false)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func tintColorDidChange() {
        super.tintColorDidChange()
        ruler.accent = tintColor
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        capsule.frame = bounds.insetBy(dx: 2, dy: 3)
        material.frame = capsule.bounds
        ruler.frame = capsule.bounds
        layer.shadowPath = UIBezierPath(roundedRect: capsule.frame, cornerRadius: 25).cgPath
    }

    static func availableMaximum(front: Bool) -> CGFloat {
        let position: AVCaptureDevice.Position = front ? .front : .back
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else { return 2 }
        return max(1.05, min(8, device.maxAvailableVideoZoomFactor))
    }

    func setRange(front: Bool) {
        maximum = Self.availableMaximum(front: front)
        ruler.maximum = maximum
        updateZoom(1, notify: false)
    }

    func setCurrent(_ factor: CGFloat) { updateZoom(factor, notify: false) }
    func reflectPinch(_ scale: CGFloat) { updateZoom(current * scale, notify: false) }

    private func updateZoom(_ factor: CGFloat, notify: Bool) {
        current = max(1, min(maximum, factor))
        ruler.current = current
        accessibilityValue = String(format: "%.1f×", Double(current))
        if notify { onZoomChanged?(current) }
    }

    private final class RulerView: UIView {
        var maximum: CGFloat = 2 { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemTeal { didSet { setNeedsDisplay() } }
        var onChange: ((CGFloat) -> Void)?
        private var dragStartX: CGFloat?
        private var dragStartZoom: CGFloat = 1
        private var dragged = false

        private var pixelsPerZoom: CGFloat {
            max(1, (bounds.width - 54) / max(0.05, min(2, maximum - 1)))
        }

        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            let center = bounds.midX
            let middle = bounds.midY
            // Move the ruler under a fixed indicator instead of sliding the indicator.
            context.saveGState()
            context.clip(to: bounds.insetBy(dx: 17, dy: 5))
            let ticks = Int(ceil((maximum - 1) * 12))
            for index in 0...ticks {
                let value = min(maximum, 1 + CGFloat(index) / 12)
                let x = center + (value - current) * pixelsPerZoom
                if x < 20 || x > bounds.width - 20 { continue }
                let major = index % 12 == 0 || index == ticks
                context.setStrokeColor((major ? accent : UIColor(white: 0.85, alpha: 0.58)).cgColor)
                context.setLineWidth(major ? 2 : 1)
                context.move(to: CGPoint(x: x, y: middle - 13))
                context.addLine(to: CGPoint(x: x, y: middle + (major ? 5 : 1)))
                context.strokePath()
            }
            let style: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedDigitSystemFont(ofSize: 11, weight: .bold),
                .foregroundColor: accent
            ]
            var marks: [CGFloat] = [1]
            if maximum >= 1.95 { marks.append(2) }
            if maximum >= 5 { marks.append(5) }
            if maximum < 1.95 || maximum > 2.4 && abs(maximum - 5) > 0.3 { marks.append(maximum) }
            for mark in marks {
                let label = (abs(mark.rounded() - mark) < 0.05
                    ? String(format: "%.0f", Double(mark))
                    : String(format: "%.1f", Double(mark))) as NSString
                let x = center + (mark - current) * pixelsPerZoom
                if x >= 24 && x <= bounds.width - 24 {
                    label.draw(at: CGPoint(x: x - label.size(withAttributes: style).width / 2, y: middle + 7), withAttributes: style)
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

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            guard let touch = touches.first else { return }
            dragStartX = touch.location(in: self).x
            dragStartZoom = current
            dragged = false
        }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
            guard let touch = touches.first, let start = dragStartX else { return }
            let distance = touch.location(in: self).x - start
            if abs(distance) > 2 { dragged = true }
            if dragged { onChange?(max(1, min(maximum, dragStartZoom - distance / pixelsPerZoom))) }
        }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
            if !dragged, let touch = touches.first {
                onChange?(max(1, min(maximum, current + (touch.location(in: self).x - bounds.midX) / pixelsPerZoom)))
            }
            dragStartX = nil
        }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { dragStartX = nil }
    }
}
