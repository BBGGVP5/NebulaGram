import UIKit
import Display
import AccountContext
import TelegramPresentationData
import SettingsUI
import NebulaSettingsContract

/// Sheet opened from the badge beside a profile name (Android `NebulaBadgeSheet`):
/// large artwork, what the badge means, how it is earned, every NebulaGram
/// badge, and a way to support the project for donation badges.
enum NebulaBadgeSheet {
    static func present(badge: NebulaBadge, context: AccountContext, presentationData: PresentationData, controller: ViewController) {
        let russian = presentationData.strings.baseLanguageCode.lowercased().hasPrefix("ru")
        let sheet = ActionSheetController(presentationData: presentationData)
        let dismiss: () -> Void = { [weak sheet] in sheet?.dismissAnimated() }
        var buttons: [ActionSheetItem] = []
        if badge.isDonation {
            buttons.append(ActionSheetButtonItem(title: russian ? "Поддержать проект" : "Support the project", color: .accent, action: { [weak sheet, weak controller] in
                sheet?.dismissAnimated()
                guard let controller else { return }
                controller.present(nebulaSupportScreen(context: context), animated: true)
            }))
        }
        buttons.append(ActionSheetButtonItem(title: russian ? "Понятно" : "Got it", color: .accent, font: badge.isDonation ? .default : .bold, action: dismiss))
        sheet.setItemGroups([
            ActionSheetItemGroup(items: [
                NebulaBadgeArtworkActionSheetItem(badge: badge),
                ActionSheetTextItem(title: badge.localizedTitle(russian: russian), font: .large, parseMarkdown: false),
                ActionSheetTextItem(title: badge.localizedDescription(russian: russian), parseMarkdown: false),
                ActionSheetTextItem(title: (russian ? "**Как получить.** " : "**How to get it.** ") + badge.howToGet(russian: russian), parseMarkdown: true),
                NebulaBadgeGalleryActionSheetItem(selected: badge, russian: russian)
            ]),
            ActionSheetItemGroup(items: buttons)
        ])
        controller.present(sheet, in: .window(.root))
    }
}

/// "All NebulaGram badges": every artwork in one row, the shown one emphasised.
final class NebulaBadgeGalleryActionSheetItem: ActionSheetItem {
    let selected: NebulaBadge
    let russian: Bool

    init(selected: NebulaBadge, russian: Bool) {
        self.selected = selected
        self.russian = russian
    }

    func node(theme: ActionSheetControllerTheme) -> ActionSheetItemNode {
        NebulaBadgeGalleryActionSheetNode(theme: theme, selected: selected, russian: russian)
    }

    func updateNode(_ node: ActionSheetItemNode) {
    }
}

private final class NebulaBadgeGalleryActionSheetNode: ActionSheetItemNode {
    private let caption = UILabel()
    private var views: [UIImageView] = []

    init(theme: ActionSheetControllerTheme, selected: NebulaBadge, russian: Bool) {
        super.init(theme: theme)
        caption.text = russian ? "Все значки NebulaGram" : "All NebulaGram badges"
        caption.font = .systemFont(ofSize: 13.0, weight: .semibold)
        caption.textColor = theme.secondaryTextColor
        caption.textAlignment = .center
        view.addSubview(caption)
        for badge in NebulaBadge.displayOrder {
            let image = UIImageView(image: NebulaProfileBadgeArtwork.image(for: badge))
            image.contentMode = .scaleAspectFit
            image.alpha = badge == selected ? 1.0 : 0.45
            image.isAccessibilityElement = true
            image.accessibilityLabel = badge.localizedTitle(russian: russian)
            if badge == selected {
                image.accessibilityTraits = .selected
            }
            view.addSubview(image)
            views.append(image)
        }
    }

    override func updateLayout(constrainedSize: CGSize, transition: ContainedViewLayoutTransition) -> CGSize {
        let size = CGSize(width: constrainedSize.width, height: 78.0)
        caption.frame = CGRect(x: 16.0, y: 8.0, width: size.width - 32.0, height: 18.0)
        let side: CGFloat = 30.0
        let step = min(44.0, (size.width - 32.0) / CGFloat(max(1, views.count)))
        let start = floor((size.width - step * CGFloat(views.count)) / 2.0)
        for (index, image) in views.enumerated() {
            image.frame = CGRect(x: start + CGFloat(index) * step + floor((step - side) / 2.0), y: 34.0, width: side, height: side)
        }
        updateInternalLayout(size, constrainedSize: constrainedSize)
        return size
    }
}
