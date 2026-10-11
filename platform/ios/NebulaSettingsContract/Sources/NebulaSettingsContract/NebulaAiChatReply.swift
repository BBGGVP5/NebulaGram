import Foundation

/// Only ordinary chat uses this boundary; transformation text stays untouched.
public enum NebulaAiChatReply {
    public static func clean(_ value: String, partial: Bool = false) -> String {
        guard !value.isEmpty else { return "" }
        let text = value as NSString
        var start = 0
        while start < text.length {
            guard let scalar = UnicodeScalar(text.character(at: start)), CharacterSet.whitespacesAndNewlines.contains(scalar) else { break }
            start += 1
        }
        let leading = text.substring(from: start).lowercased()
        for marker in ["Assistant:", "**Assistant:**", "**Assistant**:"] {
            if leading.hasPrefix(marker.lowercased()) { return text.substring(from: contentStart(text, start + (marker as NSString).length)) }
            if partial, !leading.isEmpty, marker.lowercased().hasPrefix(leading) { return "" }
        }
        return value
    }
    private static func contentStart(_ text: NSString, _ initial: Int) -> Int {
        var index = initial
        while index < text.length, text.character(at: index) == 32 || text.character(at: index) == 9 { index += 1 }
        if index < text.length, text.character(at: index) == 10 || text.character(at: index) == 13 {
            let newline = text.character(at: index); index += 1
            if newline == 13, index < text.length, text.character(at: index) == 10 { index += 1 }
            while index < text.length {
                var end = index
                while end < text.length, text.character(at: end) == 32 || text.character(at: end) == 9 { end += 1 }
                if end == text.length { return end }
                if text.character(at: end) != 10 && text.character(at: end) != 13 { return index }
                let newline = text.character(at: end); index = end + 1
                if newline == 13, index < text.length, text.character(at: index) == 10 { index += 1 }
            }
        }
        return index
    }
}
