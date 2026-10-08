import Foundation

public struct NebulaIconPackManifest: Decodable {
    public let schemaVersion: Int?
    public let packId: String
    public let packName: String
    public let author: String?
    public let version: String?
    public let icons: [String: String]
    public enum Failure: Error { case invalid }
    public static func validPath(_ path: String) -> Bool {
        !path.isEmpty && path.count <= 200 && !path.contains("\\") && !path.contains(":") && !path.contains("\0") && path.split(separator: "/", omittingEmptySubsequences: false).allSatisfy { !$0.isEmpty && $0 != "." && $0 != ".." }
    }
    public static func read(_ data: Data, files: Set<String>) throws -> Self {
        guard data.count <= 131072 else { throw Failure.invalid }
        let value = try JSONDecoder().decode(Self.self, from: data)
        guard value.schemaVersion == nil || value.schemaVersion == 1,
              value.packId.range(of: "^[a-zA-Z0-9._-]{1,80}$", options: .regularExpression) != nil,
              !value.packName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, value.packName.count <= 100,
              (value.author?.count ?? 0) <= 100, (value.version?.count ?? 0) <= 64,
              !value.icons.isEmpty, value.icons.count <= 512 else { throw Failure.invalid }
        for (name, path) in value.icons {
            guard name.range(of: "^[a-z][a-z0-9_]{0,99}$", options: .regularExpression) != nil,
                  validPath(path), files.contains(path), ["svg", "png", "webp"].contains((path as NSString).pathExtension.lowercased()) else { throw Failure.invalid }
        }
        return value
    }
    public static func validateSVG(_ data: Data) throws -> String {
        guard data.count <= 262144, let source = String(data: data, encoding: .utf8) else { throw Failure.invalid }
        let lower = source.lowercased()
        guard lower.contains("<svg"), !["<!", "<script", "<image", "<foreignobject", "href", "url("].contains(where: { lower.contains($0) }),
              lower.range(of: "\\bon[a-z]+\\s*=", options: .regularExpression) == nil else { throw Failure.invalid }
        return source.replacingOccurrences(of: "currentColor", with: "#000000")
    }
}
