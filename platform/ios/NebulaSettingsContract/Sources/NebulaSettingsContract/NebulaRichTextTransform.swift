import Foundation

/// Carries native attributes as local objects. The model sees opaque boundaries,
/// never custom emoji IDs or arbitrary attribute payloads.
public struct NebulaRichTextTransform {
    public enum Failure: Error { case tooLarge, invalidBoundaries, changedProtectedText }
    public struct Segment {
        public let original: NSAttributedString
        public let protected: Bool
    }
    public let segments: [Segment]
    private let nonce: String
    public init(_ source: NSAttributedString) throws {
        guard source.length <= 50_000 else { throw Failure.tooLarge }
        nonce = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        var parts: [Segment] = []
        source.enumerateAttributes(in: NSRange(location: 0, length: source.length)) { attributes, range, _ in
            let protected = attributes.keys.contains { ["Attribute__CustomEmoji", "Attribute__Monospace", "Attribute__TextMention", "Attribute__CollapsedBlockquote"].contains($0.rawValue) }
            if protected { parts.append(Segment(original: source.attributedSubstring(from: range), protected: true)); return }
            var start = range.location, offset = range.location
            let text = source.attributedSubstring(from: range).string
            for character in text {
                let length = String(character).utf16.count
                let emoji = character.unicodeScalars.contains { $0.properties.isEmojiPresentation }
                    || character.unicodeScalars.contains { $0.value == 0xfe0f || $0.value == 0x20e3 }
                if emoji {
                    if offset > start { parts.append(Segment(original: source.attributedSubstring(from: NSRange(location: start, length: offset - start)), protected: false)) }
                    parts.append(Segment(original: source.attributedSubstring(from: NSRange(location: offset, length: length)), protected: true))
                    start = offset + length
                }
                offset += length
            }
            if offset > start { parts.append(Segment(original: source.attributedSubstring(from: NSRange(location: start, length: offset - start)), protected: false)) }
        }
        guard parts.count <= 256 else { throw Failure.tooLarge }
        segments = parts
    }
    private func opening(_ index: Int) -> String { "[NG:\(nonce):\(index)]" }
    private func closing(_ index: Int) -> String { "[/NG:\(nonce):\(index)]" }
    public var encoded: String {
        segments.enumerated().map { index, segment in opening(index) + (segment.protected ? "" : segment.original.string) + closing(index) }.joined()
    }
    public var instructions: String {
        "Transform the text within each NG boundary. Keep every opening and closing boundary exactly once, in its original order. Empty boundaries protect local emoji or code: leave them empty. Preserve line breaks, whitespace, emoji and meaning. Return only the marked text, without Markdown fences."
    }
    public func decode(_ value: String) throws -> NSAttributedString {
        let result = NSMutableAttributedString(string: "")
        var remaining = value[...]
        for (index, segment) in segments.enumerated() {
            let open = opening(index), close = closing(index)
            // No unmarked prefix, suffix, missing/reordered/duplicated boundary.
            guard remaining.hasPrefix(open) else { throw Failure.invalidBoundaries }
            remaining = remaining.dropFirst(open.count)
            guard let end = remaining.range(of: close) else { throw Failure.invalidBoundaries }
            let text = String(remaining[..<end.lowerBound])
            guard !text.contains(nonce) else { throw Failure.invalidBoundaries }
            if segment.protected {
                guard text.isEmpty else { throw Failure.changedProtectedText }
                result.append(segment.original)
            } else {
                guard !text.isEmpty || segment.original.string.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw Failure.invalidBoundaries }
                let attributes = segment.original.length > 0 ? segment.original.attributes(at: 0, effectiveRange: nil) : [:]
                result.append(NSAttributedString(string: text, attributes: attributes))
            }
            remaining = remaining[end.upperBound...]
        }
        guard remaining.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, result.length <= 100_000 else { throw Failure.invalidBoundaries }
        return result
    }
    /// Explicit segment fallback after a malformed response, preserving every
    /// original attribute and protected run. The caller supplies translations.
    public func combining(_ translations: [String]) throws -> NSAttributedString {
        guard translations.count == segments.count else { throw Failure.invalidBoundaries }
        let result = NSMutableAttributedString(string: "")
        for (index, segment) in segments.enumerated() {
            if segment.protected { result.append(segment.original) }
            else { result.append(NSAttributedString(string: translations[index], attributes: segment.original.attributes(at: 0, effectiveRange: nil))) }
        }
        guard result.length <= 100_000 else { throw Failure.tooLarge }
        return result
    }
}
