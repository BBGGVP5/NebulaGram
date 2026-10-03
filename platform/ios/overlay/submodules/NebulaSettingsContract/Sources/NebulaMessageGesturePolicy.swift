import Foundation

/// Personal outgoing messages only; native reactions remain the fallback for unavailable actions.
public enum NebulaMessageGesturePolicy {
    public enum Action: Int { case reaction = 0, edit, reply, copy, none }

    public static func doubleTap(preference: Int, owned: Bool, channelPost: Bool,
                                 canEdit: Bool, canReply: Bool, canCopy: Bool, hasText: Bool) -> Action {
        guard owned && !channelPost else { return .reaction }
        switch preference {
        case 1: return canEdit ? .edit : .reaction
        case 2: return canReply ? .reply : .reaction
        case 3: return canCopy && hasText ? .copy : .reaction
        case 4: return .none
        default: return .reaction
        }
    }
}
