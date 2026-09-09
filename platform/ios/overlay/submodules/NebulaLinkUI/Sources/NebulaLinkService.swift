import Foundation
import UIKit
import Security
import NebulaLink
import Postbox
import TelegramCore
import SwiftSignalKit

// App-scoped SOCKS5 bridge. No VPN, credential-field interception or Telegram session export.
public final class NebulaLinkService: NSObject {
    public static let shared = NebulaLinkService()
    private let queue = DispatchQueue(label: "app.nebulagram.nebulalink")
    private var accountManager: AccountManager<TelegramAccountManagerTypes>?
    private var initialized = false
    private var wanted = UserDefaults.standard.bool(forKey: "nebula.link.wanted")
    private var timer: DispatchSourceTimer?
    private var owned: ProxyServerSettings?
    private var previous: ProxySettings?
    private let proxyDisposable = MetaDisposable()
    private var routing = false
    private var routeActions: [(@escaping () -> Void) -> Void] = []
    public static let statusChanged = Notification.Name("NebulaLinkStatusChanged")
    // Updated/read on main only. 'connected' means local proxy enabled, not APNs delivery.
    public private(set) var state = "disconnected"

    private override init() {
        super.init()
        NotificationCenter.default.addObserver(self, selector: #selector(resume), name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    public func configure(accountManager: AccountManager<TelegramAccountManagerTypes>) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard self.accountManager == nil else { return }
        self.accountManager = accountManager
        if let data = Self.readLease(), let lease = try? JSONDecoder().decode(Lease.self, from: data) {
            owned = lease.owned
            previous = lease.previous
        }
        // Restore only our stale loopback lease; never disable a manually selected proxy.
        enqueueRoute { done in
            self.restoreProxy { done(); self.resume() }
        }
    }

    private struct Lease: Codable { let owned: ProxyServerSettings; let previous: ProxySettings }
    private static var keychainQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "app.nebulagram.proxy-lease", kSecAttrAccount as String: "previous-proxy"]
    }
    private static func readLease() -> Data? {
        var query = keychainQuery
        query[kSecReturnData as String] = true
        var result: CFTypeRef?
        return SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess ? result as? Data : nil
    }
    private static func writeLease(_ data: Data) -> Bool {
        let values: [String: Any] = [kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemUpdate(keychainQuery as CFDictionary, values as CFDictionary)
        if status == errSecSuccess { return true }
        guard status == errSecItemNotFound else { return false }
        return SecItemAdd(keychainQuery.merging(values) { _, b in b } as CFDictionary, nil) == errSecSuccess
    }

    private func initializeCore() throws {
        if initialized { return }
        var directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("NebulaLink", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                               attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication, .posixPermissions: 0o700])
        var resourceValues = URLResourceValues(); resourceValues.isExcludedFromBackup = true
        try directory.setResourceValues(resourceValues)
        _ = try Self.raw("core.init", ["dir": directory.path, "os": "iOS", "os_version": ProcessInfo.processInfo.operatingSystemVersionString, "model": "iPhone/iPad", "user_agent": "NebulaGram/iOS"])
        _ = try Self.raw("settings.set", ["mode": "proxy"])
        initialized = true
    }
    private enum Failure: Error { case core, input, unavailable }
    private static func raw(_ method: String, _ payload: [String: Any] = [:]) throws -> Any {
        let data = try JSONSerialization.data(withJSONObject: payload)
        guard let json = String(data: data, encoding: .utf8) else { throw Failure.input }
        let response = NebulalinkCall(method, json)
        guard let bytes = response.data(using: .utf8),
              let object = try JSONSerialization.jsonObject(with: bytes) as? [String: Any], object["ok"] as? Bool == true else { throw Failure.core }
        return object["data"] ?? [:]
    }

    @objc private func resume() {
        guard accountManager != nil else { return }
        queue.async { [weak self] in
            guard let self = self else { return }
            do {
                try self.initializeCore()
                let status = try Self.raw("tunnel.status") as? [String: Any]
                if self.wanted && (status?["state"] as? String) != "connected" { _ = try Self.raw("tunnel.start") }
                self.refreshStatus()
            } catch { self.publish("failed") }
            if self.timer == nil {
                let timer = DispatchSource.makeTimerSource(queue: self.queue)
                timer.schedule(deadline: .now() + 3, repeating: 3)
                timer.setEventHandler { [weak self] in self?.refreshStatus() }
                self.timer = timer; timer.resume()
            }
        }
    }

    public func call(_ method: String, payload: [String: Any] = [:], completion: @escaping (Result<Any, Error>) -> Void) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard accountManager != nil else { completion(.failure(Failure.unavailable)); return }
        if method == "tunnel.stop" {
            // Skipping NebulaLink must also work when core initialization/import failed.
            queue.async {
                self.wanted = false
                UserDefaults.standard.set(false, forKey: "nebula.link.wanted")
                if self.initialized { _ = try? Self.raw("tunnel.stop") }
                DispatchQueue.main.async {
                    self.enqueueRoute { done in
                        self.restoreProxy { self.publish("disconnected"); completion(.success([:])); done() }
                    }
                }
            }
            return
        }
        queue.async {
            do {
                try self.initializeCore()
                let result = try Self.raw(method, payload)
                if method == "onboarding.connect" || method == "tunnel.start" {
                    self.wanted = true
                    UserDefaults.standard.set(true, forKey: "nebula.link.wanted")
                } else if method == "tunnel.stop" {
                    self.wanted = false
                    UserDefaults.standard.set(false, forKey: "nebula.link.wanted")
                }
                if method == "onboarding.connect" || method.hasPrefix("tunnel.") {
                    self.refreshStatus { routed in completion(routed ? .success(result) : .failure(Failure.core)) }
                } else { DispatchQueue.main.async { completion(.success(result)) } }
            } catch {
                self.refreshStatus()
                DispatchQueue.main.async { completion(.failure(Failure.core)) }
            }
        }
    }

    private func publish(_ value: String) {
        DispatchQueue.main.async {
            guard self.state != value else { return }
            self.state = value
            NotificationCenter.default.post(name: Self.statusChanged, object: self)
        }
    }
    private func refreshStatus(completion: ((Bool) -> Void)? = nil) {
        guard initialized, let status = (try? Self.raw("tunnel.status")) as? [String: Any] else {
            DispatchQueue.main.async { completion?(false) }; return
        }
        let value = (status["state"] as? String) ?? "failed"
        let port = (status["socks_port"] as? Int) ?? 0
        let settings = (try? Self.raw("settings.get")) as? [String: Any]
        let routeCalls = (settings?["route_calls"] as? Bool) ?? false
        DispatchQueue.main.async {
            self.enqueueRoute { done in
                if value == "connected", (1...65535).contains(port) {
                    self.installProxy(port: Int32(port), routeCalls: routeCalls) { ok in
                        self.publish(ok ? "connected" : "failed"); completion?(ok); done()
                    }
                } else {
                    self.restoreProxy { self.publish(value); completion?(value == "disconnected"); done() }
                }
            }
        }
    }
    private func enqueueRoute(_ action: @escaping (@escaping () -> Void) -> Void) {
        routeActions.append(action)
        drainRoutes()
    }
    private func drainRoutes() {
        guard !routing, !routeActions.isEmpty else { return }
        routing = true
        let action = routeActions.removeFirst()
        action { self.routing = false; self.drainRoutes() }
    }
    private func installProxy(port: Int32, routeCalls: Bool, completion: @escaping (Bool) -> Void) {
        guard let manager = accountManager else { completion(false); return }
        let proxy = ProxyServerSettings(host: "127.0.0.1", port: port, connection: .socks5(username: nil, password: nil))
        if owned == proxy {
            var active = false
            proxyDisposable.set((updateProxySettingsInteractively(accountManager: manager) { current in
                active = current.effectiveActiveServer == proxy
                return current
            } |> deliverOnMainQueue).start(completed: { completion(active) }))
            return
        }
        // AccountManager serializes native settings updates. Only public proxy preferences are touched.
        var saved: ProxySettings?
        let former = owned
        let original = previous
        proxyDisposable.set((updateProxySettingsInteractively(accountManager: manager) { current in
            let restore = former != nil && current.activeServer == former ? (original ?? current) : current
            guard let data = try? JSONEncoder().encode(Lease(owned: proxy, previous: restore)), Self.writeLease(data) else { return current }
            saved = restore
            var updated = current
            if let former = former, original?.servers.contains(former) != true { updated.servers.removeAll { $0 == former } }
            if !updated.servers.contains(proxy) { updated.servers.append(proxy) }
            updated.enabled = true; updated.activeServer = proxy
            updated.useForCalls = routeCalls
            return updated
        } |> deliverOnMainQueue).start(completed: {
            if let saved = saved { self.previous = saved; self.owned = proxy }
            completion(saved != nil)
        }))
    }
    private func restoreProxy(completion: @escaping () -> Void) {
        guard let manager = accountManager, let owned = owned else { completion(); return }
        let previous = self.previous
        proxyDisposable.set((updateProxySettingsInteractively(accountManager: manager) { current in
            guard current.activeServer == owned else { return current }
            var updated = current
            updated.enabled = previous?.enabled ?? false
            updated.activeServer = previous?.activeServer
            updated.useForCalls = previous?.useForCalls ?? current.useForCalls
            if previous?.servers.contains(owned) != true { updated.servers.removeAll { $0 == owned } }
            return updated
        } |> deliverOnMainQueue).start(completed: {
            self.owned = nil; self.previous = nil
            SecItemDelete(Self.keychainQuery as CFDictionary)
            completion()
        }))
    }
}
