import XCTest
@testable import NebulaSettingsContract

final class NebulaCloudSettingsTests: XCTestCase {
    let a = "11111111-1111-1111-1111-111111111111", b = "22222222-2222-2222-2222-222222222222"
    func testAndroidWireFormatAndValidation() throws {
        let text = "#NebulaGramSettingsV1\n{\"version\":1,\"device\":\"\(a)\",\"clock\":{\"\(a)\":1},\"settings\":{\"wide_posts\":true}}"
        let doc = try NebulaCloudSettingsDocument.parse(text)
        XCTAssertEqual(doc.settings["wide_posts"], .boolean(true))
        XCTAssertEqual(try NebulaCloudSettingsDocument.parse(doc.encode()), doc)
        XCTAssertThrowsError(try NebulaCloudSettingsDocument.parse(text.replacingOccurrences(of: "\"version\":1", with: "\"version\":2")))
        XCTAssertThrowsError(try NebulaCloudSettingsDocument.parse(text.replacingOccurrences(of: "wide_posts", with: "api_key")))
        XCTAssertThrowsError(try NebulaCloudSettingsDocument.parse(text + String(repeating: " ", count: 4000)))
        XCTAssertThrowsError(try NebulaCloudSettingsDocument(device: a, clock: [b: 1], settings: [:]))
        XCTAssertThrowsError(try NebulaCloudSettingsDocument(device: a, clock: [a: 1_000_000_001], settings: [:]))
    }
    func testConcurrentEditsAndExplicitMerge() throws {
        let one = try NebulaCloudSettingsDocument(device: a, clock: [a: 1], settings: [:])
        let two = try NebulaCloudSettingsDocument(device: b, clock: [b: 1], settings: ["wide_posts": .boolean(true)])
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: [:], base: [:], documents: [one, two]), .conflict)
        let next = try NebulaCloudSettingsDocument.next(device: a, documents: [one, two], settings: two.settings)
        XCTAssertTrue(next.dominates(one)); XCTAssertTrue(next.dominates(two))
        XCTAssertEqual(NebulaCloudSettingsDocument.heads([one, two, next]), [next])
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: [:], base: [:], documents: [next]), .apply(next))
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: [:], base: next.settings, documents: [next]), .publish)
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: next.settings, base: nil, documents: [next]), .unchanged)
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: [:], base: [:], documents: []), .deleted)
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: [:], base: nil, documents: []), .publish)
    }
    func testClockLimitsAndFirstEnableNeverOverwriteRemote() throws {
        let remote = try NebulaCloudSettingsDocument(device: b, clock: [b: 3], settings: ["wide_posts": .boolean(true)])
        XCTAssertEqual(NebulaCloudSettingsDocument.reconcile(local: [:], base: nil, documents: [remote]), .conflict)
        let stale = try NebulaCloudSettingsDocument(device: b, clock: [b: 2], settings: [:])
        XCTAssertEqual(NebulaCloudSettingsDocument.heads([remote, stale]), [remote])
        let exhausted = try NebulaCloudSettingsDocument(device: a, clock: [a: 1_000_000_000], settings: [:])
        XCTAssertThrowsError(try NebulaCloudSettingsDocument.next(device: a, documents: [exhausted], settings: [:]))
        var clock = [a: 1]
        for _ in 0..<24 { clock[UUID().uuidString.lowercased()] = 1 }
        XCTAssertThrowsError(try NebulaCloudSettingsDocument(device: a, clock: clock, settings: [:]))
        XCTAssertThrowsError(try NebulaCloudSettingsDocument(device: a, clock: [a: 1], settings: ["ios_glass_style": .integer(1)]))
    }
}
