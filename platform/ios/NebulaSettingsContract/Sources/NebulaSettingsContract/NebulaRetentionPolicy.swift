import Foundation

public enum NebulaRetentionPolicy {
    public static func allowed(enabled: Bool, secret: Bool, expiring: Bool, saveSecret: Bool, saveExpiring: Bool, protectedContent: Bool, incoming: Bool, service: Bool, validId: Bool) -> Bool {
        enabled && incoming && !service && validId && !protectedContent && (!secret || saveSecret) && (!expiring || saveExpiring)
    }
}
