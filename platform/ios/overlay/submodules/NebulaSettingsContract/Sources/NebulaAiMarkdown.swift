import Foundation

/// Bounded Markdown subset. HTML and URLs remain inert text, never remote content.
public enum NebulaAiMarkdown {
    public enum Kind { case bold, italic, code, heading }
    public struct Mark { public let range: NSRange; public let kind: Kind }
    public struct Result { public let text: String; public let marks: [Mark] }
    public static func parse(_ source: String) -> Result {
        var output = ""
        var marks: [Mark] = []
        var fenced = false
        var codeStart = 0
        let bounded = String(source.prefix(60_000)) + (source.count > 60_000 ? "\n…" : "")
        for var line in bounded.components(separatedBy: "\n") {
            if line.trimmingCharacters(in: .whitespaces).hasPrefix("```") {
                if fenced { marks.append(Mark(range: NSRange(location: codeStart, length: output.utf16.count - codeStart), kind: .code)) }
                else { codeStart = output.utf16.count }
                fenced.toggle(); continue
            }
            if fenced { output += line + "\n"; continue }
            let hashes = line.prefix(while: { $0 == "#" }).count
            let heading = (1...6).contains(hashes) && line.dropFirst(hashes).hasPrefix(" ")
            if heading { line = String(line.dropFirst(hashes + 1)) }
            if line.hasPrefix("* ") || line.hasPrefix("- ") { line = "• " + line.dropFirst(2) }
            let lineStart = output.utf16.count
            var position = line.startIndex
            while position < line.endIndex {
                let remaining = line[position...]
                if remaining.first == "\\" {
                    let next = line.index(after: position)
                    if next < line.endIndex { output.append(line[next]); position = line.index(after: next); continue }
                }
                let delimiter: String? = remaining.hasPrefix("**") ? "**" : remaining.hasPrefix("__") ? "__"
                    : remaining.hasPrefix("`") ? "`" : remaining.hasPrefix("*") ? "*" : nil
                if let delimiter = delimiter {
                    let start = line.index(position, offsetBy: delimiter.count)
                    if let end = line.range(of: delimiter, range: start..<line.endIndex), end.lowerBound > start {
                        let offset = output.utf16.count
                        output += line[start..<end.lowerBound]
                        marks.append(Mark(range: NSRange(location: offset, length: output.utf16.count - offset),
                                          kind: delimiter == "`" ? .code : delimiter.count == 2 ? .bold : .italic))
                        position = end.upperBound; continue
                    }
                }
                output.append(line[position]); position = line.index(after: position)
            }
            if heading { marks.append(Mark(range: NSRange(location: lineStart, length: output.utf16.count - lineStart), kind: .heading)) }
            output += "\n"
        }
        if fenced { marks.append(Mark(range: NSRange(location: codeStart, length: output.utf16.count - codeStart), kind: .code)) }
        return Result(text: output, marks: marks)
    }
}
