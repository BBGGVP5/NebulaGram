import UIKit

/// Public UIKit materials only. The preview uses the same GlassBackgroundView as chat surfaces.
struct NebulaGlassMaterialState: Equatable {
    let style: Int
    let tint: Int
    let blur: Int
    let color: UIColor
    let dark: Bool
    let reduced: Bool
    let opaque: Bool
    let animated: Bool

    func apply(to view: UIVisualEffectView) {
        view.contentView.backgroundColor = opaque ? color : color.withAlphaComponent(CGFloat(tint) / 100)
        view.overrideUserInterfaceStyle = dark ? .dark : .light
        if opaque { view.effect = nil; return }
        if #available(iOS 26.0, *), style == 1 && !reduced {
            let effect = UIGlassEffect(style: .regular)
            effect.isInteractive = animated
            view.effect = effect
        } else {
            let material: UIBlurEffect.Style
            if reduced || blur >= 75 { material = .systemThickMaterial }
            else if blur >= 35 { material = .systemMaterial }
            else if blur >= 15 { material = .systemThinMaterial }
            else { material = .systemUltraThinMaterial }
            view.effect = UIBlurEffect(style: material)
        }
    }
}
