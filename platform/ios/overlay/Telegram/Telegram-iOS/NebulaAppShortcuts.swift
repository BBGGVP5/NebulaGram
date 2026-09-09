import UIKit
import AppIntents
import BuildConfig

@available(iOS 16.0, *)
struct NebulaOpenSettingsIntent: AppIntent {
    static var title: LocalizedStringResource = "Open NebulaGram Settings"
    static var openAppWhenRun: Bool = true
    @MainActor func perform() async throws -> some IntentResult {
        let scheme = BuildConfig(baseAppBundleId: Bundle.main.bundleIdentifier ?? "").appSpecificUrlScheme
        guard let url = URL(string: "\(scheme)://settings/nebula/settings"), await UIApplication.shared.open(url) else {
            throw NebulaShortcutError.cannotOpen
        }
        return .result()
    }
}
@available(iOS 16.0, *)
struct NebulaOpenLinkIntent: AppIntent {
    static var title: LocalizedStringResource = "Open NebulaLink"
    static var openAppWhenRun: Bool = true
    @MainActor func perform() async throws -> some IntentResult {
        let scheme = BuildConfig(baseAppBundleId: Bundle.main.bundleIdentifier ?? "").appSpecificUrlScheme
        guard let url = URL(string: "\(scheme)://settings/nebula/link"), await UIApplication.shared.open(url) else {
            throw NebulaShortcutError.cannotOpen
        }
        return .result()
    }
}
private enum NebulaShortcutError: Error { case cannotOpen }
@available(iOS 16.0, *)
struct NebulaAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: NebulaOpenSettingsIntent(), phrases: ["Open settings in \(.applicationName)", "Открой настройки в \(.applicationName)"], shortTitle: "Settings", systemImageName: "gearshape.fill")
        AppShortcut(intent: NebulaOpenLinkIntent(), phrases: ["Open NebulaLink in \(.applicationName)", "Открой NebulaLink в \(.applicationName)"], shortTitle: "NebulaLink", systemImageName: "shield.lefthalf.filled")
    }
}
