import Foundation

/// Policies shared by native navigation and its headless regression checks.
public enum NebulaNavigationPolicy {
    public static func hideAllChats(requested: Bool, customFolderCount: Int) -> Bool {
        // Never leave the root list with no reachable destination.
        requested && customFolderCount > 0
    }

    public static func selectedFolder<Id: Equatable>(current: Id, available: [Id], allChats: Id) -> Id {
        available.contains(current) ? current : (available.first ?? allChats)
    }

    public static func avatarCornerFraction(roundness: Int, uniform: Bool, isForum: Bool) -> Double {
        if isForum && !uniform { return 0.25 }
        return Double(max(0, min(100, roundness))) / 200.0
    }
}
