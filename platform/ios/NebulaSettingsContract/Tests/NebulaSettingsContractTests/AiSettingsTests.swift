import Foundation
import XCTest
@testable import NebulaSettingsContract

private final class MemoryStorage: NebulaSecretStorage {
    var items: [String: String] = [:]
    func secret(for account: String) throws -> String? { items[account] }
    func setSecret(_ value: String, for account: String) throws { items[account] = value }
    func removeSecret(for account: String) throws { items.removeValue(forKey: account) }
}

final class AiSettingsTests: XCTestCase {
    private var suite: String!
    private var defaults: UserDefaults!
    private var settings: NebulaAiSettings!

    override func setUp() {
        super.setUp()
        suite = "NebulaAiSettingsTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suite)
        settings = NebulaAiSettings(defaults: defaults)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suite)
        super.tearDown()
    }

    func testDefaultsAreOffAndEmpty() {
        XCTAssertFalse(settings.enabled)
        XCTAssertEqual(settings.provider, .openAI)
        XCTAssertEqual(settings.model(for: .openAI), "")
        XCTAssertEqual(settings.customEndpoint, "")
        XCTAssertEqual(settings.instructions, "")
    }

    func testModelIsRememberedPerProvider() {
        settings.setModel("gpt-model", for: .openAI)
        settings.setModel("claude-model", for: .claude)
        XCTAssertEqual(settings.model(for: .openAI), "gpt-model")
        XCTAssertEqual(settings.model(for: .claude), "claude-model")
        XCTAssertEqual(settings.model(for: .gemini), "")
        settings.setModel("  spaced  ", for: .gemini)
        XCTAssertEqual(settings.model(for: .gemini), "spaced")
    }

    func testNamedProvidersIgnoreACustomEndpoint() {
        settings.customEndpoint = "https://leftover.example.com/v1"
        XCTAssertEqual(settings.endpoint(for: .openAI)?.host, "api.openai.com")
        XCTAssertEqual(settings.endpoint(for: .claude)?.host, "api.anthropic.com")
        XCTAssertEqual(settings.endpoint(for: .custom)?.host, "leftover.example.com")
    }

    func testOnlyHttpsIsAccepted() {
        // A key travels in a request header; plain http hands it to the network.
        for address in ["http://example.com/v1", "ftp://example.com", "example.com/v1", "https://", "  ", "not a url at all"] {
            settings.customEndpoint = address
            XCTAssertNil(settings.endpoint(for: .custom), "accepted \(address)")
        }
        settings.customEndpoint = "  https://example.com/v1  "
        XCTAssertEqual(settings.endpoint(for: .custom)?.absoluteString, "https://example.com/v1")
    }

    func testConfiguredNeedsAnAddressAModelAndAKey() {
        let storage = MemoryStorage()
        let secrets = NebulaAiSecrets(storage: storage)
        XCTAssertFalse(settings.isConfigured(secrets: secrets))
        settings.setModel("a-model", for: .openAI)
        XCTAssertFalse(settings.isConfigured(secrets: secrets), "a model alone is not a connection")
        try? secrets.setKey("a-key", for: .openAI)
        XCTAssertTrue(settings.isConfigured(secrets: secrets))
        // The key belongs to the provider, not to the app.
        settings.provider = .claude
        XCTAssertFalse(settings.isConfigured(secrets: secrets))
    }

    func testLongValuesAreBounded() {
        settings.setModel(String(repeating: "m", count: 400), for: .openAI)
        XCTAssertEqual(settings.model(for: .openAI).count, 256)
        settings.instructions = String(repeating: "i", count: 10_000)
        XCTAssertEqual(settings.instructions.count, 8192)
    }
}
