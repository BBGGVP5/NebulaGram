import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaLocalAudioPolicyTests: XCTestCase {
    func testChunksCoverTheCompleteRecordingAtBoundaries() throws {
        for count in [1, 2_639_999, 2_640_000, 2_640_001, 28_800_000] {
            let chunks = try NebulaLocalAudioPolicy.ranges(frameCount: count)
            XCTAssertEqual(chunks.first?.lowerBound, 0); XCTAssertEqual(chunks.last?.upperBound, count)
            XCTAssertEqual(chunks.map(\.count).reduce(0, +), count)
            for i in chunks.indices {
                XCTAssertGreaterThan(chunks[i].count, 0); XCTAssertLessThanOrEqual(chunks[i].count, 2_640_000)
                if i > 0 { XCTAssertEqual(chunks[i - 1].upperBound, chunks[i].lowerBound) }
            }
        }
        XCTAssertEqual(try NebulaLocalAudioPolicy.ranges(frameCount: 2_640_001).map(\.count), [2_640_000, 1])
        for count in [-1, 0, 28_800_001, Int.max] { XCTAssertThrowsError(try NebulaLocalAudioPolicy.ranges(frameCount: count)) }
    }
    func testOnlyAllFinalChunksProduceAResult() throws {
        XCTAssertEqual(try NebulaLocalAudioPolicy.joinedFinals([" Первый ", "", " Second\n"], expectedCount: 3), "Первый\nSecond")
        XCTAssertThrowsError(try NebulaLocalAudioPolicy.joinedFinals(["partial"], expectedCount: 2))
        XCTAssertThrowsError(try NebulaLocalAudioPolicy.joinedFinals([], expectedCount: 0))
        XCTAssertThrowsError(try NebulaLocalAudioPolicy.joinedFinals([" \n", ""], expectedCount: 2))
        XCTAssertThrowsError(try NebulaLocalAudioPolicy.joinedFinals([String(repeating: "🚀", count: 50_001)], expectedCount: 1))
    }
    func testLocalChoicePersistsWithoutChangingTextOrSpeechServices() throws {
        let suite = "local-audio-\(UUID().uuidString)", defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let audio = NebulaAudioPreferences(defaults: defaults), services = NebulaAiServices(defaults: defaults)
        audio.migrate(services: services); audio.speechService = "speech-profile"
        audio.transcriptionService = NebulaLocalAudioPolicy.service; audio.transcriptionLocale = "ru_RU"; audio.transcriptionEnabled = true
        XCTAssertTrue(audio.localTranscription); XCTAssertNil(audio.connection(speech: false, services: services))
        let reopened = NebulaAudioPreferences(defaults: defaults)
        XCTAssertEqual(reopened.transcriptionLocale, "ru-RU"); XCTAssertEqual(reopened.speechService, "speech-profile"); XCTAssertTrue(reopened.transcriptionEnabled)
        XCTAssertNil(services.active); audio.transcriptionLocale = "../../file"; XCTAssertEqual(audio.transcriptionLocale, "ru-RU")
        audio.transcriptionService = "deleted-cloud-profile"; XCTAssertFalse(audio.localTranscription); XCTAssertNil(audio.connection(speech: false, services: services))
    }
    func testLocaleBoundsAndCanonicalization() {
        XCTAssertEqual(NebulaLocalAudioPolicy.locale("en_US"), "en-US"); XCTAssertEqual(NebulaLocalAudioPolicy.locale("zh-Hans-CN"), "zh-Hans-CN")
        for value in ["", "en--US", "x-US", "12-US", "ru\nRU", "en-US/", String(repeating: "a", count: 65)] { XCTAssertNil(NebulaLocalAudioPolicy.locale(value)) }
    }
    func testOnlyActualTranscriptionSwitchChangesRefreshTheChat() {
        let suite = "audio-notification-\(UUID().uuidString)", defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let audio = NebulaAudioPreferences(defaults: defaults)
        let changes = expectation(description: "Enable and disable refresh native buttons")
        changes.expectedFulfillmentCount = 2; changes.assertForOverFulfill = true
        let token = NotificationCenter.default.addObserver(forName: NebulaAudioPreferences.transcriptionChanged, object: nil, queue: nil) { notification in
            if notification.object as? NebulaAudioPreferences === audio { changes.fulfill() }
        }
        defer { NotificationCenter.default.removeObserver(token) }
        audio.transcriptionEnabled = true; audio.transcriptionEnabled = true
        audio.transcriptionService = NebulaLocalAudioPolicy.service; audio.speechService = "device"
        audio.transcriptionEnabled = false; audio.transcriptionEnabled = false
        wait(for: [changes], timeout: 1)
    }
}
