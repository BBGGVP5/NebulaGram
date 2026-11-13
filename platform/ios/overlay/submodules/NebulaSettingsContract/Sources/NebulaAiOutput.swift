import Foundation

/// How an AI editor result goes into the composer (Android `NebulaAiReplacements.insert`):
/// the answer alone (default) or after the original text, optionally as a quote.
public enum NebulaAiOutput {
    public static let responseOnlyKey = "nebula.ai.response_only"
    public static let quoteAnswerKey = "nebula.ai.quote_answer"

    public static func responseOnly(_ defaults: UserDefaults = .standard) -> Bool {
        defaults.object(forKey: responseOnlyKey) as? Bool ?? true
    }
    public static func setResponseOnly(_ value: Bool, _ defaults: UserDefaults = .standard) { defaults.set(value, forKey: responseOnlyKey) }
    public static func quoteAnswer(_ defaults: UserDefaults = .standard) -> Bool { defaults.bool(forKey: quoteAnswerKey) }
    public static func setQuoteAnswer(_ value: Bool, _ defaults: UserDefaults = .standard) { defaults.set(value, forKey: quoteAnswerKey) }

    /// The text to insert and the range the answer occupies in it.
    public static func compose(source: NSAttributedString, result: NSAttributedString, responseOnly: Bool) -> (text: NSAttributedString, answer: NSRange) {
        let output = NSMutableAttributedString()
        if !responseOnly && source.length > 0 {
            output.append(source)
            output.append(NSAttributedString(string: "\n\n"))
        }
        let start = output.length
        output.append(result)
        return (output, NSRange(location: start, length: result.length))
    }
}
