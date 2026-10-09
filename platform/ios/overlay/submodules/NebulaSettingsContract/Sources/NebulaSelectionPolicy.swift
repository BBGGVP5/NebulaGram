import Foundation

public enum NebulaSelectionPolicy {
    /// Never truncate an existing native selection or fetch more history.
    public static func addingLoaded<ID: Hashable>(_ loaded: [ID], to selected: Set<ID>, limit: Int = 100) -> Set<ID> {
        var result = selected
        for id in loaded {
            guard result.count < limit else { break }
            result.insert(id)
        }
        return result
    }
}
