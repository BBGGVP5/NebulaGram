import Foundation
import XCTest
@testable import NebulaSettingsContract

final class SettingsCatalogTests: XCTestCase {
    func testCatalogAndImplementationStatus() throws {
        let catalog = try SettingsCatalog.bundled()
        XCTAssertEqual(catalog.settings.count, 65)
        XCTAssertEqual(catalog.settings.filter(\.transferV1).count, 56)
        XCTAssertFalse(catalog.settings.contains(where: \.isImplementedOnIOS))
        XCTAssertEqual(catalog.settings.first { $0.key == "material_you" }?.iosStatus, "unsupported")
        XCTAssertEqual(catalog.settings.first { $0.key == "centered_chat_header" }?.defaultValue, .boolean(true))
        for setting in catalog.settings {
            if let value = setting.defaultValue { XCTAssertNoThrow(try setting.validate(value)) }
        }
    }

    func testTransferValidationDoesNotApplyDefaults() throws {
        let catalog = try SettingsCatalog.bundled()
        let document = SettingsDocument(settings: ["centered_chat_header": .boolean(true), "avatar_round": .integer(40)])
        try catalog.validate(document)
        let restored = try JSONDecoder().decode(SettingsDocument.self, from: JSONEncoder().encode(document))
        XCTAssertEqual(restored.settings, document.settings)
        try catalog.validate(SettingsDocument(settings: [:]))
        let invalid: [[String: SettingValue]] = [
            ["centered_chat_header": .integer(1)], ["avatar_round": .integer(101)],
            ["avatar_round": .integer(-1)], ["api_key": .string("secret")],
            ["glass_opacity": .integer(50)], // inventoried, not enabled in legacy v1 transfer
            ["bottom_bar_order": .string(String(repeating: "🫧", count: 513))]
        ]
        for values in invalid {
            XCTAssertThrowsError(try catalog.validate(SettingsDocument(settings: values)))
        }
        let future = try JSONDecoder().decode(SettingsDocument.self, from: Data("{\"format\":\"NebulaGram-settings\",\"version\":2,\"settings\":{}}".utf8))
        XCTAssertThrowsError(try catalog.validate(future))
    }

    func testRejectsNonScalarValues() {
        for json in ["null", "[]", "{}", "1.25"] {
            XCTAssertThrowsError(try JSONDecoder().decode(SettingValue.self, from: Data(json.utf8)))
        }
    }
}
