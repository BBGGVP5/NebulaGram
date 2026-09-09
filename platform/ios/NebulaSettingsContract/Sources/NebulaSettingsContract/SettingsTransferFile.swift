import Foundation

public struct SettingsTransferPreview {
    public let document: SettingsDocument
    public let activeKeys: Set<String>
    public let pendingKeys: Set<String>
}

public enum SettingsTransferFile {
    /// The caller owns file-provider coordination/security scope. Stream rather
    /// than trusting a file-size attribute that may change during reading.
    public static func read(_ url: URL) throws -> Data {
        guard url.isFileURL, let stream = InputStream(url: url) else { throw ContractError.invalidDocument }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 8192)
        while true {
            let count = stream.read(&buffer, maxLength: min(buffer.count,
                NebulaSettingsStore.maximumTransferBytes + 1 - data.count))
            if count < 0 { throw stream.streamError ?? ContractError.invalidDocument }
            if count == 0 { return data }
            data.append(buffer, count: count)
            guard data.count <= NebulaSettingsStore.maximumTransferBytes else { throw ContractError.invalidDocument }
        }
    }
}
