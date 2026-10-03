import XCTest
@testable import NebulaSettingsContract

final class NebulaTranslationTests: XCTestCase {
    func testTranslationConsentIsIndependentAndAccountScoped() {
        let suite = "NebulaTranslationTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = NebulaTranslationPreferences(defaults: defaults)
        XCTAssertFalse(settings.composerShortcut)
        XCTAssertFalse(settings.options(account: "1", peer: "2").incoming)
        XCTAssertFalse(settings.options(account: "1", peer: "2").draft)
        XCTAssertFalse(settings.options(account: "1", peer: "2").outgoing)
        settings.update(account: "1", peer: "2") { $0.incoming = true; $0.draftLanguage = "de" }
        let restored = NebulaTranslationPreferences(defaults: defaults)
        XCTAssertTrue(restored.options(account: "1", peer: "2").incoming)
        XCTAssertFalse(restored.options(account: "1", peer: "2").draft)
        XCTAssertFalse(restored.options(account: "1", peer: "2").outgoing)
        XCTAssertEqual(restored.options(account: "1", peer: "2").draftLanguage, "de")
        XCTAssertFalse(restored.options(account: "3", peer: "2").incoming)
        XCTAssertFalse(restored.options(account: "1", peer: "3").incoming)
    }
    func testDelayIsBoundedAndFinite() {
        let suite = "NebulaTranslationTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = NebulaTranslationPreferences(defaults: defaults)
        XCTAssertEqual(settings.options(account: "a", peer: "p").delay, 0.3)
        for (input, expected) in [(-1.0, 0.15), (10, 2), (.infinity, 0.3), (.nan, 0.3), (1, 1)] {
            settings.update(account: "a", peer: "p") { $0.delay = input }
            XCTAssertEqual(settings.options(account: "a", peer: "p").delay, expected)
        }
    }
    func testSentConsentPersistsIndependentlyAndUsesMessageDirection() {
        let suite = "NebulaTranslationTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = NebulaTranslationPreferences(defaults: defaults)
        settings.update(account: "1", peer: "2") { $0.outgoing = true }
        let restored = NebulaTranslationPreferences(defaults: defaults)
        let options = restored.options(account: "1", peer: "2")
        XCTAssertTrue(options.translates(incoming: false))
        XCTAssertFalse(options.translates(incoming: true))
        XCTAssertFalse(options.draft)
        XCTAssertFalse(restored.options(account: "3", peer: "2").outgoing)
        XCTAssertFalse(restored.options(account: "1", peer: "3").outgoing)
        settings.update(account: "1", peer: "2") { $0.incoming = true; $0.outgoing = false }
        let incoming = settings.options(account: "1", peer: "2")
        XCTAssertTrue(incoming.translates(incoming: true))
        XCTAssertFalse(incoming.translates(incoming: false))
    }
    func testDismissedPreviewIsScopedToItsLanguage() {
        var gate = NebulaDraftRevision()
        gate.suppress("en:source")
        XCTAssertNil(gate.begin("en:source"))
        gate.cancel()
        XCTAssertNil(gate.begin("en:source"))
        XCTAssertNotNil(gate.begin("de:source"))
    }
    func testOutOfOrderResponsesAndReturningToSameText() {
        var gate = NebulaDraftRevision()
        let first = gate.begin("A:en")!
        XCTAssertNil(gate.begin("A:en"))
        let second = gate.begin("B:en")!
        let third = gate.begin("A:en")!
        XCTAssertFalse(gate.accepts(first)); XCTAssertFalse(gate.accepts(second)); XCTAssertTrue(gate.accepts(third))
        _ = gate.begin("A:de")
        XCTAssertFalse(gate.accepts(third))
        gate.cancel()
        XCTAssertFalse(gate.accepts(gate.revision))
        XCTAssertNotNil(gate.begin("A:en"))
    }
}
