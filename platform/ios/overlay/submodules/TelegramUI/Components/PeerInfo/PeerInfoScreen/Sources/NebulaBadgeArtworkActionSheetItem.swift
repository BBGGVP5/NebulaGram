import UIKit
import Display
import NebulaSettingsContract

/// Shows the assigned artwork in the badge details sheet, not a placeholder glyph.
final class NebulaBadgeArtworkActionSheetItem: ActionSheetItem {
    let badge: NebulaBadge

    init(badge: NebulaBadge) {
        self.badge = badge
    }

    func node(theme: ActionSheetControllerTheme) -> ActionSheetItemNode {
        NebulaBadgeArtworkActionSheetNode(theme: theme, badge: badge)
    }

    func updateNode(_ node: ActionSheetItemNode) {
        (node as? NebulaBadgeArtworkActionSheetNode)?.setBadge(badge)
    }
}

private final class NebulaBadgeArtworkActionSheetNode: ActionSheetItemNode {
    private let artwork = UIImageView()

    init(theme: ActionSheetControllerTheme, badge: NebulaBadge) {
        super.init(theme: theme)
        artwork.contentMode = .scaleAspectFit
        artwork.isAccessibilityElement = true
        view.addSubview(artwork)
        setBadge(badge)
    }

    func setBadge(_ badge: NebulaBadge) {
        artwork.image = NebulaProfileBadgeArtwork.image(for: badge)
        artwork.accessibilityLabel = badge.title
        artwork.layer.removeAnimation(forKey: "nebula.details.pulse")
        if !UIAccessibility.isReduceMotionEnabled {
            let pulse = CAKeyframeAnimation(keyPath: "transform.scale")
            pulse.values = [1.0, 1.07, 1.0]
            pulse.keyTimes = [0.0, 0.5, 1.0]
            pulse.duration = 2.4
            pulse.repeatCount = .infinity
            artwork.layer.add(pulse, forKey: "nebula.details.pulse")
        }
    }

    override func updateLayout(constrainedSize: CGSize, transition: ContainedViewLayoutTransition) -> CGSize {
        let size = CGSize(width: constrainedSize.width, height: 112)
        artwork.frame = CGRect(x: floor((size.width - 84) / 2), y: 14, width: 84, height: 84)
        updateInternalLayout(size, constrainedSize: constrainedSize)
        return size
    }
}
