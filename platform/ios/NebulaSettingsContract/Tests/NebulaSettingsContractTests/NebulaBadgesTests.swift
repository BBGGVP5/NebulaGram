import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import XCTest
@testable import NebulaSettingsContract

final class NebulaBadgesTests: XCTestCase {
    private func payload(_ kind: String) -> Data { Data("{\"badge\":\"\(kind)\"}".utf8) }

    func testExistingServiceKindsAndMiraIdentity() throws {
        for kind in NebulaBadge.allCases {
            XCTAssertEqual(try NebulaBadges.decode(payload(kind.rawValue)), kind)
        }
        XCTAssertEqual(NebulaBadge.star.title, "Mira")
        XCTAssertNil(try NebulaBadges.decode(payload("")))
        XCTAssertNil(try NebulaBadges.decode(payload("unrecognized")))
        XCTAssertThrowsError(try NebulaBadges.decode(Data("{}".utf8)))
        XCTAssertThrowsError(try NebulaBadges.decode(Data("{\"badge\":7}".utf8)))
        XCTAssertThrowsError(try NebulaBadges.decode(Data(repeating: 32, count: 8193)))
    }

    func testInvalidUserIdsNeverUseNetwork() {
        let store = NebulaBadges { _, _ in XCTFail("Invalid ID sent to transport") }
        let done = expectation(description: "invalid IDs")
        done.expectedFulfillmentCount = 2
        for id in [Int64(0), -100] {
            store.badge(for: id) { badge in
                XCTAssertTrue(Thread.isMainThread)
                XCTAssertNil(badge)
                done.fulfill()
            }
        }
        wait(for: [done], timeout: 2)
    }

    func testCoalescesRequestsAndSeparatesUsers() {
        var requests: [Int64: (Result<Data, Error>) -> Void] = [:]
        var count = 0
        let store = NebulaBadges { id, callback in count += 1; requests[id] = callback }
        let done = expectation(description: "all listeners")
        done.expectedFulfillmentCount = 3
        for _ in 0..<2 {
            store.badge(for: 10) { badge in
                XCTAssertTrue(Thread.isMainThread)
                XCTAssertEqual(badge, .star)
                done.fulfill()
            }
        }
        store.badge(for: 20) { badge in XCTAssertEqual(badge, .supporter); done.fulfill() }
        XCTAssertEqual(count, 2)
        requests[20]?(.success(payload("supporter")))
        requests[10]?(.success(payload("star")))
        wait(for: [done], timeout: 2)
    }

    private func read(_ store: NebulaBadges, id: Int64 = 10) -> NebulaBadge? {
        let done = expectation(description: "read badge")
        var result: NebulaBadge?
        store.badge(for: id) { badge in
            XCTAssertTrue(Thread.isMainThread)
            result = badge
            done.fulfill()
        }
        wait(for: [done], timeout: 2)
        return result
    }

    func testPositiveAndNegativeCacheExpireAfterOneDay() {
        for kind in ["star", ""] {
            var date = Date(timeIntervalSince1970: 100)
            var count = 0
            let store = NebulaBadges(now: { date }) { _, callback in
                count += 1
                callback(.success(self.payload(kind)))
            }
            XCTAssertEqual(read(store), NebulaBadge(rawValue: kind))
            date = date.addingTimeInterval(86399)
            XCTAssertEqual(read(store), NebulaBadge(rawValue: kind))
            XCTAssertEqual(count, 1)
            date = date.addingTimeInterval(1)
            _ = read(store)
            XCTAssertEqual(count, 2)
        }
    }

    func testFailuresCanRetryAfterOneMinute() {
        for initial in [Result<Data, Error>.failure(NebulaBadges.FetchError.invalidResponse), .success(Data("bad json".utf8))] {
            var date = Date(timeIntervalSince1970: 100)
            var count = 0
            let store = NebulaBadges(now: { date }) { _, callback in
                count += 1
                callback(count == 1 ? initial : .success(self.payload("star")))
            }
            XCTAssertNil(read(store))
            date = date.addingTimeInterval(59)
            XCTAssertNil(read(store))
            XCTAssertEqual(count, 1)
            date = date.addingTimeInterval(1)
            XCTAssertEqual(read(store), .star)
            XCTAssertEqual(count, 2)
        }
    }

    func testBoundedCacheEvictsOldestEntry() {
        var date = Date(timeIntervalSince1970: 100)
        var count = 0
        let store = NebulaBadges(capacity: 2, now: { date }) { _, callback in
            count += 1
            callback(.success(self.payload("star")))
        }
        for id in [Int64(1), 2, 3] {
            _ = read(store, id: id)
            date = date.addingTimeInterval(1)
        }
        _ = read(store, id: 2)
        XCTAssertEqual(count, 3)
        _ = read(store, id: 1)
        XCTAssertEqual(count, 4)
    }

    func testHTTPValidation() throws {
        let url = URL(string: "https://example.invalid/v1/badge/1")!
        for status in [204, 301, 401, 404, 500] {
            let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)
            XCTAssertThrowsError(try NebulaBadges.responseData(payload("star"), response: response, error: nil).get())
        }
        let ok = HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: nil)
        XCTAssertEqual(try NebulaBadges.responseData(payload("star"), response: ok, error: nil).get(), payload("star"))
        XCTAssertThrowsError(try NebulaBadges.responseData(nil, response: ok, error: nil).get())
        XCTAssertThrowsError(try NebulaBadges.responseData(payload("star"), response: ok, error: NebulaBadges.FetchError.invalidResponse).get())
    }
}
