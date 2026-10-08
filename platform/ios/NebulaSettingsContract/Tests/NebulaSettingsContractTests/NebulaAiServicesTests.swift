import Foundation
import XCTest
@testable import NebulaSettingsContract

private final class ServiceSecretMemory: NebulaSecretStorage {
    var values: [String: String] = [:]
    var failWrites = false
    func secret(for account: String) throws -> String? { values[account] }
    func setSecret(_ value: String, for account: String) throws {
        if failWrites { throw NebulaAiSecrets.Failure.keychain(-1) }
        values[account] = value
    }
    func removeSecret(for account: String) throws { values.removeValue(forKey: account) }
}

final class NebulaAiServicesTests: XCTestCase {
    private func withStore(_ body: (UserDefaults, NebulaAiServices, NebulaAiSecrets, ServiceSecretMemory) throws -> Void) rethrows {
        let suite = "NebulaAiServicesTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let storage = ServiceSecretMemory()
        try body(defaults, NebulaAiServices(defaults: defaults), NebulaAiSecrets(storage: storage), storage)
    }
    func testMigrationIsRepeatableAndPreservesCredentials() throws {
        try withStore { defaults, store, secrets, _ in
            let settings = NebulaAiSettings(defaults: defaults)
            settings.provider = .gemini; settings.setModel("model", for: .gemini)
            try secrets.setKey("original-key", for: .gemini)
            try store.migrateLegacy(secrets: secrets)
            try store.migrateLegacy(secrets: secrets)
            XCTAssertEqual(store.connections.count, 1)
            XCTAssertEqual(store.active?.provider, .gemini)
            XCTAssertEqual(try settings.apiKey(secrets: secrets), "original-key")
            XCTAssertEqual(try secrets.key(for: .gemini), "original-key")
            let encoded = String(decoding: defaults.data(forKey: "nebula.ai.services.v1")!, as: UTF8.self)
            XCTAssertFalse(encoded.contains("original-key"))
        }
    }
    func testFailedKeyMigrationDoesNotCommitMetadata() throws {
        try withStore { defaults, store, secrets, storage in
            defaults.set("model", forKey: "nebula.ai.model_0")
            try secrets.setKey("key", for: .openAI)
            storage.failWrites = true
            XCTAssertThrowsError(try store.migrateLegacy(secrets: secrets))
            XCTAssertFalse(store.migrated)
            XCTAssertEqual(try secrets.key(for: .openAI), "key")
        }
    }
    func testSameProviderConnectionsHaveIndependentKeysAndDeletionDoesNotSelectAnother() throws {
        try withStore { defaults, store, secrets, _ in
            let first = NebulaAiConnection(name: "Work", provider: .openAI, model: "a")
            let second = NebulaAiConnection(name: "Work", provider: .openAI, model: "b")
            try store.save(first, apiKey: "first", secrets: secrets)
            try store.save(second, apiKey: "second", secrets: secrets)
            try store.select(second.id)
            let settings = NebulaAiSettings(defaults: defaults)
            XCTAssertEqual(settings.model(for: .openAI), "b")
            XCTAssertEqual(try settings.apiKey(secrets: secrets), "second")
            try store.remove(second.id, secrets: secrets)
            XCTAssertNil(store.active)
            XCTAssertFalse(settings.isConfigured(secrets: secrets))
            XCTAssertNil(try settings.apiKey(secrets: secrets))
            XCTAssertEqual(try secrets.serviceKey(id: first.id), "first")
            try secrets.removeAll()
            XCTAssertNil(try secrets.serviceKey(id: first.id))
        }
    }
    func testDamagedMetadataIsNotOverwrittenAndEndpointsAreValidated() throws {
        try withStore { defaults, store, secrets, _ in
            let original = Data("broken".utf8); defaults.set(original, forKey: "nebula.ai.services.v1")
            XCTAssertTrue(store.hasLoadError)
            XCTAssertThrowsError(try store.save(NebulaAiConnection(name: "New", provider: .openAI, model: "a"), apiKey: nil, secrets: secrets))
            XCTAssertEqual(defaults.data(forKey: "nebula.ai.services.v1"), original)
            for endpoint in ["http://example.com", "https://user:pass@example.com", "https://example.com?key=secret"] {
                XCTAssertFalse(NebulaAiConnection(name: "Custom", provider: .custom, model: "a", endpoint: endpoint).valid)
            }
        }
    }
    func testChangingServiceHostRequiresNewCredential() throws {
        try withStore { _, store, secrets, _ in
            var connection = NebulaAiConnection(name: "Custom", provider: .custom, model: "a", endpoint: "https://one.example")
            try store.save(connection, apiKey: "first", secrets: secrets)
            connection.endpoint = "https://two.example"
            XCTAssertThrowsError(try store.save(connection, apiKey: nil, secrets: secrets))
            XCTAssertEqual(store.connections.first?.endpoint, "https://one.example")
            try store.save(connection, apiKey: "second", secrets: secrets)
            XCTAssertEqual(try secrets.serviceKey(id: connection.id), "second")
        }
    }
    func testRolesAndGenerationBounds() throws {
        try withStore { defaults, _, _, _ in
            let settings = NebulaAiSettings(defaults: defaults)
            settings.temperature = .nan; XCTAssertEqual(settings.temperature, 1)
            settings.temperature = 10; XCTAssertEqual(settings.temperature, 2)
            XCTAssertTrue(settings.streaming)
            let role = NebulaAiRole(name: "My role", emoji: "🤖", instruction: "Be clear")
            try settings.roles.save(role); settings.roles.selectedId = role.id
            XCTAssertEqual(settings.roles.selected(russian: true), role)
            try settings.roles.remove(role.id); XCTAssertNil(settings.roles.selected(russian: true))
            let damaged = Data("broken".utf8)
            defaults.set(damaged, forKey: "nebula.ai.roles.v1")
            XCTAssertThrowsError(try settings.roles.save(role))
            XCTAssertEqual(defaults.data(forKey: "nebula.ai.roles.v1"), damaged)
        }
    }

