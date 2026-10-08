import UIKit
import Display
import ItemListUI
import SwiftSignalKit
import AccountContext
import TelegramPresentationData

final class NebulaSettingsIntroItem: ListViewItem, ItemListItem {
    let sectionId: ItemListSectionId = -2
    let isAlwaysPlain = true
    private let context: AccountContext
    private let theme: PresentationTheme
    private let symbol: String
    private let title: String
    private let summary: String
    init(context: AccountContext, theme: PresentationTheme, symbol: String, title: String, summary: String) {
        self.context = context; self.theme = theme; self.symbol = symbol; self.title = title; self.summary = summary
    }
    private func makeHero() -> NebulaSettingsHero {
        NebulaSettingsHero(symbol: symbol, title: title, summary: summary, context: context, theme: theme)
    }
    func nodeConfiguredForParams(async: @escaping (@escaping () -> Void) -> Void, params: ListViewItemLayoutParams, synchronousLoads: Bool, previousItem: ListViewItem?, nextItem: ListViewItem?, completion: @escaping (ListViewItemNode, @escaping () -> (Signal<Void, NoError>?, (ListViewItemApply) -> Void)) -> Void) {
        Queue.mainQueue().async {
            let node = NebulaSettingsIntroNode(hero: self.makeHero())
            let layout = node.measure(width: params.width)
            node.contentSize = layout.contentSize; node.insets = layout.insets
            completion(node, { (nil, { _ in node.apply(layout) }) })
        }
    }
    func updateNode(async: @escaping (@escaping () -> Void) -> Void, node: @escaping () -> ListViewItemNode, params: ListViewItemLayoutParams, previousItem: ListViewItem?, nextItem: ListViewItem?, animation: ListViewItemUpdateAnimation, completion: @escaping (ListViewItemNodeLayout, @escaping (ListViewItemApply) -> Void) -> Void) {
        Queue.mainQueue().async {
            guard let node = node() as? NebulaSettingsIntroNode else { return }
            node.replaceHero(self.makeHero())
            let layout = node.measure(width: params.width)
            completion(layout, { _ in node.apply(layout) })
        }
    }
}

private final class NebulaSettingsIntroNode: ListViewItemNode {
    private var hero: NebulaSettingsHero
    init(hero: NebulaSettingsHero) {
        self.hero = hero; super.init(layerBacked: false)
        view.addSubview(hero)
    }
    override var canBeSelected: Bool { false }
    override var visibility: ListViewItemNodeVisibility {
        didSet { hero.setPageVisible(visibility != .none) }
    }
    func replaceHero(_ next: NebulaSettingsHero) {
        hero.removeFromSuperview(); hero = next; view.addSubview(next)
        hero.setPageVisible(visibility != .none)
    }
    func measure(width: CGFloat) -> ListViewItemNodeLayout {
        let size = hero.systemLayoutSizeFitting(CGSize(width: width, height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        return ListViewItemNodeLayout(contentSize: CGSize(width: width, height: ceil(size.height)), insets: .zero)
    }
    func apply(_ layout: ListViewItemNodeLayout) { hero.frame = CGRect(origin: .zero, size: layout.contentSize) }
}
