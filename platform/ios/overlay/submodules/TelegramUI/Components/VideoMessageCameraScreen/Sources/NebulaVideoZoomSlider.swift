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
        if notify { onZoomChanged?(current) }
    }

    private final class RulerView: UIView {
        var maximum: CGFloat = 2 { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemTeal { didSet { setNeedsDisplay() } }
        var onChange: ((CGFloat) -> Void)?

        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            let left: CGFloat = 27
            let span = max(1, bounds.width - 54)
            let middle = bounds.midY
            for index in 0...24 {
                let value = 1 + (maximum - 1) * CGFloat(index) / 24
                let x = left + span * CGFloat(index) / 24
                let major = index == 0 || index == 24 || abs(value - 2) < (maximum - 1) / 48 || abs(value - 5) < (maximum - 1) / 48
                context.setStrokeColor((major ? accent : UIColor(white: 0.85, alpha: 0.58)).cgColor)
                context.setLineWidth(major ? 2 : 1)
                context.move(to: CGPoint(x: x, y: middle - 13))
                context.addLine(to: CGPoint(x: x, y: middle + (major ? 5 : 1)))
                context.strokePath()
            }
            let indicator = left + span * (current - 1) / max(0.001, maximum - 1)
            context.setStrokeColor(accent.cgColor)
            context.setLineWidth(5)
            context.setLineCap(.round)
            context.move(to: CGPoint(x: indicator, y: middle - 16))
            context.addLine(to: CGPoint(x: indicator, y: middle + 5))
            context.strokePath()
            let style: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedDigitSystemFont(ofSize: 11, weight: .bold),
                .foregroundColor: accent
            ]
            var marks: [CGFloat] = [1]
            if maximum >= 1.95 { marks.append(2) }
            if maximum >= 5 { marks.append(5) }
            if maximum - marks[marks.count - 1] > 0.1 { marks.append(maximum) }
            for mark in marks {
                let label = String(format: "%.0f", Double(mark)) as NSString
                let x = left + span * (mark - 1) / max(0.001, maximum - 1)
                label.draw(at: CGPoint(x: x - label.size(withAttributes: style).width / 2, y: middle + 7), withAttributes: style)
            }
        }

        private func select(_ touch: UITouch) {
            let ratio = max(0, min(1, (touch.location(in: self).x - 27) / max(1, bounds.width - 54)))
            onChange?(1 + (maximum - 1) * ratio)
        }
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) { if let touch = touches.first { select(touch) } }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { if let touch = touches.first { select(touch) } }
    }
}
