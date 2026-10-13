import UIKit

/// A private editor embedded above the native recorder. It never sends or deletes audio.
public final class NebulaDraftVoicePreview {
    private let controller: UIViewController
    public var view: UIView { controller.view }
    public let selection: () -> (selected: Bool, text: String?)
    private let dispose: () -> Void
    private var closed = false
    public init(controller: UIViewController, selection: @escaping () -> (selected: Bool, text: String?), dispose: @escaping () -> Void) {
        self.controller = controller; self.selection = selection; self.dispose = dispose
    }
    public func attach(to parent: UIViewController) {
        guard !closed, controller.parent !== parent else { return }
        parent.addChild(controller); parent.view.addSubview(view); controller.didMove(toParent: parent)
    }
    public func cancel() {
        guard !closed else { return }; closed = true
        view.endEditing(true); controller.willMove(toParent: nil); view.removeFromSuperview(); controller.removeFromParent(); dispose()
    }
    deinit { dispose() }
}
