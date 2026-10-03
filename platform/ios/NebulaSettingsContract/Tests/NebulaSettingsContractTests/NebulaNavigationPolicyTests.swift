import XCTest
@testable import NebulaSettingsContract

final class NebulaNavigationPolicyTests: XCTestCase {
    func testLastFolderDeletionRestoresAllChats() {
        XCTAssertFalse(NebulaNavigationPolicy.hideAllChats(requested: true, customFolderCount: 0))
        XCTAssertTrue(NebulaNavigationPolicy.hideAllChats(requested: true, customFolderCount: 1))
        XCTAssertFalse(NebulaNavigationPolicy.hideAllChats(requested: false, customFolderCount: 4))
        XCTAssertEqual(NebulaNavigationPolicy.selectedFolder(current: 12, available: [7, 19], allChats: 0), 7)
        XCTAssertEqual(NebulaNavigationPolicy.selectedFolder(current: 19, available: [7, 19], allChats: 0), 19)
        XCTAssertEqual(NebulaNavigationPolicy.selectedFolder(current: 19, available: [], allChats: 0), 0)
    }

    func testAvatarRadiusAndForumOverrideAreBounded() {
        XCTAssertEqual(NebulaNavigationPolicy.avatarCornerFraction(roundness: -10, uniform: true, isForum: false), 0)
        XCTAssertEqual(NebulaNavigationPolicy.avatarCornerFraction(roundness: 150, uniform: true, isForum: false), 0.5)
        XCTAssertEqual(NebulaNavigationPolicy.avatarCornerFraction(roundness: 60, uniform: true, isForum: true), 0.3)
        XCTAssertEqual(NebulaNavigationPolicy.avatarCornerFraction(roundness: 60, uniform: false, isForum: true), 0.25)
    }
}
