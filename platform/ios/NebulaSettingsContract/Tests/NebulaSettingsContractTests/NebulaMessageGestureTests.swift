import XCTest
@testable import NebulaSettingsContract

final class NebulaMessageGestureTests: XCTestCase {
    private func action(_ preference: Int, owned: Bool = true, channel: Bool = false,
                        edit: Bool = true, reply: Bool = true, copy: Bool = true, text: Bool = true) -> NebulaMessageGesturePolicy.Action {
        NebulaMessageGesturePolicy.doubleTap(preference: preference, owned: owned, channelPost: channel,
            canEdit: edit, canReply: reply, canCopy: copy, hasText: text)
    }

    func testIncomingAndChannelPostsKeepNativeBehavior() {
        for value in 0...4 {
            XCTAssertEqual(action(value, owned: false), .reaction)
            XCTAssertEqual(action(value, channel: true), .reaction)
        }
    }

    func testNativePermissionFallbacks() {
        XCTAssertEqual(action(1), .edit)
        XCTAssertEqual(action(1, edit: false), .reaction)
        XCTAssertEqual(action(2), .reply)
        XCTAssertEqual(action(2, reply: false), .reaction)
        XCTAssertEqual(action(3), .copy)
        XCTAssertEqual(action(3, copy: false), .reaction)
        XCTAssertEqual(action(3, text: false), .reaction)
        XCTAssertEqual(action(4, edit: false, reply: false, copy: false, text: false), .none)
        XCTAssertEqual(action(99), .reaction)
    }
}