    func testIncompleteLegacyRecoveryIsExplicitAndSurvivesFailure() throws {
        try withStore { defaults, store, secrets, storage in
            try secrets.setKey("original", for: .openAI)
            try store.migrateLegacy(secrets: secrets)
            XCTAssertTrue(store.connections.isEmpty)
            var draft = try XCTUnwrap(store.recoverableLegacy(secrets: secrets).first)
            XCTAssertNil(store.active)
            XCTAssertNil(try secrets.serviceKey(id: draft.id))
            draft.model = "model"; storage.failWrites = true
            XCTAssertThrowsError(try store.recoverLegacy(draft, apiKey: nil, secrets: secrets))
            XCTAssertTrue(store.connections.isEmpty)
            XCTAssertEqual(try store.recoverableLegacy(secrets: secrets).count, 1)
            storage.failWrites = false
            try store.recoverLegacy(draft, apiKey: nil, secrets: secrets)
            XCTAssertEqual(try secrets.serviceKey(id: draft.id), "original")
            XCTAssertEqual(try secrets.key(for: .openAI), "original")
            XCTAssertNil(store.active) // Saving recovery is not selection.
            try store.remove(draft.id, secrets: secrets)
            XCTAssertTrue(try store.recoverableLegacy(secrets: secrets).isEmpty)
            XCTAssertNil(defaults.string(forKey: "nebula.ai.model_0"))
        }
    }

    func testRecoveryDoesNotReuseKeyForDifferentDestination() throws {
        try withStore { defaults, store, secrets, _ in
            try secrets.setKey("old", for: .custom)
            defaults.set("model", forKey: "nebula.ai.model_3")
            defaults.set("http://old.example", forKey: "nebula.ai.endpoint")
            try store.migrateLegacy(secrets: secrets)
            var draft = try XCTUnwrap(store.recoverableLegacy(secrets: secrets).first)
            draft.endpoint = "https://new.example"
            XCTAssertThrowsError(try store.recoverLegacy(draft, apiKey: nil, secrets: secrets))
            draft.provider = .openAI
            XCTAssertThrowsError(try store.recoverLegacy(draft, apiKey: nil, secrets: secrets))
            try store.recoverLegacy(draft, apiKey: "new", secrets: secrets)
            XCTAssertEqual(try secrets.serviceKey(id: draft.id), "new")
            XCTAssertEqual(defaults.string(forKey: "nebula.ai.endpoint"), "http://old.example")
        }
    }

}
