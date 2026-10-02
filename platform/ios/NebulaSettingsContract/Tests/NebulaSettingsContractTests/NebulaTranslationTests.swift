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
        settings.update(account: "1", peer: "2") { $0.incoming = true; $0.draftLanguage = "de" }
        let restored = NebulaTranslationPreferences(defaults: defaults)
        XCTAssertTrue(restored.options(account: "1", peer: "2").incoming)
        XCTAssertFalse(restored.options(account: "1", peer: "2").draft)
        XCTAssertEqual(restored.options(account: "1", peer: "2").draftLanguage, "de")
        XCTAssertFalse(restored.options(account: "3", peer: "2").incoming)
        XCTAssertFalse(restored.options(account: "1", peer: "3").incoming)
    }
    func testDelayIsBoundedAndFinite() {
        let suite = "NebulaTranslationTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = NebulaTranslationPreferences(defaults: defaults)
        for (input, expected) in [(-1.0, 0.5), (10, 2), (.infinity, 1), (.nan, 1)] {
            settings.update(account: "a", peer: "p") { $0.delay = input }
            XCTAssertEqual(settings.options(account: "a", peer: "p").delay, expected)
        }
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
