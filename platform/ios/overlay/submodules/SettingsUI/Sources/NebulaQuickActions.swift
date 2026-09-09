import UIKit
import AccountContext
import Display
import NebulaLinkUI

public func nebulaOpenQuickAction(context: AccountContext, path: String, navigationController: NavigationController) -> Bool {
    let route = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    if route == "nebula/settings" {
        navigationController.pushViewController(nebulaSettingsController(context: context))
        return true
    }
    if route == "nebula/link", let host = navigationController.topViewController {
        guard host.presentedViewController == nil else { return true }
        NebulaLinkService.shared.configure(accountManager: context.sharedContext.accountManager)
        let ru = context.sharedContext.currentPresentationData.with { $0 }.strings.baseLanguageCode.hasPrefix("ru")
        host.present(UINavigationController(rootViewController: NebulaLinkController(russian: ru)), animated: true)
        return true
    }
    return false
}
