import Foundation

public enum NebulaSwipeAction: Int, CaseIterable {
    case reply = 0, copy = 1, tools = 2, translate = 3
}

public enum NebulaSwipeActions {
    public static func parse(_ value: String) -> [NebulaSwipeAction] {
        guard value.utf16.count <= 40 else { return [.reply] }
        var result: [NebulaSwipeAction] = []
        for token in value.split(separator: ",") {
            if let raw = Int(token), let action = NebulaSwipeAction(rawValue: raw), !result.contains(action) { result.append(action) }
        }
        return result.isEmpty ? [.reply] : result
    }
    public static func action(order: [NebulaSwipeAction], verticalOffset: Double) -> NebulaSwipeAction {
        guard !order.isEmpty, verticalOffset.isFinite else { return .reply }
        let index = Int(max(0, min(Double(order.count - 1), (verticalOffset / 56).rounded())))
        return order[index]
    }
}
