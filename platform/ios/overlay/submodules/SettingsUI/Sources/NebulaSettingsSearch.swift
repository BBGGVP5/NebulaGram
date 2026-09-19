import Foundation

/// Transient local filtering; this does not store queries or touch Telegram search.
enum NebulaSettingsSearch {
    static func matches(_ query: String, in title: String) -> Bool {
        let options: String.CompareOptions = [.caseInsensitive, .diacriticInsensitive]
        let locale = Locale(identifier: "ru_RU")
        let text = title.folding(options: options, locale: locale)
        let words = query.folding(options: options, locale: locale).split(whereSeparator: { $0.isWhitespace })
        return words.allSatisfy { text.contains($0) }
    }
}
