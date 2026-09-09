import Foundation

public enum SettingsStoreError: Error {
    case unavailable
    case unsupportedControl(String)
}

/// Owns its subscription, not the store or its UI consumer.
public final class SettingsObservation {
    private let center: NotificationCenter
    private var token: NSObjectProtocol?
    private let lock = NSLock()

    fileprivate init(center: NotificationCenter, token: NSObjectProtocol) {
        self.center = center
        self.token = token
    }

    public func cancel() {
        lock.lock()
        let previous = token
        token = nil
        lock.unlock()
        if let previous { center.removeObserver(previous) }
    }

    deinit { cancel() }
}

/// Global presentation preferences only: never Telegram sessions or AI/VPN secrets.
/// The native iOS adapter is experimental; other imported v1 keys are retained,
/// not applied. Catalog defaults are not materialized into exports.
public final class NebulaSettingsStore {
    public static let shared = NebulaSettingsStore(defaults: .standard)
    public static let storageKey = "app.nebulagram.presentation.settings.v1"
    public static let editableKeys: Set<String> = ["hide_tab_counters", "show_stories", "settings_search_history", "glass_quality"]
    public static let maximumTransferBytes = 1024 * 1024

    private let defaults: UserDefaults
    private let catalog: SettingsCatalog?
    private let lock = NSLock()
    private let center = NotificationCenter()
    private let changed = Notification.Name("NebulaSettingsChanged")
    private var values: [String: SettingValue] = [:]
    private var failedToLoad = false

    public init(defaults: UserDefaults, catalog: SettingsCatalog? = try? SettingsCatalog.bundled()) {
        self.defaults = defaults
        self.catalog = catalog
        guard let catalog else { failedToLoad = true; return }
        guard let stored = defaults.object(forKey: Self.storageKey) else { return }
        do {
            guard let data = stored as? Data else { throw SettingsStoreError.unavailable }
            let document = try JSONDecoder().decode(SettingsDocument.self, from: data)
            try catalog.validate(document, localKeys: Self.editableKeys)
            values = document.settings
        } catch {
            // Keep corrupt/unsupported bytes intact. Only an explicit valid import
            // may replace them; opening the screen must never reset preferences.
            failedToLoad = true
        }
    }

    public var hasLoadError: Bool {
        lock.lock(); defer { lock.unlock() }
        return failedToLoad
    }

    public var hideTabCounters: Bool {
        lock.lock(); defer { lock.unlock() }
        if case let .boolean(value) = values["hide_tab_counters"] { return value }
        return false
    }

    public var glassQuality: Int {
        lock.lock(); defer { lock.unlock() }
        if case let .integer(value) = values["glass_quality"] { return max(0, min(2, value)) }
        return 0
    }
    public var showStories: Bool { boolean("show_stories", fallback: true) }
    public var settingsSearchHistory: Bool { boolean("settings_search_history", fallback: true) }
    private func boolean(_ key: String, fallback: Bool) -> Bool {
        lock.lock(); defer { lock.unlock() }
        if case let .boolean(value) = values[key] { return value }
        return fallback
    }

    public func exportData() throws -> Data {
        lock.lock(); defer { lock.unlock() }
        guard !failedToLoad else { throw SettingsStoreError.unavailable }
        guard let catalog else { throw SettingsStoreError.unavailable }
        let transferKeys = Set(catalog.settings.filter(\.transferV1).map(\.key))
        return try JSONEncoder().encode(SettingsDocument(settings: values.filter { transferKeys.contains($0.key) }))
    }

    public func set(_ value: SettingValue, for key: String) throws {
        guard Self.editableKeys.contains(key) else { throw SettingsStoreError.unsupportedControl(key) }
        try mutate(recover: false) { current in
            var next = current
            next[key] = value
            return SettingsDocument(settings: next)
        }
    }

    /// Explicit replacement, validated in full before storage or UI is changed.
    /// Returns keys retained for future ports, not currently activated on iOS.
    @discardableResult
    public func importData(_ data: Data) throws -> Set<String> {
        let preview = try previewImport(data)
        try mutate(recover: true) { current in
            guard let catalog = self.catalog else { throw SettingsStoreError.unavailable }
            let localKeys = Set(catalog.settings.filter { !$0.transferV1 }.map(\.key))
            var next = current.filter { localKeys.contains($0.key) }
            next.merge(preview.document.settings) { _, imported in imported }
            return SettingsDocument(settings: next)
        }
        return preview.pendingKeys
    }

    /// Read-only validation for confirmation UI; even a recovery import is not
    /// committed until the user confirms. The supplied bytes are bounded first.
    public func previewImport(_ data: Data) throws -> SettingsTransferPreview {
        guard data.count <= Self.maximumTransferBytes else { throw ContractError.invalidDocument }
        guard let catalog else { throw SettingsStoreError.unavailable }
        let document = try JSONDecoder().decode(SettingsDocument.self, from: data)
        try catalog.validate(document)
        return SettingsTransferPreview(document: document,
            activeKeys: Set(document.settings.keys).intersection(Self.editableKeys),
            pendingKeys: Set(document.settings.keys).subtracting(Self.editableKeys))
    }

    private func mutate(recover: Bool, document: ([String: SettingValue]) throws -> SettingsDocument) throws {
        lock.lock()
        do {
            guard let catalog, recover || !failedToLoad else { throw SettingsStoreError.unavailable }
            let next = try document(values)
            try catalog.validate(next, localKeys: Self.editableKeys)
            let data = try JSONEncoder().encode(next)
            let didChange = failedToLoad || values != next.settings
            if didChange {
                defaults.set(data, forKey: Self.storageKey)
                values = next.settings
                failedToLoad = false
            }
            lock.unlock()
            if didChange { center.post(name: changed, object: nil) }
        } catch {
            lock.unlock()
            throw error
        }
    }

    /// UI subscribers use the default main queue and read the latest snapshot.
    /// Pass nil only for synchronous, non-UI consumers/tests.
    public func observe(queue: OperationQueue? = .main, _ callback: @escaping () -> Void) -> SettingsObservation {
        let token = center.addObserver(forName: changed, object: nil, queue: queue) { _ in callback() }
        return SettingsObservation(center: center, token: token)
    }
}
