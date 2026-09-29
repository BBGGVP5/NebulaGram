import UIKit

/// Public UIKit materials only. The preview uses the same GlassBackgroundView as chat surfaces.
struct NebulaGlassMaterialState: Equatable {
    let style: Int
    let tint: Int
    let dark: Bool
    let reduced: Bool
    let opaque: Bool
    let animated: Bool

    func apply(to view: UIVisualEffectView) {
        let color = dark ? UIColor(red: 0.08, green: 0.12, blue: 0.18, alpha: 1) : UIColor(red: 0.91, green: 0.95, blue: 1, alpha: 1)
        view.contentView.backgroundColor = opaque ? color : color.withAlphaComponent(CGFloat(tint) / 100)
        view.overrideUserInterfaceStyle = dark ? .dark : .light
        if opaque { view.effect = nil; return }
        if #available(iOS 26.0, *), style == 1 && !reduced {
            let effect = UIGlassEffect(style: .regular)
            effect.isInteractive = animated
            view.effect = effect
        } else {
            view.effect = UIBlurEffect(style: reduced ? .systemThickMaterial : .systemMaterial)
        }
    }
}
