import Foundation

/// Device-local order only. Identity and folder scope are never derived from display names.
public final class NebulaLocalPinStore {
    public static let maximumPins = 1000
    public struct Snapshot {
        public let enabled: Bool
        public let orders: [String: [Int64]]
        public var count: Int { orders.values.reduce(0) { $0 + $1.count } }
    }
    private struct Stored: Codable { let version: Int; let enabled: Bool; let orders: [String: [Int64]] }
    private let defaults: UserDefaults
    private let lock = NSRecursiveLock()
    private var cached: [Int64: Snapshot] = [:]
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    private func key(_ owner: Int64) -> String { "nebula.localPins.\(owner)" }
    public static func validScope(_ scope: String) -> Bool {
        scope == "root:0" || scope == "root:1" || scope.range(of: "^filter:[1-9][0-9]{0,9}$", options: .regularExpression) != nil
    }
    public func read(owner: Int64) -> Snapshot {
        lock.lock(); defer { lock.unlock() }
        guard owner > 0 else { return Snapshot(enabled: false, orders: [:]) }
        if let value = cached[owner] { return value }
        var value = Snapshot(enabled: true, orders: [:])
        if let bytes = defaults.data(forKey: key(owner)) {
            if bytes.count <= 1_000_000, let stored = try? JSONDecoder().decode(Stored.self, from: bytes), stored.version == 1 {
                var orders: [String: [Int64]] = [:]; var count = 0
                for scope in stored.orders.keys.sorted() where Self.validScope(scope) {
                    var seen = Set<Int64>(); var ids: [Int64] = []
                    for id in stored.orders[scope] ?? [] where id != 0 && count < Self.maximumPins {
                        if seen.insert(id).inserted { ids.append(id); count += 1 }
                    }
                    if !ids.isEmpty { orders[scope] = ids }
                }
                value = Snapshot(enabled: stored.enabled, orders: orders)
            } else { value = Snapshot(enabled: false, orders: [:]) }
        }
        cached[owner] = value; return value
    }
    private func write(owner: Int64, snapshot: Snapshot) -> Bool {
        guard owner > 0, let data = try? JSONEncoder().encode(Stored(version: 1, enabled: snapshot.enabled, orders: snapshot.orders)), data.count <= 1_000_000 else { return false }
        defaults.set(data, forKey: key(owner)); cached[owner] = snapshot; return true
    }
    @discardableResult public func pin(owner: Int64, scope: String, peer: Int64, pinned: Bool) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard owner > 0, peer != 0, Self.validScope(scope) else { return false }
        let old = read(owner: owner); var orders = old.orders; var ids = orders[scope] ?? []
        if ids.contains(peer) == pinned { return true }
        guard !pinned || old.count < Self.maximumPins else { return false }
        if pinned { ids.insert(peer, at: 0) } else { ids.removeAll { $0 == peer } }
        if ids.isEmpty { orders.removeValue(forKey: scope) } else { orders[scope] = ids }
        return write(owner: owner, snapshot: Snapshot(enabled: old.enabled, orders: orders))
    }
    @discardableResult public func reorder(owner: Int64, scope: String, requested: [Int64]) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard owner > 0, Self.validScope(scope) else { return false }
        let old = read(owner: owner); var orders = old.orders; let known = orders[scope] ?? []; let allowed = Set(known)
        var seen = Set<Int64>(); let next = (requested + known).filter { allowed.contains($0) && seen.insert($0).inserted }
        if next.isEmpty { orders.removeValue(forKey: scope) } else { orders[scope] = next }
        return write(owner: owner, snapshot: Snapshot(enabled: old.enabled, orders: orders))
    }
    @discardableResult public func setEnabled(owner: Int64, value: Bool) -> Bool {
        lock.lock(); defer { lock.unlock() }; return write(owner: owner, snapshot: Snapshot(enabled: value, orders: read(owner: owner).orders))
    }
    @discardableResult public func clear(owner: Int64) -> Bool {
        lock.lock(); defer { lock.unlock() }; return write(owner: owner, snapshot: Snapshot(enabled: read(owner: owner).enabled, orders: [:]))
    }
    public static func merge(remote: [Int64], local: [Int64]) -> [Int64] {
        var seen = Set<Int64>(); return (remote + local).filter { $0 != 0 && seen.insert($0).inserted }
    }
}
