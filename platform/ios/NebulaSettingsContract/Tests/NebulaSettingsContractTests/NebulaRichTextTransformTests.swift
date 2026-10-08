import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaRichTextTransformTests: XCTestCase {
    func testFormattingSurrogatesAndCustomEmojiRoundTrip() throws {
        let source = NSMutableAttributedString(string: "Hello 🧰 world")
        source.addAttribute(NSAttributedString.Key("Attribute__Bold"), value: true, range: NSRange(location: 0, length: source.length))
        source.addAttribute(NSAttributedString.Key("Attribute__CustomEmoji"), value: "document-123", range: NSRange(location: 6, length: 2))
        source.addAttribute(NSAttributedString.Key("Attribute__TextUrl"), value: "https://example.com", range: NSRange(location: 9, length: 5))
        let transform = try NebulaRichTextTransform(source)
        XCTAssertFalse(transform.encoded.contains("document-123"))
        XCTAssertFalse(transform.encoded.contains("🧰"))
        let result = try transform.decode(transform.encoded.replacingOccurrences(of: "Hello", with: "Привет").replacingOccurrences(of: "world", with: "мир"))
        XCTAssertEqual(result.string, "Привет 🧰 мир")
        XCTAssertEqual(result.attribute(NSAttributedString.Key("Attribute__CustomEmoji"), at: 7, effectiveRange: nil) as? String, "document-123")
        XCTAssertEqual(result.attribute(NSAttributedString.Key("Attribute__TextUrl"), at: 10, effectiveRange: nil) as? String, "https://example.com")
        XCTAssertEqual(result.attribute(NSAttributedString.Key("Attribute__Bold"), at: 10, effectiveRange: nil) as? Bool, true)
    }
    func testOrdinaryEmojiAndLiteralMarkersAreProtected() throws {
        let source = NSAttributedString(string: "Hi 👩🏽‍💻 🇷🇺 1️⃣ [NG:literal:0]")
        let transform = try NebulaRichTextTransform(source)
        XCTAssertFalse(transform.encoded.contains("👩🏽‍💻"))
        XCTAssertEqual(try transform.decode(transform.encoded).string, source.string)
        XCTAssertEqual(try transform.combining(transform.segments.map { $0.protected ? "invented" : $0.original.string }).string, source.string)
    }
    func testMissingDuplicateAndInventedBoundariesFailWithoutChangingSource() throws {
        let original = NSAttributedString(string: "Hello", attributes: [NSAttributedString.Key("Attribute__Spoiler"): true])
        let transform = try NebulaRichTextTransform(original)
        XCTAssertThrowsError(try transform.decode("Hello"))
        XCTAssertThrowsError(try transform.decode(transform.encoded + transform.encoded))
        XCTAssertThrowsError(try transform.decode(transform.encoded.replacingOccurrences(of: "Hello", with: "")))
        XCTAssertEqual(original.string, "Hello")
    }
    func testCodeAndCollapsedQuotesNeverGoToTheModel() throws {
        let source = NSMutableAttributedString(string: "secret() quote")
        source.addAttribute(NSAttributedString.Key("Attribute__Monospace"), value: true, range: NSRange(location: 0, length: 8))
        source.addAttribute(NSAttributedString.Key("Attribute__CollapsedBlockquote"), value: NSAttributedString(string: "hidden quote"), range: NSRange(location: 9, length: 5))
        let transform = try NebulaRichTextTransform(source)
        XCTAssertFalse(transform.encoded.contains("secret()")); XCTAssertFalse(transform.encoded.contains("hidden quote"))
        XCTAssertTrue(try transform.decode(transform.encoded).isEqual(to: source))
    }
}
