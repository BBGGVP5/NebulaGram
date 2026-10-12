import Foundation
import AVFoundation
import AudioToolbox
import NebulaSettingsContract

@available(iOS 15.0, *)
enum NebulaDraftVoiceAudio {
    static func prepare(source: URL, directory: URL, duration: Double, start: Double, end: Double, cloud: Bool) async throws -> URL {
        let pcm = directory.appendingPathComponent("source.caf")
        try await NebulaRecordingPCM.decode(source: source, destination: pcm, expectedDuration: duration)
        let destination = directory.appendingPathComponent(cloud ? "selected.m4a" : "selected.caf")
        let worker = Task.detached(priority: .userInitiated) {
            let input = try AVAudioFile(forReading: pcm, commonFormat: .pcmFormatInt16, interleaved: true)
            let rate = input.processingFormat.sampleRate
            let range = try NebulaDraftVoicePolicy.frames(total: input.length, sampleRate: rate, start: start, end: end)
            input.framePosition = range.lowerBound
            let settings: [String: Any] = cloud
                ? [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: rate, AVNumberOfChannelsKey: 1, AVEncoderBitRateKey: 64000]
                : input.processingFormat.settings
            let output = try AVAudioFile(forWriting: destination, settings: settings,
                commonFormat: cloud ? .pcmFormatFloat32 : .pcmFormatInt16, interleaved: !cloud)
            let deadline = ProcessInfo.processInfo.systemUptime + 120
            while input.framePosition < range.upperBound {
                try Task.checkCancellation()
                guard ProcessInfo.processInfo.systemUptime < deadline,
                      let buffer = AVAudioPCMBuffer(pcmFormat: input.processingFormat, frameCapacity: 4096) else {
                    throw NebulaLocalAudioPolicy.Failure.invalidRecording
                }
                try input.read(into: buffer, frameCount: AVAudioFrameCount(min(4096, range.upperBound - input.framePosition)))
                guard buffer.frameLength > 0 else { throw NebulaLocalAudioPolicy.Failure.incomplete }
                if cloud {
                    guard let converted = AVAudioPCMBuffer(pcmFormat: output.processingFormat, frameCapacity: buffer.frameLength),
                          let sourceSamples = buffer.int16ChannelData?[0], let targetSamples = converted.floatChannelData?[0] else {
                        throw NebulaLocalAudioPolicy.Failure.invalidRecording
                    }
                    converted.frameLength = buffer.frameLength
                    for index in 0..<Int(buffer.frameLength) { targetSamples[index] = Float(sourceSamples[index]) / 32768.0 }
                    try output.write(from: converted)
                } else { try output.write(from: buffer) }
            }
            try Task.checkCancellation()
        }
        try await withTaskCancellationHandler(operation: { try await worker.value }, onCancel: { worker.cancel() })
        return destination
    }
}
