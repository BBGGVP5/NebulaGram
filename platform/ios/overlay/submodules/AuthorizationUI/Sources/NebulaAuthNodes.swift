import UIKit
import AsyncDisplayKit
import TelegramPresentationData

final class NebulaAuthArtNode: ASDisplayNode {
    init(kind: NebulaAuthArtView.Kind, accent: UIColor) {
        super.init()
        setViewBlock { NebulaAuthArtView(kind: kind, accent: accent) }
        isUserInteractionEnabled = false
    }
}

final class NebulaAuthBackdropNode: ASDisplayNode {
    init(theme: PresentationTheme) {
        super.init()
        setViewBlock { NebulaAuthBackdropView(accent: theme.list.itemAccentColor, surface: theme.list.plainBackgroundColor) }
        isUserInteractionEnabled = false
    }
    func setFieldFrame(_ frame: CGRect) {
        (view as? NebulaAuthBackdropView)?.setFieldFrame(frame)
    }
}
