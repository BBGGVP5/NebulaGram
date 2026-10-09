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
        return result
    }
}
