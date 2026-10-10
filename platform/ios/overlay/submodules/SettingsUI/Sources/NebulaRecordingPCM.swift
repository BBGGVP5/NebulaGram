import Foundation
import AVFoundation
import CoreMedia
import UniversalMediaPlayer
import NebulaSettingsContract

@available(iOS 15.0, *)
enum NebulaRecordingPCM {
    // Existing Telegram software decoding supports both OGG voice notes and MP4
    // round videos. It emits mono 48 kHz PCM16; AVFoundation alone cannot read OGG.
    static func decode(source: URL, destination: URL, expectedDuration: Double?) async throws {
        let worker = Task.detached(priority: .userInitiated) {
            let bytes = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard bytes > 0, bytes <= NebulaAudioTranscription.maximumBytes else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
            if let duration = expectedDuration, (!duration.isFinite || duration <= 0 || duration > Double(NebulaLocalAudioPolicy.maximumSeconds)) { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
            let deadline = ProcessInfo.processInfo.systemUptime + 120
            let decoder = SoftwareAudioSource(path: source.path)
            guard decoder.hasStream, let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: Double(NebulaLocalAudioPolicy.sampleRate), channels: 1, interleaved: true) else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
            let output = try AVAudioFile(forWriting: destination, settings: format.settings, commonFormat: .pcmFormatInt16, interleaved: true)
            var frames = 0
            while let sample = decoder.readSampleBuffer() {
                try Task.checkCancellation()
                guard ProcessInfo.processInfo.systemUptime < deadline,
                      let description = CMSampleBufferGetFormatDescription(sample),
                      let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(description),
                      asbd.pointee.mSampleRate == Double(NebulaLocalAudioPolicy.sampleRate), asbd.pointee.mChannelsPerFrame == 1,
                      asbd.pointee.mFormatID == kAudioFormatLinearPCM, asbd.pointee.mBitsPerChannel == 16,
                      asbd.pointee.mFormatFlags & kAudioFormatFlagIsSignedInteger != 0,
                      let block = CMSampleBufferGetDataBuffer(sample) else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
                let count = CMSampleBufferGetNumSamples(sample)
                guard count > 0, count <= 1_000_000, frames <= NebulaLocalAudioPolicy.maximumSeconds * NebulaLocalAudioPolicy.sampleRate - count,
                      CMBlockBufferGetDataLength(block) == count * 2,
                      let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(count)), let samples = buffer.int16ChannelData else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
                buffer.frameLength = AVAudioFrameCount(count)
                guard CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: count * 2, destination: samples[0]) == kCMBlockBufferNoErr else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
                try output.write(from: buffer); frames += count
            }
            try Task.checkCancellation()
            guard frames > 0 else { throw NebulaLocalAudioPolicy.Failure.invalidRecording }
            // A decoder that stops early must not publish a partial recording as
            // complete. Telegram's integer-second voice metadata may be rounded.
            if let duration = expectedDuration, Double(frames) / Double(NebulaLocalAudioPolicy.sampleRate) + 2 < duration { throw NebulaLocalAudioPolicy.Failure.incomplete }
        }
        try await withTaskCancellationHandler(operation: { try await worker.value }, onCancel: { worker.cancel() })
    }
}
