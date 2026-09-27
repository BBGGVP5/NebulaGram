import UIKit
import AVFoundation

/// Native control for round-video zoom. It uses the current camera's available range.
final class NebulaVideoZoomSlider: UIView {
    var onZoomChanged: ((CGFloat) -> Void)?
    private let valueLabel = UILabel()
    private let limitLabel = UILabel()
    private let slider = UISlider()
    private var maximum: CGFloat = 2

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = UIColor(red: 25.0 / 255.0, green: 35.0 / 255.0, blue: 52.0 / 255.0, alpha: 0.94)
        layer.cornerRadius = 25
        layer.cornerCurve = .continuous
        layer.borderWidth = 1
        layer.borderColor = UIColor(red: 78.0 / 255.0, green: 130.0 / 255.0, blue: 155.0 / 255.0, alpha: 0.35).cgColor
        for label in [valueLabel, limitLabel] {
            label.font = .monospacedDigitSystemFont(ofSize: 14, weight: .semibold)
            label.textColor = UIColor(red: 240.0 / 255.0, green: 245.0 / 255.0, blue: 1, alpha: 1)
            label.textAlignment = .center
            addSubview(label)
        }
        slider.minimumValue = 1
        slider.minimumTrackTintColor = UIColor(red: 87.0 / 255.0, green: 197.0 / 255.0, blue: 208.0 / 255.0, alpha: 1)
        slider.thumbTintColor = slider.minimumTrackTintColor
        slider.addTarget(self, action: #selector(changed), for: .valueChanged)
        slider.accessibilityLabel = "Zoom"
        addSubview(slider)
        setRange(front: false)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        valueLabel.frame = CGRect(x: 5, y: 0, width: 49, height: bounds.height)
        limitLabel.frame = CGRect(x: bounds.width - 49, y: 0, width: 44, height: bounds.height)
        slider.frame = CGRect(x: 53, y: 0, width: max(0, bounds.width - 104), height: bounds.height)
    }

    static func availableMaximum(front: Bool) -> CGFloat {
        let position: AVCaptureDevice.Position = front ? .front : .back
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else { return 2 }
        return max(1, min(8, device.maxAvailableVideoZoomFactor))
    }

    func setRange(front: Bool) {
        maximum = Self.availableMaximum(front: front)
        slider.maximumValue = Float(maximum)
        limitLabel.text = Self.format(maximum)
        setCurrent(1)
    }

    func setCurrent(_ factor: CGFloat) {
        let bounded = max(1, min(maximum, factor))
        slider.value = Float(bounded)
        valueLabel.text = Self.format(bounded)
    }

    @objc private func changed() {
        let factor = CGFloat(slider.value)
        valueLabel.text = Self.format(factor)
        onZoomChanged?(factor)
    }

    private static func format(_ factor: CGFloat) -> String {
        abs(factor - factor.rounded()) < 0.05 ? "\(Int(factor.rounded()))×" : String(format: "%.1f×", Double(factor))
    }
}
