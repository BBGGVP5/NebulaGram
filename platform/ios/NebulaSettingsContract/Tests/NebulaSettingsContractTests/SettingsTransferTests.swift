import Foundation
import XCTest
@testable import NebulaSettingsContract

final class SettingsTransferTests: XCTestCase {
    func testPreviewIsReadOnlyAndRetainsPendingKeys() throws {
        let name = "NebulaTransferTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let store = NebulaSettingsStore(defaults: defaults)
        try store.set(.boolean(false), for: "hide_tab_counters")
        let saved = defaults.data(forKey: NebulaSettingsStore.storageKey)
        var calls = 0
        let token = store.observe(queue: nil) { calls += 1 }
        defer { token.cancel() }
        let data = try JSONEncoder().encode(SettingsDocument(settings: [
            "hide_tab_counters": .boolean(true), "centered_chat_header": .boolean(false)
        ]))
        let preview = try store.previewImport(data)
        XCTAssertEqual(preview.activeKeys, ["hide_tab_counters"])
        XCTAssertEqual(preview.pendingKeys, ["centered_chat_header"])
        XCTAssertFalse(store.hideTabCounters)
        XCTAssertEqual(defaults.data(forKey: NebulaSettingsStore.storageKey), saved)
        XCTAssertEqual(calls, 0)
        try store.importData(data)
        XCTAssertTrue(store.hideTabCounters)
        XCTAssertEqual(calls, 1)
        let exported = try JSONDecoder().decode(SettingsDocument.self, from: store.exportData())
        XCTAssertEqual(exported.settings, preview.document.settings)
    }

    func testInvalidAndOversizedPreviewDoesNotRecoverCorruption() throws {
        let name = "NebulaTransferTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let corrupt = Data("corrupt".utf8)
        defaults.set(corrupt, forKey: NebulaSettingsStore.storageKey)
        let store = NebulaSettingsStore(defaults: defaults)
        XCTAssertThrowsError(try store.previewImport(Data(repeating: 32, count: NebulaSettingsStore.maximumTransferBytes + 1)))
        XCTAssertThrowsError(try store.previewImport(corrupt))
        let empty = try JSONEncoder().encode(SettingsDocument(settings: [:]))
        XCTAssertTrue(try store.previewImport(empty).activeKeys.isEmpty)
        XCTAssertTrue(store.hasLoadError)
        XCTAssertEqual(defaults.data(forKey: NebulaSettingsStore.storageKey), corrupt)
        try store.importData(empty) // Explicit confirmation may replace with an empty document.
        XCTAssertFalse(store.hasLoadError)
        XCTAssertFalse(store.hideTabCounters)
    }

    func testBoundedFileReader() throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: file) }
        XCTAssertThrowsError(try SettingsTransferFile.read(file))
        XCTAssertThrowsError(try SettingsTransferFile.read(URL(string: "https://example.invalid/settings.json")!))
        let data = Data(repeating: 32, count: NebulaSettingsStore.maximumTransferBytes)
        try data.write(to: file)
        XCTAssertEqual(try SettingsTransferFile.read(file), data)
        try (data + Data([32])).write(to: file)
        XCTAssertThrowsError(try SettingsTransferFile.read(file))
    }
}
