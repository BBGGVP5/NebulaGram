import Foundation

/// Explicit local cleanup. No requests, redirects, domain replacement or sending.
public enum NebulaLinkCleanup {
    private static let tracking: Set<String> = ["fbclid", "gclid", "dclid", "msclkid", "yclid", "igshid", "_openstat", "mc_cid", "mc_eid"]
    private static let protected: Set<String> = ["sig", "signature", "token", "access_token", "auth", "authorization", "key", "api_key", "code", "state", "expires", "expiry", "policy", "hmac", "hash", "client_secret", "session_id", "payment_id", "invoice"]
    private static let expression = try! NSRegularExpression(pattern: "https?://[^\\s<>\"']+", options: .caseInsensitive)

    public static func clean(_ text: String) -> String {
        guard !text.isEmpty, text.utf16.count <= 50_000 else { return text }
        let source = text as NSString
        let result = NSMutableString(string: text)
        for match in expression.matches(in: text, range: NSRange(location: 0, length: source.length)).reversed() {
            let candidate = source.substring(with: match.range) as NSString
            let end = trimEnd(candidate)
            let url = candidate.substring(to: end)
            result.replaceCharacters(in: NSRange(location: match.range.location, length: end), with: cleanURL(url))
        }
        return result as String
    }
    private static func trimEnd(_ value: NSString) -> Int {
        var balance = [0, 0, 0]
        for index in 0..<value.length {
            switch value.character(at: index) {
            case 40: balance[0] += 1
            case 41: balance[0] -= 1
            case 91: balance[1] += 1
            case 93: balance[1] -= 1
            case 123: balance[2] += 1
            case 125: balance[2] -= 1
            default: break
            }
        }
        var end = value.length
        while end > 0 {
            let last = value.character(at: end - 1)
            if last == 46 || last == 44 || last == 33 || last == 59 { end -= 1; continue }
            let bracket = last == 41 ? 0 : last == 93 ? 1 : last == 125 ? 2 : -1
            if bracket < 0 || balance[bracket] >= 0 { break }
            balance[bracket] += 1; end -= 1
        }
        return end
    }
    private static func key(_ part: String) -> String? {
        let raw = String(part.prefix(while: { $0 != "=" })).replacingOccurrences(of: "+", with: " ")
        guard let decoded = raw.removingPercentEncoding, !decoded.contains("\u{fffd}") else { return nil }
        return decoded.lowercased()
    }
    private static func protectedParts(_ value: String) -> Bool {
        for part in value.components(separatedBy: "&") {
            guard let key = key(part) else { return true }
            if protected.contains(key) || key.hasPrefix("x-amz-") || key.hasPrefix("x-goog-") { return true }
        }
        return false
    }
    private static func validEscapes(_ value: String) -> Bool {
        let bytes = Array(value.utf8)
        func hex(_ byte: UInt8) -> Bool { (48...57).contains(byte) || (65...70).contains(byte) || (97...102).contains(byte) }
        for index in bytes.indices where bytes[index] == 37 {
            if index + 2 >= bytes.count || !hex(bytes[index + 1]) || !hex(bytes[index + 2]) { return false }
        }
        return true
    }
    private static func cleanURL(_ value: String) -> String {
        guard value.utf16.count <= 8192, validEscapes(value), let components = URLComponents(string: value),
              let host = components.host, !host.isEmpty, components.user == nil,
              let question = value.firstIndex(of: "?") else { return value }
        let hash = value.firstIndex(of: "#") ?? value.endIndex
        guard question < hash else { return value }
        let query = String(value[value.index(after: question)..<hash])
        guard !protectedParts(query), hash == value.endIndex || !protectedParts(String(value[value.index(after: hash)...])) else { return value }
        var kept: [String] = []
        var changed = false
        for part in query.components(separatedBy: "&") {
            guard let key = key(part) else { return value }
            if key.hasPrefix("utm_") || tracking.contains(key) { changed = true } else { kept.append(part) }
        }
        guard changed else { return value }
        let rest = kept.joined(separator: "&")
        return String(value[..<question]) + (rest.isEmpty ? "" : "?" + rest) + String(value[hash...])
    }
}
