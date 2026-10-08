import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaBrowserRulesTests: XCTestCase {
    private func decoded(_ rules: NebulaBrowserRules) throws -> [[String: Any]] { try XCTUnwrap(JSONSerialization.jsonObject(with: Data(rules.json.utf8)) as? [[String: Any]]) }
    func testExceptionsFollowBlocksAndDoNotBlockTopLevelDocuments() throws {
        let rules = try NebulaBrowserRules(easyList: "@@||ads.example^\n||ads.example^$script,third-party\n##.advert", exclusions: ["allowed.example"])
        let result = try decoded(rules)
        XCTAssertEqual(rules.count, 3)
        XCTAssertEqual((result[0]["action"] as? [String: String])?["type"], "block")
        XCTAssertEqual((result[2]["action"] as? [String: String])?["type"], "ignore-previous-rules")
        XCTAssertEqual((result.last?["trigger"] as? [String: Any])?["if-domain"] as? [String], ["*allowed.example"])
        let trigger = try XCTUnwrap(result[0]["trigger"] as? [String: Any])
        XCTAssertEqual(trigger["resource-type"] as? [String], ["script"])
        XCTAssertEqual(trigger["load-type"] as? [String], ["third-party"])
        let regex = try NSRegularExpression(pattern: XCTUnwrap(trigger["url-filter"] as? String))
        for url in ["https://ads.example/ad.js", "https://sub.ads.example:443/ad.js"] { XCTAssertNotNil(regex.firstMatch(in: url, range: NSRange(location: 0, length: url.utf16.count))) }
        for url in ["https://notads.example/ad.js", "https://ads.example.evil/ad.js"] { XCTAssertNil(regex.firstMatch(in: url, range: NSRange(location: 0, length: url.utf16.count))) }
    }
    func testUnsupportedOptionsAndSelectorsAreSkipped() throws {
        let rules = try NebulaBrowserRules(easyList: "||ads.example^\n||track.example^$redirect=noop.js\n##body:has(.ad)\n##.a{display:none}\nnews.example##.banner")
        XCTAssertEqual(rules.count, 2)
        XCTAssertFalse(rules.json.contains("redirect")); XCTAssertFalse(rules.json.contains("body:has"))
        XCTAssertThrowsError(try NebulaBrowserRules(easyList: "not a filter"))
    }
    func testExclusionsValidationAndBounds() throws {
        XCTAssertEqual(try NebulaBrowserRules.exclusions("Example.COM, sub.example.com example.com"), ["example.com", "sub.example.com"])
        for value in ["example.com/path", "https://example.com", "user@example.com", "a..com", "-bad.com", "*.com"] { XCTAssertThrowsError(try NebulaBrowserRules.exclusions(value)) }
        XCTAssertThrowsError(try NebulaBrowserRules(easyList: String(repeating: "x", count: 5_000_001)))
    }
    func testFailedFilterUpdateKeepsOldDataAndIsOffByDefault() throws {
        let suite = "BrowserRulesTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!; defer { defaults.removePersistentDomain(forName: suite) }
        let store = NebulaBrowserPreferences(defaults: defaults)
        XCTAssertFalse(store.enabled)
        try store.commitValidatedFilter("||ads.example^")
        XCTAssertThrowsError(try store.commitValidatedFilter(String(repeating: "x", count: 5_000_001)))
        XCTAssertEqual(store.filter, "||ads.example^")
        XCTAssertEqual(try NebulaBrowserRules(easyList: NebulaBrowserRules.baseline).count, 18)
    }
}
