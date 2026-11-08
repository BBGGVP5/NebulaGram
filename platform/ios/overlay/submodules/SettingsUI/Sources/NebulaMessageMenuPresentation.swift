import UIKit
import ContextUI
import NebulaSettingsContract

public enum NebulaMessageMenuPresentation {
    public static func filter(_ items: [ContextMenuItem]) -> [ContextMenuItem] {
        let prefs = NebulaMessageMenuPreferences.shared
        var result: [ContextMenuItem] = []
        var separatorPending = false
        for item in items {
            if case .separator = item { separatorPending = !result.isEmpty; continue }
            if case let .action(action) = item {
                guard prefs.visible(identifier: action.id as? String) else { continue }
                action.nebulaCompact = prefs.compact
            }
            if separatorPending { result.append(.separator); separatorPending = false }
            result.append(item)
        }
        return split(result, enabled: prefs.split)
    }

    /// Moves the frequent actions into an icon row below the list.
    static func split(_ items: [ContextMenuItem], enabled: Bool) -> [ContextMenuItem] {
        var actionPositions: [Int] = []
        var identifiers: [String?] = []
        for (index, item) in items.enumerated() {
            if case let .action(action) = item {
                actionPositions.append(index)
                identifiers.append(action.id as? String)
            }
        }
        let row = NebulaMessageMenuSplit.row(identifiers, enabled: enabled)
        guard !row.isEmpty else { return items }
        let moved = Set(row.map { actionPositions[$0] })
        var rowActions: [ContextMenuActionItem] = []
        for position in row.map({ actionPositions[$0] }) {
            if case let .action(action) = items[position] { rowActions.append(action) }
        }
        var list: [ContextMenuItem] = []
        for (index, item) in items.enumerated() where !moved.contains(index) {
            if case .separator = item {
                if list.isEmpty { continue }
                if let last = list.last, case .separator = last { continue }
            }
            list.append(item)
        }
        if let last = list.last, case .separator = last { list.removeLast() }
        list.append(.separator)
        list.append(.custom(NebulaMessageMenuSplitRowItem(actions: rowActions), false))
        return list
    }
}
