import UIKit

/// A single modal owns the tools, editor and settings navigation. UIKit keeps
/// the chat visible and dimmed and handles keyboard, rotation and dismissal.
public final class NebulaToolsNavigationController: UINavigationController, UIAdaptivePresentationControllerDelegate {
    private var onDismiss: (() -> Void)?

    public init(root: UIViewController, onDismiss: (() -> Void)? = nil) {
        self.onDismiss = onDismiss
        super.init(rootViewController: root)
        modalPresentationStyle = .pageSheet
        preferredContentSize = CGSize(width: 540, height: 560)
        if #available(iOS 15.0, *) {
            if let sheet = sheetPresentationController {
                if #available(iOS 16.0, *) {
                    sheet.detents = [.custom { context in min(560, context.maximumDetentValue * 0.8) }, .large()]
                } else {
                    sheet.detents = [.medium(), .large()]
                }
                sheet.prefersGrabberVisible = true
                sheet.prefersScrollingExpandsWhenScrolledToEdge = true
                sheet.preferredCornerRadius = 24
            }
        }
        presentationController?.delegate = self
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func didDismissTools() {
        let completion = onDismiss
        onDismiss = nil
        completion?()
    }

    public func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        didDismissTools()
    }

    public override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if isBeingDismissed || presentingViewController == nil { didDismissTools() }
    }
}

