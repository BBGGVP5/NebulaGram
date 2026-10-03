import Foundation

/// Transient request state. Each visible chat owns one request; nothing is persisted.
public struct NebulaTranslationKey: Hashable {
    public let account: String
    public let peer: String
    public let namespace: Int32
    public let message: Int32
    public init(account: String, peer: String, namespace: Int32, message: Int32) {
        self.account = account; self.peer = peer; self.namespace = namespace; self.message = message
    }
}

public enum NebulaTranslationActivity {
    public static let changed = Notification.Name("NebulaTranslationActivityChanged")
    private static let lock = NSLock()
    private static var requests: [UUID: NebulaTranslationKey] = [:]
    private static var notificationPending = false
    public static func contains(_ key: NebulaTranslationKey) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return requests.values.contains(key)
    }
    public static func set(owner: UUID, key: NebulaTranslationKey?) {
        lock.lock()
        guard requests[owner] != key else { lock.unlock(); return }
        requests[owner] = key
        let notify = !notificationPending
        notificationPending = true
        lock.unlock()
        if notify {
            DispatchQueue.main.async {
                lock.lock(); notificationPending = false; lock.unlock()
                NotificationCenter.default.post(name: changed, object: nil)
            }
        }
    }
}
