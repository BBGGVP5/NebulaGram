import XCTest
@testable import NebulaSettingsContract

final class NebulaTranslationActivityTests: XCTestCase {
    func testCancellationKeepsOtherOwnersAndAccountsIndependent() {
        let first = UUID(), second = UUID()
        let key = NebulaTranslationKey(account: "1", peer: "20", namespace: 0, message: 3)
        let other = NebulaTranslationKey(account: "2", peer: "20", namespace: 0, message: 3)
        defer { NebulaTranslationActivity.set(owner: first, key: nil); NebulaTranslationActivity.set(owner: second, key: nil) }
        NebulaTranslationActivity.set(owner: first, key: key)
        NebulaTranslationActivity.set(owner: second, key: key)
        XCTAssertTrue(NebulaTranslationActivity.contains(key))
        XCTAssertFalse(NebulaTranslationActivity.contains(other))
        NebulaTranslationActivity.set(owner: first, key: nil)
        XCTAssertTrue(NebulaTranslationActivity.contains(key))
        NebulaTranslationActivity.set(owner: second, key: other)
        XCTAssertFalse(NebulaTranslationActivity.contains(key))
        XCTAssertTrue(NebulaTranslationActivity.contains(other))
        NebulaTranslationActivity.set(owner: second, key: nil)
        XCTAssertFalse(NebulaTranslationActivity.contains(other))
    }
    func testNextRequestClearsPreviousMessageAndNamespace() {
        let owner = UUID()
        defer { NebulaTranslationActivity.set(owner: owner, key: nil) }
        let first = NebulaTranslationKey(account: "a", peer: "p", namespace: 0, message: 1)
        let next = NebulaTranslationKey(account: "a", peer: "p", namespace: 1, message: 1)
        NebulaTranslationActivity.set(owner: owner, key: first)
        NebulaTranslationActivity.set(owner: owner, key: next)
        XCTAssertFalse(NebulaTranslationActivity.contains(first))
        XCTAssertTrue(NebulaTranslationActivity.contains(next))
    }
}
