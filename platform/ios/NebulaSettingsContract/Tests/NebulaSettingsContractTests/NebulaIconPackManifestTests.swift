import XCTest
@testable import NebulaSettingsContract

final class NebulaIconPackManifestTests: XCTestCase {
    func testPortableManifestAndPaths() throws {
        let bytes = Data(#"{"schemaVersion":1,"packId":"remix-1","packName":"Remix","icons":{"msg_search":"icons/search.svg"}}"#.utf8)
        XCTAssertEqual(try NebulaIconPackManifest.read(bytes, files: ["icons/search.svg"]).packName, "Remix")
        XCTAssertThrowsError(try NebulaIconPackManifest.read(bytes, files: []))
        for path in ["../x", "/x", "x//y", "x/./y", "x/../y", "x:y", "x\\y"] { XCTAssertFalse(NebulaIconPackManifest.validPath(path)) }
        XCTAssertTrue(NebulaIconPackManifest.validPath("icons/chat.svg"))
    }
    func testSVGRejectsExternalContent() throws {
        XCTAssertTrue(try NebulaIconPackManifest.validateSVG(Data(#"<svg><path fill="currentColor" d="M0 0"/></svg>"#.utf8)).contains("#000000"))
        for source in ["<!DOCTYPE svg><svg/>", "<svg><image href='https://x'/></svg>", "<svg onload='x'/>", "<svg><path fill='url(x)'/></svg>"] { XCTAssertThrowsError(try NebulaIconPackManifest.validateSVG(Data(source.utf8))) }
    }
}
