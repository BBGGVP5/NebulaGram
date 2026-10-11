import XCTest
@testable import NebulaSettingsContract

final class NebulaNavigationPolicyTests: XCTestCase {
    func testProfileChoiceIndependentOfBarStyleAndSettingsRecovery() {
        for customized in [false, true] {
            for contacts in [false, true] {
                for profile in [false, true] {
                    for settings in [false, true] {
                        let tabs = NebulaNavigationPolicy.visibleRootTabs(customized: customized, showContacts: contacts, showProfile: profile, showSettings: settings)
                        XCTAssertEqual(tabs.contains("profile"), profile)
                        XCTAssertEqual(tabs.contains("contacts"), contacts || !customized)
                        XCTAssertEqual(tabs.contains("settings"), settings || !profile || !customized)
                        XCTAssertTrue(tabs.contains("chats"))
                    }
                }
            }
        }
    }

    func testProfileVisibilityChangesLiveAndMatchesReloadedStore() throws {
        let suite = "NebulaNavigationPolicyTests." + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = NebulaSettingsStore(defaults: defaults)
        try store.set(.boolean(false), for: "bottom_bar")
        try store.set(.boolean(true), for: "bottom_bar_profile")
        try store.set(.boolean(false), for: "bottom_bar_settings")
        var seen: [Bool] = []
        let observation = store.observe(queue: nil) {
            let tabs = NebulaNavigationPolicy.visibleRootTabs(customized: store.showBottomBar, showContacts: store.showContactsTab, showProfile: store.showProfileTab, showSettings: store.showSettingsTab)
            seen.append(tabs.contains("profile"))
            if !store.showProfileTab { XCTAssertTrue(tabs.contains("settings")) }
        }
        for visible in [false, true, false] { try store.set(.boolean(visible), for: "bottom_bar_profile") }
        XCTAssertEqual(seen, [false, true, false])
        observation.cancel()
        let reloaded = NebulaSettingsStore(defaults: defaults)
        XCTAssertEqual(reloaded.showProfileTab, store.showProfileTab)
        XCTAssertFalse(NebulaNavigationPolicy.visibleRootTabs(customized: reloaded.showBottomBar, showContacts: reloaded.showContactsTab, showProfile: reloaded.showProfileTab, showSettings: reloaded.showSettingsTab).contains("profile"))
    }

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
