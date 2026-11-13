import UIKit
import NebulaSettingsContract

/// Soft colour glow from the photo behind it in the media viewer (Android
/// `NebulaMediaAmbient`): an enlarged, blurred and dimmed copy of the picture.
/// Nothing is sampled per frame; the view only follows the shown image.
final class NebulaMediaAmbientView: UIView {
    private let imageView = UIImageView()
    private let blur = UIVisualEffectView(effect: UIBlurEffect(style: .dark))

    override init(frame: CGRect) {
        super.init(frame: frame)
        self.isUserInteractionEnabled = false
        self.clipsToBounds = true
        self.isHidden = true
        self.imageView.contentMode = .scaleAspectFill
        self.imageView.alpha = 0.6
        self.addSubview(self.imageView)
        self.addSubview(self.blur)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func update(image: UIImage?) {
        guard NebulaSettingsStore.shared.mediaAmbient, let image else {
            self.isHidden = true
            self.imageView.image = nil
            return
        }
        self.isHidden = false
        self.imageView.image = image
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        self.imageView.frame = self.bounds.insetBy(dx: -self.bounds.width * 0.15, dy: -self.bounds.height * 0.15)
        self.blur.frame = self.bounds
    }
}
