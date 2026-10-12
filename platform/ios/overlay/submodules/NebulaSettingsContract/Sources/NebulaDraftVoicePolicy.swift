import Foundation

public enum NebulaDraftVoicePolicy {
    public static func frames(total: Int64, sampleRate: Double, start: Double, end: Double) throws -> Range<Int64> {
        guard total > 0, sampleRate.isFinite, (8000...192000).contains(sampleRate),
              total <= Int64(sampleRate * Double(NebulaLocalAudioPolicy.maximumSeconds)), start.isFinite, end.isFinite else {
            throw NebulaLocalAudioPolicy.Failure.invalidRecording
        }
        let duration = Double(total) / sampleRate
        let lower = max(0, min(duration, start))
        let upper = max(0, min(duration, end))
        guard upper > lower else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
        let first = Int64((lower * sampleRate).rounded(.down))
        let last = upper >= duration ? total : Int64((upper * sampleRate).rounded(.down))
        guard last > first else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
        return first..<last
    }
}
