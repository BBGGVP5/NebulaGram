import XCTest
@testable import NebulaSettingsContract

final class NebulaDraftVoiceTests: XCTestCase {
    func testSelectedFramesAndLimits() throws {
        XCTAssertEqual(try NebulaDraftVoicePolicy.frames(total: 48000, sampleRate: 48000, start: 0.25, end: 0.75), 12000..<36000)
        XCTAssertEqual(try NebulaDraftVoicePolicy.frames(total: 48000, sampleRate: 48000, start: -100, end: 100), 0..<48000)
        for (total, rate, start, end) in [(Int64(0), 48000.0, 0.0, 1.0), (Int64.max, 48000, 0, 1), (48000, .nan, 0, 1),
                                         (48000, 48000, .infinity, 1), (48000, 48000, 0, .nan), (48000, 48000, 1, 0)] {
            XCTAssertThrowsError(try NebulaDraftVoicePolicy.frames(total: total, sampleRate: rate, start: start, end: end))
        }
    }
    func testDraftOptionIsIndependentOfReceivedTranscription() {
        let name = "DraftVoiceTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        let prefs = NebulaAudioPreferences(defaults: defaults)
        XCTAssertTrue(prefs.draftTranscriptionEnabled)
        prefs.transcriptionEnabled = false
        XCTAssertTrue(prefs.draftTranscriptionEnabled)
        prefs.draftTranscriptionEnabled = false
        prefs.transcriptionEnabled = true
        XCTAssertFalse(prefs.draftTranscriptionEnabled)
        XCTAssertTrue(prefs.transcriptionEnabled)
    }
}
