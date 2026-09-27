import Foundation
import XCTest
@testable import NebulaSettingsContract

private final class ChatLockMemoryStorage: NebulaSecretStorage {
    var values: [String: String] = [:]
    func secret(for account: String) throws -> String? { values[account] }
    func setSecret(_ value: String, for account: String) throws { values[account] = value }
    func removeSecret(for account: String) throws { values.removeValue(forKey: account) }
}

final class NebulaChatLocksTests: XCTestCase {
    func testPinAndPasswordArePerAccountAndPeer() throws {
        let storage = ChatLockMemoryStorage()
        let locks = NebulaChatLocks(storage: storage)
        XCTAssertFalse(try locks.hasLock(account: 1, peer: 20))
        XCTAssertThrowsError(try locks.set(account: 1, peer: 20, current: nil, new: "12a4", mode: .pin))
        try locks.set(account: 1, peer: 20, current: nil, new: "1234", mode: .pin)
        XCTAssertTrue(try locks.hasLock(account: 1, peer: 20))
        XCTAssertFalse(try locks.hasLock(account: 2, peer: 20))
        XCTAssertFalse(try locks.hasLock(account: 1, peer: 21))
        XCTAssertEqual(try locks.mode(account: 1, peer: 20), .pin)
        XCTAssertFalse(try locks.verify(account: 1, peer: 20, credential: "4321"))
        XCTAssertTrue(try locks.verify(account: 1, peer: 20, credential: "1234"))
        XCTAssertThrowsError(try locks.set(account: 1, peer: 20, current: "4321", new: "long password", mode: .password))
        try locks.set(account: 1, peer: 20, current: "1234", new: "long password", mode: .password)
        XCTAssertEqual(try locks.mode(account: 1, peer: 20), .password)
        try locks.remove(account: 1, peer: 20, current: "long password")
        XCTAssertFalse(try locks.hasLock(account: 1, peer: 20))
        XCTAssertEqual(storage.values.count, 0)
    }

    func testFiveFailuresThrottleAcrossStoreInstances() throws {
        let storage = ChatLockMemoryStorage()
        var now = Date(timeIntervalSince1970: 1_000)
        let locks = NebulaChatLocks(storage: storage, clock: { now })
        try locks.set(account: 1, peer: 2, current: nil, new: "1234", mode: .pin)
        for _ in 0..<5 { XCTAssertFalse(try locks.verify(account: 1, peer: 2, credential: "0000")) }
        let restarted = NebulaChatLocks(storage: storage, clock: { now })
        XCTAssertThrowsError(try restarted.verify(account: 1, peer: 2, credential: "1234"))
        now.addTimeInterval(31)
        XCTAssertTrue(try restarted.verify(account: 1, peer: 2, credential: "1234"))
    }
}
