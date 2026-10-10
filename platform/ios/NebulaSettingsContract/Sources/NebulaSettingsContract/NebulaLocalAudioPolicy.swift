import Foundation

public enum NebulaLocalAudioPolicy {
    public static let service = "apple-device"
    public static let sampleRate = 48_000
    public static let maximumSeconds = 600
    public static let chunkFrames = 55 * sampleRate
    public enum Failure: Error { case invalidRecording, incomplete, noSpeech }

    public static func ranges(frameCount: Int) throws -> [Range<Int>] {
        guard frameCount > 0, frameCount <= maximumSeconds * sampleRate else { throw Failure.invalidRecording }
        return stride(from: 0, to: frameCount, by: chunkFrames).map { $0..<min(frameCount, $0 + chunkFrames) }
    }

    public static func joinedFinals(_ chunks: [String], expectedCount: Int) throws -> String {
        guard expectedCount > 0, chunks.count == expectedCount else { throw Failure.incomplete }
        let value = chunks.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }.joined(separator: "\n")
        guard value.utf16.count <= 100_000 else { throw Failure.incomplete }
        guard !value.isEmpty else { throw Failure.noSpeech }
        return value
    }

    public static func locale(_ value: String) -> String? {
        guard value.utf8.count <= 64 else { return nil }
        let parts = value.replacingOccurrences(of: "_", with: "-").split(separator: "-", omittingEmptySubsequences: false)
        guard (1...3).contains(parts.count), (2...3).contains(parts[0].count),
              parts[0].utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) }),
              parts.allSatisfy({ !$0.isEmpty && $0.utf8.allSatisfy { (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) } }) else { return nil }
        return parts.enumerated().map { $0.offset == 0 ? $0.element.lowercased() : $0.element.count == 2 ? $0.element.uppercased() : $0.element.count == 4 ? $0.element.prefix(1).uppercased() + $0.element.dropFirst().lowercased() : String($0.element) }.joined(separator: "-")
    }
}
