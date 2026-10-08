import AccountContext
import NebulaLinkUI

func nebulaLinkController(context: AccountContext, russian: Bool) -> NebulaLinkController {
    let theme = context.sharedContext.currentPresentationData.with { $0 }.theme
    let hero = NebulaSettingsHero(symbol: "link", title: "NebulaLink",
        summary: russian ? "Подключение, серверы и настройки соединения" : "Connection, servers and connection settings", context: context, theme: theme)
    return NebulaLinkController(russian: russian, introduction: hero, introductionVisibility: { [weak hero] visible in hero?.setPageVisible(visible) })
}
