import Foundation
import XCTest
@testable import NebulaSettingsContract

/// An in-memory stand-in for the keychain. A test process on a build machine
/// has no keychain to talk to, and these tests are about the policy around it.
private final class MemoryStorage: NebulaSecretStorage {
    var items: [String: String] = [:]
    var failure: OSStatus?

    func secret(for account: String) throws -> String? {
        if let failure { throw NebulaAiSecrets.Failure.keychain(failure) }
        return items[account]
    }
    func setSecret(_ value: String, for account: String) throws {
        if let failure { throw NebulaAiSecrets.Failure.keychain(failure) }
        items[account] = value
    }
    func removeSecret(for account: String) throws {
        if let failure { throw NebulaAiSecrets.Failure.keychain(failure) }
        items.removeValue(forKey: account)
    }
}

final class AiSecretsTests: XCTestCase {
    func testProviderNumbersMatchAndroid() {
        // Android writes these numbers into its own settings; a provider has to
        // mean the same thing on both sides.
        XCTAssertEqual(NebulaAiProvider.openAI.rawValue, 0)
        XCTAssertEqual(NebulaAiProvider.claude.rawValue, 1)
        XCTAssertEqual(NebulaAiProvider.gemini.rawValue, 2)
        XCTAssertEqual(NebulaAiProvider.custom.rawValue, 3)
        XCTAssertEqual(NebulaAiProvider.allCases.count, 4)
        XCTAssertNil(NebulaAiProvider.custom.endpoint)
        for provider in NebulaAiProvider.allCases where provider != .custom {
            XCTAssertEqual(provider.endpoint?.hasPrefix("https://"), true)
        }
    }

    func testKeysAreStoredPerProvider() throws {
        let storage = MemoryStorage()
        let secrets = NebulaAiSecrets(storage: storage)
        try secrets.setKey("first", for: .openAI)
        try secrets.setKey("second", for: .gemini)
        XCTAssertEqual(try secrets.key(for: .openAI), "first")
        XCTAssertEqual(try secrets.key(for: .gemini), "second")
        XCTAssertNil(try secrets.key(for: .claude))
        XCTAssertTrue(secrets.hasKey(for: .openAI))
        XCTAssertFalse(secrets.hasKey(for: .claude))
        XCTAssertEqual(storage.items.count, 2)
    }

    func testWhitespaceIsTrimmedAndAnEmptyValueRemovesTheKey() throws {
        let storage = MemoryStorage()
        let secrets = NebulaAiSecrets(storage: storage)
        try secrets.setKey("  padded-key\n", for: .claude)
        XCTAssertEqual(try secrets.key(for: .claude), "padded-key")
        try secrets.setKey("   ", for: .claude)
        XCTAssertNil(try secrets.key(for: .claude))
        XCTAssertTrue(storage.items.isEmpty)
    }

    func testRemoveAllLeavesNothingBehind() throws {
        let storage = MemoryStorage()
        let secrets = NebulaAiSecrets(storage: storage)
        for provider in NebulaAiProvider.allCases {
            try secrets.setKey("key-\(provider.rawValue)", for: provider)
        }
        XCTAssertEqual(storage.items.count, 4)
        try secrets.removeAll()
        XCTAssertTrue(storage.items.isEmpty)
    }

    func testAKeychainRefusalIsReportedRatherThanSwallowed() {
        let storage = MemoryStorage()
        storage.failure = errSecAuthFailed
        let secrets = NebulaAiSecrets(storage: storage)
        XCTAssertThrowsError(try secrets.setKey("value", for: .openAI)) { error in
            XCTAssertEqual(error as? NebulaAiSecrets.Failure, .keychain(errSecAuthFailed))
        }
        // hasKey answers a screen, not a security decision: a keychain that
        // will not talk to us means "no key to offer", not a crash.
        XCTAssertFalse(secrets.hasKey(for: .openAI))
    }

    func testCredentialsAreNotPartOfTheSettingsTransfer() throws {
        // The catalog describes presentation; exportData can only serialise
        // what the catalog names, so a key has no route out through an export.
        let catalog = try SettingsCatalog.bundled()
        for provider in NebulaAiProvider.allCases {
            XCTAssertFalse(catalog.settings.contains { $0.key.contains("provider-\(provider.rawValue)") })
        }
        XCTAssertFalse(catalog.settings.contains { $0.key.lowercased().contains("api_key") })
        XCTAssertFalse(catalog.settings.contains { $0.key.lowercased().contains("secret") })
    }
}
