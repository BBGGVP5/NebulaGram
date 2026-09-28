import UIKit
import AVFoundation

/// Camera-limited round-video zoom with quick presets and an expanding ruler.
final class NebulaVideoZoomSlider: UIView {
    var onZoomChanged: ((CGFloat) -> Void)?

    private let accent = UIColor(red: 0.89, green: 0.20, blue: 0.57, alpha: 1)
    private let capsule = UIView()
    private let currentLabel = UILabel()
    private let secondLabel = UILabel()
    private let ruler = RulerView()
    private var maximum: CGFloat = 2
    private var current: CGFloat = 1
    private var expanded = false
    private var collapseWork: DispatchWorkItem?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        capsule.backgroundColor = UIColor(red: 0.13, green: 0.11, blue: 0.17, alpha: 0.94)
        capsule.layer.cornerRadius = 26
        capsule.layer.cornerCurve = .continuous
        capsule.layer.borderWidth = 1
        capsule.layer.borderColor = UIColor(white: 1, alpha: 0.20).cgColor
        capsule.clipsToBounds = true
        addSubview(capsule)

        for label in [currentLabel, secondLabel] {
            label.font = .monospacedDigitSystemFont(ofSize: 18, weight: .bold)
            label.textColor = .white
            label.textAlignment = .center
            label.isUserInteractionEnabled = true
            label.layer.cornerRadius = 23
            label.layer.cornerCurve = .continuous
            label.clipsToBounds = true
            capsule.addSubview(label)
        }
        currentLabel.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapCurrent)))
        secondLabel.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapSecond)))
        ruler.isHidden = true
        ruler.contentMode = .redraw
        ruler.accent = accent
        ruler.onChange = { [weak self] factor in
            self?.collapseWork?.cancel()
            self?.updateZoom(factor, notify: true)
        }
        ruler.onEnd = { [weak self] in self?.scheduleCollapse() }
        capsule.addSubview(ruler)
        setRange(front: false)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        let inset: CGFloat = 5
        let capsuleWidth = min(bounds.width, expanded ? 320 : 224)
        capsule.frame = CGRect(x: floor((bounds.width - capsuleWidth) / 2), y: 0, width: capsuleWidth, height: bounds.height)
        let width = (capsuleWidth - inset * 2) / 2
        currentLabel.frame = CGRect(x: inset, y: 4, width: width, height: bounds.height - 8)
        secondLabel.frame = CGRect(x: inset + width, y: 4, width: width, height: bounds.height - 8)
        ruler.frame = capsule.bounds
    }

    static func availableMaximum(front: Bool) -> CGFloat {
        let position: AVCaptureDevice.Position = front ? .front : .back
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else { return 2 }
        return max(1, min(8, device.maxAvailableVideoZoomFactor))
    }

    func setRange(front: Bool) {
        maximum = Self.availableMaximum(front: front)
        ruler.maximum = maximum
        updateZoom(1, notify: false)
        setExpanded(false)
    }

    func setCurrent(_ factor: CGFloat) { updateZoom(factor, notify: false) }

    @objc private func tapCurrent() {
        if abs(current - 2) < 0.05 { updateZoom(1, notify: true) }
        setExpanded(true)
    }

    @objc private func tapSecond() {
        updateZoom(min(2, maximum), notify: true)
        setExpanded(true)
    }

    private func updateZoom(_ factor: CGFloat, notify: Bool) {
        current = max(1, min(maximum, factor))
        let secondSelected = maximum >= 2 && abs(current - 2) < 0.05
        currentLabel.text = secondSelected ? "1×" : Self.format(current)
        secondLabel.text = Self.format(min(2, maximum))
        currentLabel.backgroundColor = secondSelected ? .clear : accent
        secondLabel.backgroundColor = secondSelected ? accent : .clear
        currentLabel.accessibilityLabel = "Zoom \(currentLabel.text ?? "1×")"
        secondLabel.accessibilityLabel = "Zoom \(secondLabel.text ?? "2×")"
        ruler.current = current
        if notify { onZoomChanged?(current) }
    }

    private func setExpanded(_ value: Bool) {
        collapseWork?.cancel()
        if expanded == value {
            if value { scheduleCollapse() }
            return
        }
        expanded = value
        setNeedsLayout()
        let incoming: UIView = value ? ruler : currentLabel
        let duration: TimeInterval = UIAccessibility.isReduceMotionEnabled ? 0 : 0.22
        ruler.isHidden = !value
        currentLabel.isHidden = value
        secondLabel.isHidden = value
        incoming.alpha = duration == 0 ? 1 : 0
        incoming.transform = duration == 0 ? .identity : CGAffineTransform(scaleX: value ? 0.94 : 1.04, y: 1)
        UIView.animate(withDuration: duration, delay: 0, options: [.beginFromCurrentState, .curveEaseOut]) {
            self.layoutIfNeeded()
            incoming.alpha = 1
            incoming.transform = .identity
            self.secondLabel.alpha = 1
        }
        if value { scheduleCollapse() }
    }

    private func scheduleCollapse() {
        collapseWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.setExpanded(false) }
        collapseWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8, execute: work)
    }

    private static func format(_ value: CGFloat) -> String {
        abs(value - value.rounded()) < 0.05 ? "\(Int(value.rounded()))×" : String(format: "%.1f×", Double(value))
    }

    private final class RulerView: UIView {
        var maximum: CGFloat = 2 { didSet { setNeedsDisplay() } }
        var current: CGFloat = 1 { didSet { setNeedsDisplay() } }
        var accent: UIColor = .systemPink
        var onChange: ((CGFloat) -> Void)?
        var onEnd: (() -> Void)?

        override func draw(_ rect: CGRect) {
            guard let context = UIGraphicsGetCurrentContext() else { return }
            let left: CGFloat = 20
            let span = max(1, bounds.width - 40)
            for index in 0...24 {
                let value = 1 + (maximum - 1) * CGFloat(index) / 24
                let x = left + span * CGFloat(index) / 24
                let major = index == 0 || index == 24 || abs(value - 2) < (maximum - 1) / 48 || abs(value - 5) < (maximum - 1) / 48
                context.setStrokeColor((major ? accent : UIColor(white: 0.85, alpha: 0.55)).cgColor)
                context.setLineWidth(major ? 2 : 1)
                context.move(to: CGPoint(x: x, y: 10))
                context.addLine(to: CGPoint(x: x, y: major ? 29 : 23))
                context.strokePath()
            }
            let x = left + span * (current - 1) / max(0.001, maximum - 1)
            context.setStrokeColor(accent.cgColor)
            context.setLineWidth(5)
            context.setLineCap(.round)
            context.move(to: CGPoint(x: x, y: 8))
            context.addLine(to: CGPoint(x: x, y: 30))
            context.strokePath()
            let style: [NSAttributedString.Key: Any] = [.font: UIFont.monospacedDigitSystemFont(ofSize: 11, weight: .bold), .foregroundColor: accent]
            var marks: [CGFloat] = [1]
            if maximum >= 2 { marks.append(2) }
            if maximum >= 5 { marks.append(5) }
            if maximum - marks[marks.count - 1] > 0.1 { marks.append(maximum) }
            for value in marks {
                let label = String(format: "%.0f", Double(value)) as NSString
                let position = left + span * (value - 1) / max(0.001, maximum - 1)
                label.draw(at: CGPoint(x: position - label.size(withAttributes: style).width / 2, y: 33), withAttributes: style)
            }
        }

        private func select(_ touch: UITouch) {
            let ratio = max(0, min(1, (touch.location(in: self).x - 20) / max(1, bounds.width - 40)))
            onChange?(1 + (maximum - 1) * ratio)
        }
        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) { if let touch = touches.first { select(touch) } }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { if let touch = touches.first { select(touch) } }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { onEnd?() }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { onEnd?() }
    }
}
