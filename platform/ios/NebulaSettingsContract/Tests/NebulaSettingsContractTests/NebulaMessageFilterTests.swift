import XCTest
@testable import NebulaSettingsContract

final class NebulaMessageFilterTests: XCTestCase {
    func testUnicodePhraseAndWordBoundaries() {
        var rules = NebulaFilterRules(); rules.words = "КОТ\nкупить сейчас"
        XCTAssertTrue(rules.matches("Котёнок")); rules.wholeWords = true
        XCTAssertFalse(rules.matches("Котёнок")); XCTAssertTrue(rules.matches("🐈 кот!"))
        XCTAssertTrue(rules.matches("Можно купить сейчас.")); XCTAssertFalse(rules.matches("кот_1"))
        rules.words = "привет"; rules.transliterate = true
        XCTAssertTrue(rules.matches("privet!")); XCTAssertTrue(rules.matches("ПРИВЕТ"))
        rules.words = "a+b"; XCTAssertTrue(rules.matches("a+b!")); XCTAssertFalse(rules.matches("aaab"))
    }
    func testAccountIsolationEligibilityRevealAndExceptions() throws {
        let suite = "FilterTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        let store = NebulaMessageFilter(defaults: defaults)
        var rules = NebulaFilterRules(); rules.words = "offer"
        try store.save(rules, account: 1)
        func hidden(_ account: Int64 = 1, incoming: Bool = true, sponsored: Bool = false) -> Bool { store.hidden(account: account, peer: 2, namespace: 0, id: 3, author: 4, text: "offer", incoming: incoming, sponsored: sponsored) }
        XCTAssertTrue(hidden()); XCTAssertFalse(hidden(2)); XCTAssertFalse(hidden(incoming: false)); XCTAssertFalse(hidden(sponsored: true))
        store.reveal(account: 1, peer: 2, namespace: 0, id: 3); XCTAssertFalse(hidden())
        rules.excludedPeers = [2]; try store.save(rules, account: 1); XCTAssertFalse(hidden())
        rules.excludedPeers = []; rules.words = ""; rules.blockedAuthors = true; try store.save(rules, account: 1)
        store.updateBlocked([4], account: 1); XCTAssertTrue(hidden()); store.updateBlocked([], account: 1); XCTAssertFalse(hidden())
        XCTAssertEqual(NebulaMessageFilter(defaults: defaults).rules(account: 1), rules)
    }
}
