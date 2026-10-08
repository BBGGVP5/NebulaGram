import UIKit
import AccountContext
import TelegramCore
import SwiftSignalKit
import NebulaSettingsContract

/// Main-thread coordinator. One selected account owns global presentation sync.
public final class NebulaCloudSettingsSync {
    public static let shared = NebulaCloudSettingsSync()
    static let changed = Notification.Name("NebulaCloudSyncChanged")
    private weak var context: AccountContext?
    private let store = NebulaSettingsStore.shared
    private let defaults = UserDefaults.standard
    private let request = MetaDisposable()
    private var observation: SettingsObservation?
    private var scheduled: Foundation.Timer?, deadline: Foundation.Timer?
    private var generation = 0, device = ""
    private var directory: URL?
    private var applying = false, foreground = false, storageFailed = false
    private struct State: Codable {
        var base: [String: SettingValue]?
        var time: Double = 0
        var pending: String?
        var random: Int64 = 0
    }
    private var state = State()
    private(set) var busy = false
    private(set) var status = ""
    private(set) var choices: [NebulaCloudSettingsDocument] = []
    var accountId: String? { context.map { String($0.account.peerId.toInt64()) } }
    var enabled: Bool { !device.isEmpty && defaults.string(forKey: "nebula.cloud.device") == device && defaults.string(forKey: "nebula.cloud.owner") == accountId && accountId != nil }
    var lastTime: Double { state.time }
    private func text(_ ru: String, _ en: String) -> String { context?.sharedContext.currentPresentationData.with { $0.strings.baseLanguageCode.hasPrefix("ru") } == true ? ru : en }
    private func announce(_ value: String) { status = value; NotificationCenter.default.post(name: Self.changed, object: nil) }
    private init() {
        NotificationCenter.default.addObserver(self, selector: #selector(resume), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(pause), name: UIApplication.willResignActiveNotification, object: nil)
        observation = store.observe { [weak self] in guard let self, !self.applying else { return }; self.schedule(3) }
    }
    public func bind(context: AccountContext) {
        pause(); self.context = context; state = State(); storageFailed = false; choices = []; device = ""; directory = nil; status = ""
        do {
            var directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("NebulaCloudSync", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var values = URLResourceValues(); values.isExcludedFromBackup = true; try directory.setResourceValues(values)
            self.directory = directory
            let idFile = directory.appendingPathComponent("installation")
            if FileManager.default.fileExists(atPath: idFile.path) {
                device = String(decoding: try SettingsTransferFile.read(idFile), as: UTF8.self)
                guard device.count == 36, device == device.lowercased(), UUID(uuidString: device) != nil else { throw ContractError.invalidDocument }
            } else { device = UUID().uuidString.lowercased(); try Data(device.utf8).write(to: idFile, options: .atomic) }
            if let file = stateFile(), FileManager.default.fileExists(atPath: file.path) {
                let bytes = try SettingsTransferFile.read(file)
                let loaded = try JSONDecoder().decode(State.self, from: bytes)
                if let base = loaded.base { try SettingsCatalog.bundled().validate(SettingsDocument(settings: base)) }
                if let pending = loaded.pending { guard try NebulaCloudSettingsDocument.parse(pending).device == device, loaded.random != 0 else { throw ContractError.invalidDocument } }
                state = loaded
            }
        } catch { storageFailed = true; announce(text("Не удалось прочитать состояние синхронизации", "Could not read sync state")) }
        foreground = UIApplication.shared.applicationState == .active; schedule(1)
    }
    public func unbind(context: AccountContext) { if self.context === context { pause(); self.context = nil } }
    private func stateFile() -> URL? { guard let id = accountId else { return nil }; return directory?.appendingPathComponent("account-\(id).json") }
    private func persist(_ value: State) throws {
        guard !storageFailed, let file = stateFile() else { throw ContractError.invalidDocument }
        try JSONEncoder().encode(value).write(to: file, options: .atomic); state = value
    }
    func setEnabled(_ value: Bool) {
        guard !value || (!storageFailed && accountId != nil && !device.isEmpty) else { announce(text("Не удалось прочитать состояние синхронизации", "Could not read sync state")); return }
        pause(); choices = []
        if value, !storageFailed, let id = accountId { defaults.set(device, forKey: "nebula.cloud.device"); defaults.set(id, forKey: "nebula.cloud.owner") }
        else { defaults.removeObject(forKey: "nebula.cloud.owner") }
        foreground = UIApplication.shared.applicationState == .active
        announce(text(value ? "Проверяем «Избранное»…" : "Синхронизация выключена", value ? "Checking Saved Messages…" : "Sync is off")); schedule(0.1)
    }
    @objc private func resume() { foreground = true; schedule(1) }
    @objc private func pause() { foreground = false; generation += 1; busy = false; request.set(nil); deadline?.invalidate(); scheduled?.invalidate() }
    private func schedule(_ seconds: Double) {
        scheduled?.invalidate(); guard enabled, foreground, !storageFailed else { return }
        scheduled = Foundation.Timer.scheduledTimer(withTimeInterval: seconds, repeats: false) { [weak self] _ in self?.sync() }
    }
    private func snapshot() throws -> [String: SettingValue] { try JSONDecoder().decode(SettingsDocument.self, from: store.exportData()).settings }
    private func valid(_ token: Int) -> Bool { token == generation && enabled && foreground && !storageFailed }
    func sync(keepLocal: Bool = false, selected: NebulaCloudSettingsDocument? = nil) {
        guard !busy, enabled, foreground, !storageFailed, let context else { return }
        do {
            let captured = try snapshot(); busy = true; generation += 1; let token = generation
            announce(text("Синхронизация…", "Syncing…"))
            deadline?.invalidate(); deadline = Foundation.Timer.scheduledTimer(withTimeInterval: 45, repeats: false) { [weak self] _ in guard let self, self.valid(token) else { return }; self.fail() }
            read(context: context, offset: 0, pages: 0, documents: [], own: nil, captured: captured, token: token, keepLocal: keepLocal, selected: selected)
        } catch { fail() }
    }
    private func read(context: AccountContext, offset: Int32, pages: Int, documents: [NebulaCloudSettingsDocument], own: Int32?, captured: [String: SettingValue], token: Int, keepLocal: Bool, selected: NebulaCloudSettingsDocument?) {
        request.set((NebulaCloudSettingsTransport.read(account: context.account, offset: offset) |> deliverOnMainQueue).start(next: { [weak self] page in
            guard let self, self.valid(token) else { return }
            do {
                var docs = documents; var own = own
                for record in page.records {
                    let document = try NebulaCloudSettingsDocument.parse(record.text); docs.append(document)
                    if document.device == self.device { own = max(own ?? 0, record.id) }
                }
                if let next = page.next {
                    guard pages < 3, next > 0, next != offset else { throw ContractError.invalidDocument }
                    self.read(context: context, offset: next, pages: pages + 1, documents: docs, own: own, captured: captured, token: token, keepLocal: keepLocal, selected: selected); return
                }
                guard captured == (try self.snapshot()) else { self.finishWork(); self.schedule(3); return }
                if own != nil, self.state.pending != nil { var state = self.state; state.pending = nil; state.random = 0; try self.persist(state) }
                let heads = NebulaCloudSettingsDocument.heads(docs)
                self.choices = heads.reduce(into: []) { result, doc in if !result.contains(where: { $0.settings == doc.settings }) { result.append(doc) } }
                if let selected {
                    guard heads.contains(selected) else { self.conflict(); return }
                    try self.publish(context: context, docs: docs, own: own, values: selected.settings, captured: captured, token: token, apply: true); return
                }
                if keepLocal { try self.publish(context: context, docs: docs, own: own, values: captured, captured: captured, token: token, apply: false); return }
                switch NebulaCloudSettingsDocument.reconcile(local: captured, base: self.state.base, documents: docs) {
                case .unchanged: try self.done(captured)
                case .publish: try self.publish(context: context, docs: docs, own: own, values: captured, captured: captured, token: token, apply: false)
                case let .apply(remote): try self.apply(remote.settings); try self.done(remote.settings)
                case .conflict: self.conflict()
                case .deleted: self.finishWork(); self.announce(self.text("Копия удалена. Можно сохранить настройки заново.", "The cloud copy was deleted. You can save settings again."))
                }
            } catch { self.fail() }
        }, error: { [weak self] _ in guard let self, self.valid(token) else { return }; self.fail() }))
    }
    private func publish(context: AccountContext, docs: [NebulaCloudSettingsDocument], own: Int32?, values: [String: SettingValue], captured: [String: SettingValue], token: Int, apply: Bool) throws {
        var document = try NebulaCloudSettingsDocument.next(device: device, documents: docs, settings: values)
        var random = Int64.random(in: 1...Int64.max); var applyAfter = apply
        if own == nil {
            if let pending = state.pending { document = try .parse(pending); random = state.random; applyAfter = false }
            else { var state = state; state.pending = try document.encode(); state.random = random; try persist(state) }
        }
        let published = document.settings
        request.set((NebulaCloudSettingsTransport.write(account: context.account, id: own, text: try document.encode(), randomId: random) |> deliverOnMainQueue).start(next: { [weak self] in
            guard let self, self.valid(token) else { return }
            do {
                var state = self.state; state.pending = nil; state.random = 0; try self.persist(state)
                if applyAfter, captured == (try self.snapshot()) { try self.apply(published) }
                try self.done(published)
            } catch { self.fail() }
        }, error: { [weak self] _ in guard let self, self.valid(token) else { return }; self.fail() }))
    }
    private func apply(_ values: [String: SettingValue]) throws { applying = true; defer { applying = false }; try store.importData(JSONEncoder().encode(SettingsDocument(settings: values))) }
    private func done(_ values: [String: SettingValue]) throws {
        var state = state; state.base = values; state.time = Date().timeIntervalSince1970; try persist(state)
        finishWork(); choices = []; announce(text("Настройки синхронизированы", "Settings are up to date")); schedule((try snapshot()) == values ? 300 : 3)
    }
    private func finishWork() { busy = false; deadline?.invalidate() }
    private func conflict() { finishWork(); announce(text("Настройки отличаются. Выберите версию для устройств.", "Settings differ. Choose the version to use on your devices.")) }
    private func fail() { generation += 1; request.set(nil); finishWork(); announce(text("Не удалось синхронизировать. Проверьте соединение и формат копии.", "Could not sync. Check the connection and snapshot format.")); schedule(300) }
}
