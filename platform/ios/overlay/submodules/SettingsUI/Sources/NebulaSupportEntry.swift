import UIKit
import Display
import AccountContext

/// The support page as a standalone sheet, for entry points outside settings
/// (the badge sheet's "Support the project").
public func nebulaSupportScreen(context: AccountContext) -> UIViewController {
    let presentation = context.sharedContext.currentPresentationData.with { $0 }
    let navigation = UINavigationController()
    let support = NebulaSupportController(russian: presentation.strings.baseLanguageCode.lowercased().hasPrefix("ru"), theme: presentation.theme, context: context, openCommunity: { [weak navigation] id in
        let presenting = navigation?.presentingViewController
        navigation?.dismiss(animated: true, completion: {
            let screen = context.sharedContext.makeCommunityViewScreen(context: context, communityId: id, mode: .sheet)
            if let navigationController = (presenting as? NavigationController) ?? presenting?.navigationController as? NavigationController {
                navigationController.pushViewController(screen)
            }
        })
    })
    navigation.viewControllers = [support]
    return navigation
}
