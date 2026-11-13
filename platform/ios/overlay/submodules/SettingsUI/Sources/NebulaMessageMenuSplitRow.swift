import UIKit
import AsyncDisplayKit
import Display
import ContextUI
import TelegramPresentationData

/// Split message menu: the frequent actions become one row of icons below
/// the list (Android `NebulaMessageMenuSplit`). Every icon runs the original
/// Telegram action, so eligibility and side effects stay native.
final class NebulaMessageMenuSplitRowItem: ContextMenuCustomItem {
    let actions: [ContextMenuActionItem]

    init(actions: [ContextMenuActionItem]) {
        self.actions = actions
    }

    func node(presentationData: PresentationData, getController: @escaping () -> ContextControllerProtocol?, actionSelected: @escaping (ContextMenuActionResult) -> Void) -> ContextMenuCustomNode {
        return NebulaMessageMenuSplitRowNode(presentationData: presentationData, actions: self.actions, getController: getController, actionSelected: actionSelected)
    }
}

private final class NebulaMessageMenuSplitRowNode: ASDisplayNode, ContextMenuCustomNode {
    private static let buttonWidth: CGFloat = 48.0
    private static let height: CGFloat = 50.0
    private static let inset: CGFloat = 6.0

    private let actions: [ContextMenuActionItem]
    private let getController: () -> ContextControllerProtocol?
    private let actionSelected: (ContextMenuActionResult) -> Void
    private var presentationData: PresentationData
    private var buttons: [UIButton] = []

    init(presentationData: PresentationData, actions: [ContextMenuActionItem], getController: @escaping () -> ContextControllerProtocol?, actionSelected: @escaping (ContextMenuActionResult) -> Void) {
        self.presentationData = presentationData
        self.actions = actions
        self.getController = getController
        self.actionSelected = actionSelected
        super.init()
        for (index, action) in actions.enumerated() {
            let button = UIButton(type: .custom)
            button.tag = index
            button.layer.cornerRadius = 12.0
            button.accessibilityLabel = action.text
            button.showsLargeContentViewer = true
            button.largeContentTitle = action.text
            button.addInteraction(UILargeContentViewerInteraction())
            button.addTarget(self, action: #selector(self.buttonPressed(_:)), for: .touchUpInside)
            button.addTarget(self, action: #selector(self.buttonHighlighted(_:)), for: [.touchDown, .touchDragEnter])
            button.addTarget(self, action: #selector(self.buttonUnhighlighted(_:)), for: [.touchUpInside, .touchUpOutside, .touchCancel, .touchDragExit])
            self.buttons.append(button)
        }
        self.applyTheme()
    }

    override func didLoad() {
        super.didLoad()
        for button in self.buttons {
            self.view.addSubview(button)
        }
    }

    private func applyTheme() {
        let theme = self.presentationData.theme
        for (index, button) in self.buttons.enumerated() {
            let image = self.actions[index].icon(theme)
            button.setImage(image, for: .normal)
            button.largeContentImage = image
            button.tintColor = theme.contextMenu.primaryColor
        }
    }

    func updateLayout(constrainedWidth: CGFloat, constrainedHeight: CGFloat) -> (CGSize, (CGSize, ContainedViewLayoutTransition) -> Void) {
        let count = CGFloat(max(1, self.buttons.count))
        let width = min(constrainedWidth, count * Self.buttonWidth + Self.inset * 2.0)
        return (CGSize(width: width, height: Self.height), { [weak self] size, transition in
            guard let self else { return }
            let buttonWidth = floor((size.width - Self.inset * 2.0) / count)
            for (index, button) in self.buttons.enumerated() {
                let frame = CGRect(x: Self.inset + CGFloat(index) * buttonWidth, y: 3.0, width: buttonWidth, height: size.height - 6.0)
                transition.updateFrame(view: button, frame: frame)
            }
        })
    }

    func updateTheme(presentationData: PresentationData) {
        self.presentationData = presentationData
        self.applyTheme()
    }

    func canBeHighlighted() -> Bool {
        return false
    }

    func updateIsHighlighted(isHighlighted: Bool) {
    }

    func performAction() {
    }

    var needsSeparator: Bool {
        return false
    }

    @objc private func buttonHighlighted(_ sender: UIButton) {
        sender.backgroundColor = self.presentationData.theme.contextMenu.itemHighlightedBackgroundColor
    }

    @objc private func buttonUnhighlighted(_ sender: UIButton) {
        UIView.animate(withDuration: 0.2) {
            sender.backgroundColor = nil
        }
    }

    @objc private func buttonPressed(_ sender: UIButton) {
        guard sender.tag >= 0, sender.tag < self.actions.count, let action = self.actions[sender.tag].action else { return }
        action(ContextMenuActionItem.Action(controller: self.getController(), dismissWithResult: self.actionSelected, updateAction: { _, _ in }))
    }
}
