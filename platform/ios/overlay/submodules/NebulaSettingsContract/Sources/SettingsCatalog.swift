import Foundation

// This module describes/validates preferences. It does not apply them to Telegram.
public enum SettingValue: Codable, Equatable {
    case boolean(Bool)
    case integer(Int)
    case string(String)

    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if let v = try? value.decode(Bool.self) { self = .boolean(v) }
        else if let v = try? value.decode(Int.self) { self = .integer(v) }
        else if let v = try? value.decode(String.self) { self = .string(v) }
        else { throw DecodingError.dataCorruptedError(in: value, debugDescription: "Expected bool, integer or string") }
    }

    public func encode(to encoder: Encoder) throws {
        var value = encoder.singleValueContainer()
        switch self {
        case let .boolean(v): try value.encode(v)
        case let .integer(v): try value.encode(v)
        case let .string(v): try value.encode(v)
        }
    }
}

public enum ContractError: Error, Equatable {
    case invalidCatalog
    case invalidDocument
    case unknownSetting(String)
    case invalidValue(String)
}

public struct SettingDefinition: Decodable {
    public let key: String
    public let type: String
    public let feature: String
    public let androidStore: String
    public let transferV1: Bool
    public let iosStatus: String
    public let iosMapping: String
    public let defaultValue: SettingValue?
    public let defaultPolicy: String?
    public let min: Int?
    public let max: Int?
    public let maxLength: Int?

    enum CodingKeys: String, CodingKey {
        case key, type, feature, androidStore, transferV1, iosStatus, iosMapping
        case defaultValue = "default"
        case defaultPolicy, min, max, maxLength
    }

    public var isImplementedOnIOS: Bool { iosStatus == "implemented" }

    public func validate(_ value: SettingValue) throws {
        switch (type, value) {
        case ("boolean", .boolean): return
        case let ("integer", .integer(v)):
            if let min, let max, min <= max, (min...max).contains(v) { return }
        case let ("string", .string(v)):
            if let maxLength, v.utf16.count <= maxLength { return }
        default: break
        }
        throw ContractError.invalidValue(key)
    }
}

public struct SettingsDocument: Codable {
    public let format: String
    public let version: Int
    public let settings: [String: SettingValue]

    public init(settings: [String: SettingValue]) {
        format = "NebulaGram-settings"
        version = 1
        self.settings = settings
    }
}

public struct SettingsCatalog: Decodable {
    public let schemaVersion: Int
    public let settings: [SettingDefinition]

    public static func bundled() throws -> SettingsCatalog {
        #if SWIFT_PACKAGE
        guard let url = Bundle.module.url(forResource: "catalog", withExtension: "json") else {
            throw ContractError.invalidCatalog
        }
        let data = try Data(contentsOf: url)
        #else
        let data = Data(NebulaEmbeddedCatalog.json.utf8)
        #endif
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let catalog = try decoder.decode(Self.self, from: data)
        guard catalog.schemaVersion == 1, Set(catalog.settings.map(\.key)).count == catalog.settings.count else {
            throw ContractError.invalidCatalog
        }
        return catalog
    }

    /// Validate present keys only. Never fill missing values or silently activate planned UI.
    public func validate(_ document: SettingsDocument, localKeys: Set<String> = []) throws {
        guard document.format == "NebulaGram-settings", document.version == 1 else {
            throw ContractError.invalidDocument
        }
        let definitions = Dictionary(uniqueKeysWithValues: settings.map { ($0.key, $0) })
        for (key, value) in document.settings {
            guard let definition = definitions[key] else { throw ContractError.unknownSetting(key) }
            guard definition.transferV1 || localKeys.contains(key) else { throw ContractError.invalidValue(key) }
            try definition.validate(value)
        }
    }
}
