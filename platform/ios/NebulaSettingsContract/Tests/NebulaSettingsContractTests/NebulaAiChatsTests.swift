import Foundation
import XCTest
@testable import NebulaSettingsContract

final class NebulaAiChatsTests: XCTestCase {
    private func withDefaults(_ body: (UserDefaults) -> Void) {
        let name = "NebulaAiChatsTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defer { defaults.removePersistentDomain(forName: name) }
        body(defaults)
    }

    func testSwitchingAndHistoryOptIn() {
        withDefaults { defaults in
            let chats = NebulaAiChats(defaults: defaults)
            let first = chats.current()
            chats.append(id: first.id, identity: "model-a", input: "First question", output: "First answer")
            let second = chats.fresh()
            chats.append(id: second.id, identity: "model-b", input: "Second question", output: "Second answer")
            XCTAssertEqual(chats.list().count, 2)
            XCTAssertEqual(chats.select(first.id)?.turns.first?.output, "First answer")
            XCTAssertEqual(chats.current().id, first.id)
            XCTAssertEqual(NebulaAiChats(defaults: defaults).list().count, 1)

            NebulaAiSettings(defaults: defaults).historyEnabled = true
            chats.append(id: first.id, identity: "model-a", input: "Follow-up", output: "Reply")
            let restored = NebulaAiChats(defaults: defaults)
            XCTAssertEqual(restored.list().count, 2)
            XCTAssertEqual(restored.current().id, first.id)
            XCTAssertEqual(restored.current().turns.count, 2)
            restored.clear()
            XCTAssertTrue(NebulaAiChats(defaults: defaults).current().turns.isEmpty)
        }
    }
}
