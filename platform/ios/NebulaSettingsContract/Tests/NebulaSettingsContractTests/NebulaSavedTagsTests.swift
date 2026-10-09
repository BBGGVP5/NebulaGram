import XCTest
@testable import NebulaSettingsContract

final class NebulaSavedTagsTests: XCTestCase {
    func testAccountIsolationPersistenceRenameAndRemoval() throws {
        let suite = "saved-labels-test-\(UUID().uuidString)", defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let first = try NebulaSavedTags(accountId: "1", defaults: defaults), second = try NebulaSavedTags(accountId: "2", defaults: defaults)
        let work = try first.create(" Работа "), personal = try first.create("Личное")
        try first.toggle(message: 42, label: work); try first.toggle(message: 7, label: work); try first.toggle(message: 42, label: personal)
        XCTAssertEqual(try first.messages(label: work), [42, 7]); XCTAssertTrue(try first.assigned(message: 42, label: personal)); XCTAssertTrue(try second.labels.isEmpty)
        XCTAssertThrowsError(try first.create("работа")); try first.rename(id: work, name: "Проект"); XCTAssertEqual(try first.labels.first?.name, "Проект")
        let reopened = try NebulaSavedTags(accountId: "1", defaults: defaults); XCTAssertEqual(try reopened.messages(label: work), [42, 7])
        try reopened.remove(id: work); XCTAssertTrue(try reopened.messages(label: work).isEmpty); XCTAssertTrue(try reopened.assigned(message: 42, label: personal))
        try reopened.toggle(message: 42, label: personal); XCTAssertFalse(try reopened.assigned(message: 42, label: personal))
    }
    func testUnicodeAndBounds() throws {
        let suite = "saved-labels-bounds-\(UUID().uuidString)", defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = try NebulaSavedTags(accountId: "42", defaults: defaults)
        _ = try store.create("Cafe\u{0301}"); XCTAssertThrowsError(try store.create("Café"))
        XCTAssertThrowsError(try store.create("\n")); XCTAssertThrowsError(try store.create("bad\u{0000}name")); XCTAssertThrowsError(try store.create(String(repeating: "a", count: 33)))
        let labels = try (0..<9).map { try store.create("label \($0)") }
        for id in labels.prefix(8) { try store.toggle(message: 1, label: id) }
        XCTAssertThrowsError(try store.toggle(message: 1, label: labels[8])); XCTAssertThrowsError(try store.toggle(message: 0, label: labels[0])); XCTAssertThrowsError(try store.toggle(message: 1, label: "missing"))
        XCTAssertThrowsError(try NebulaSavedTags(accountId: "0", defaults: defaults)); XCTAssertThrowsError(try NebulaSavedTags(accountId: "01", defaults: defaults))
    }
    func testDamagedMetadataIsNeverSilentlyOverwritten() throws {
        let suite = "saved-labels-corrupt-\(UUID().uuidString)", defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let key = "nebula.savedLabels.v1.42", bytes = Data(#"{"labels":[],"messages":{"1":["missing"]}}"#.utf8)
        defaults.set(bytes, forKey: key); let store = try NebulaSavedTags(accountId: "42", defaults: defaults)
        XCTAssertThrowsError(try store.labels); XCTAssertThrowsError(try store.create("new")); XCTAssertEqual(defaults.data(forKey: key), bytes)
    }
}
