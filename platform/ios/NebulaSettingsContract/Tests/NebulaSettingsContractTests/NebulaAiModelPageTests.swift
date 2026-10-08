import XCTest
@testable import NebulaSettingsContract
final class NebulaAiModelPageTests: XCTestCase {
    func testCatalogValidationAndPaging() throws {
        let page = try NebulaAiModelPage.decode(Data(#"{"models":[{"name":"models/gemini-text","supportedGenerationMethods":["generateContent"]},{"name":"models/embedding","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"a?&=b"}"#.utf8), provider: .gemini)
        XCTAssertEqual(page.models, ["gemini-text"]); XCTAssertEqual(page.cursor, "a?&=b")
        let common = try NebulaAiModelPage.decode(Data(#"{"data":[{"id":"z"},{"id":"a"},{"id":"a"},{"id":""}],"has_more":true,"last_id":"z"}"#.utf8), provider: .claude)
        XCTAssertEqual(common.models, ["a", "z"]); XCTAssertEqual(common.cursor, "z")
        XCTAssertThrowsError(try NebulaAiModelPage.decode(Data(#"{"error":"denied"}"#.utf8), provider: .openAI))
        XCTAssertThrowsError(try NebulaAiModelPage.decode(Data(repeating: 32, count: 2_000_001), provider: .custom))
    }
}
