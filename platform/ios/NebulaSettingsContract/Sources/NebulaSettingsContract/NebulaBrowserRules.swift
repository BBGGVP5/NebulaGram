import Foundation

public struct NebulaBrowserRules {
    public enum Failure: Error { case tooLarge, invalidDomain, noSupportedRules }
    public let json: String
    public let count: Int
    public static let baseline = """
    ||doubleclick.net^
    ||googlesyndication.com^
    ||googleadservices.com^
    ||adnxs.com^
    ||adsrvr.org^
    ||criteo.com^
    ||pubmatic.com^
    ||rubiconproject.com^
    ||openx.net^
    ||taboola.com^
    ||outbrain.com^
    ||adform.net^
    ||advertising.com^
    ||casalemedia.com^
    ||moatads.com^
    ||amazon-adsystem.com^
    ##.adsbygoogle
    ##.ad-banner
    """
    public static func domain(_ input: String) -> String? {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard text.count <= 253, text.contains("."), !text.hasPrefix("."), !text.hasSuffix("."),
              text.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == ".") }),
              text.split(separator: ".", omittingEmptySubsequences: false).allSatisfy({ !$0.isEmpty && $0.count <= 63 && !$0.hasPrefix("-") && !$0.hasSuffix("-") }) else { return nil }
        return text
    }
    public static func exclusions(_ input: String) throws -> [String] {
        let parts = input.components(separatedBy: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ",;"))).filter { !$0.isEmpty }
        guard parts.count <= 128 else { throw Failure.tooLarge }
        let values = parts.compactMap(domain)
        guard values.count == parts.count else { throw Failure.invalidDomain }
        return Array(Set(values)).sorted()
    }
    public init(easyList: String, exclusions: [String] = []) throws {
        guard easyList.utf8.count <= 5_000_000, exclusions.count <= 128 else { throw Failure.tooLarge }
        guard exclusions.allSatisfy({ Self.domain($0) == $0 }) else { throw Failure.invalidDomain }
        var block: [[String: Any]] = [], allow: [[String: Any]] = []
        var seen = Set<String>()
        let cosmeticExceptions = Set(easyList.components(separatedBy: .newlines).compactMap { line -> String? in
            guard let marker = line.range(of: "#@#") else { return nil }
            return String(line[marker.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
        })
        let resources = ["image", "style-sheet", "script", "font", "raw", "svg-document", "media"]
        for raw in easyList.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard line.count <= 2048, !line.isEmpty, !line.hasPrefix("!"), !line.hasPrefix("["), seen.insert(line).inserted else { continue }
            guard block.count + allow.count < 20_000 else { throw Failure.tooLarge }
            if let split = line.range(of: "##") {
                let domains = String(line[..<split.lowerBound]), selector = String(line[split.upperBound...])
                // Deliberately accept only plain class/ID selectors. Procedural,
                // cosmetic exceptions and arbitrary executable syntax are skipped.
                guard !cosmeticExceptions.contains(selector), selector.count >= 2, selector.count <= 128, selector.first == "." || selector.first == "#",
                      selector.dropFirst().allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-" || $0 == "_") }),
                      let first = selector.dropFirst().first, first.isLetter || first == "_" || first == "-" else { continue }
                var trigger: [String: Any] = ["url-filter": ".*"]
                if !domains.isEmpty {
                    let values = domains.split(separator: ",").map(String.init)
                    guard values.allSatisfy({ Self.domain($0) != nil }) else { continue }
                    trigger["if-domain"] = values.map { "*" + $0 }
                }
                block.append(["trigger": trigger, "action": ["type": "css-display-none", "selector": selector]])
                continue
            }
            let exception = line.hasPrefix("@@")
            let value = exception ? String(line.dropFirst(2)) : line
            let pieces = value.split(separator: "$", omittingEmptySubsequences: false)
            guard pieces.count <= 2, let pattern = pieces.first, pattern.hasPrefix("||"), pattern.hasSuffix("^"),
                  let host = Self.domain(String(pattern.dropFirst(2).dropLast())) else { continue }
            var trigger: [String: Any] = ["url-filter": "^https?://([^/]+\\.)?" + host.replacingOccurrences(of: ".", with: "\\.") + "[:/]", "resource-type": resources]
            if pieces.count == 2 {
                let options = pieces[1].split(separator: ",").map(String.init)
                let mapping = ["image": "image", "stylesheet": "style-sheet", "script": "script", "font": "font", "xmlhttprequest": "raw", "media": "media"]
                guard options.allSatisfy({ $0 == "third-party" || $0 == "~third-party" || mapping[$0] != nil }), !(options.contains("third-party") && options.contains("~third-party")) else { continue }
                if options.contains("third-party") { trigger["load-type"] = ["third-party"] }
                if options.contains("~third-party") { trigger["load-type"] = ["first-party"] }
                let types = options.compactMap { mapping[$0] }; if !types.isEmpty { trigger["resource-type"] = types }
            }
            let rule: [String: Any] = ["trigger": trigger, "action": ["type": exception ? "ignore-previous-rules" : "block"]]
            if exception { allow.append(rule) } else { block.append(rule) }
        }
        guard !block.isEmpty else { throw Failure.noSupportedRules }
        count = block.count + allow.count
        // Exceptions follow all block/cosmetic rules. They affect this list only.
        if !exclusions.isEmpty { allow.append(["trigger": ["url-filter": ".*", "if-domain": exclusions.map { "*" + $0 }], "action": ["type": "ignore-previous-rules"]]) }
        let data = try JSONSerialization.data(withJSONObject: block + allow, options: [.sortedKeys])
        json = String(decoding: data, as: UTF8.self)
    }
}

public final class NebulaBrowserPreferences {
    public static let shared = NebulaBrowserPreferences()
    public static let changed = Notification.Name("NebulaBrowserRulesChanged")
    private let defaults: UserDefaults
    public init(defaults: UserDefaults = .standard) { self.defaults = defaults }
    public var enabled: Bool {
        get { defaults.bool(forKey: "nebula.browser.blockAds") }
        set { defaults.set(newValue, forKey: "nebula.browser.blockAds"); notify() }
    }
    public var exclusions: [String] { defaults.stringArray(forKey: "nebula.browser.exclusions") ?? [] }
    public var filter: String { defaults.string(forKey: "nebula.browser.easyList") ?? "" }
    public var updatedAt: Date? { defaults.object(forKey: "nebula.browser.updatedAt") as? Date }
    public func rules(filter: String? = nil, exclusions: [String]? = nil) throws -> NebulaBrowserRules {
        try NebulaBrowserRules(easyList: NebulaBrowserRules.baseline + "\n" + (filter ?? self.filter), exclusions: exclusions ?? self.exclusions)
    }
    public func saveExclusions(_ value: String) throws {
        let values = try NebulaBrowserRules.exclusions(value)
        defaults.set(values, forKey: "nebula.browser.exclusions"); notify()
    }
    /// Call only after native WebKit compilation succeeds; failures keep the old list.
    public func commitValidatedFilter(_ value: String) throws {
        _ = try rules(filter: value)
        defaults.set(value, forKey: "nebula.browser.easyList"); defaults.set(Date(), forKey: "nebula.browser.updatedAt"); notify()
    }
    private func notify() { NotificationCenter.default.post(name: Self.changed, object: nil) }
}
