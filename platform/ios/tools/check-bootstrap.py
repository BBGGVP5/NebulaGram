#!/usr/bin/env python3
"""Check the pinned iOS patch/overlay in a temporary tree, never reset the vendor.

--swift additionally parses native hooks (NOT a Telegram typecheck/build) and
compiles/runs the actual Bazel-side Foundation sources without SWIFT_PACKAGE.
"""
import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from check_onboarding import check as check_onboarding

ROOT = Path(__file__).resolve().parents[3]


def run(*args, **kwargs):
    return subprocess.check_output(args, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tree', type=Path, default=ROOT / 'vendor/telegram-ios')
    parser.add_argument('--swift', action='store_true')
    args = parser.parse_args()
    tree = args.tree.resolve()
    subprocess.run([sys.executable, str(ROOT / 'platform/ios/tools/generate-overlay.py'), '--check'], check=True)
    subprocess.run([sys.executable, str(ROOT / 'platform/ios/tools/generate-badge-artwork.py'), '--check'], check=True)
    entry = run('git', '-C', str(ROOT), 'ls-files', '--stage', '--', 'vendor/telegram-ios', text=True).split()
    if not entry or entry[0] != '160000':
        raise SystemExit('Missing pinned iOS gitlink')
    revision = entry[1]
    actual = run('git', '-C', str(tree), 'rev-parse', 'HEAD', text=True).strip()
    if actual != revision:
        raise SystemExit(f'Upstream revision mismatch: expected {revision}, found {actual}')
    subprocess.run([sys.executable, str(ROOT / 'scripts/generate-settings-icons.py'), '--check'], check=True)
    patches = sorted((ROOT / 'patches/ios').glob('*.patch'))
    paths = set()
    for patch in patches:
        pairs = re.findall(r'^diff --git a/(\S+) b/(\S+)$', patch.read_text(encoding='utf-8'), re.M)
        if not pairs:
            raise SystemExit(f'Empty patch: {patch.name}')
        for a, b in pairs:
            if a != b or not (a.startswith('submodules/') or a in {'Telegram/BUILD', 'Telegram/WidgetKitWidget/TodayViewController.swift'}) or '..' in Path(a).parts or '\\' in a:
                raise SystemExit('Unexpected patch path: ' + a)
            paths.add(a)
    with tempfile.TemporaryDirectory(prefix='nebula-ios-bootstrap-') as temporary:
        temp = Path(temporary)
        subprocess.run(['git', 'init', '--quiet', str(temp)], check=True)
        for path in paths:
            dest = temp / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(run('git', '-C', str(tree), 'show', revision + ':' + path))
        for patch in patches:
            subprocess.run(['git', '-C', str(temp), 'apply', '--whitespace=error', str(patch)], check=True)
        overlay = ROOT / 'platform/ios/overlay'
        for source in overlay.rglob('*'):
            if source.is_file():
                dest = temp / source.relative_to(overlay)
                if dest.exists():
                    raise SystemExit(f'Overlay unexpectedly replaces upstream file: {dest}')
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(source.read_bytes())
        folder = temp / 'submodules/TelegramUI/Components/ChatList/ChatListFilterTabContainerNode'
        tabs = (folder / 'Sources/ChatListFilterTabContainerNode.swift').read_text(encoding='utf-8')
        assert 'self.badgeContainerNode.isHidden = NebulaSettingsStore.shared.hideTabCounters' in tabs
        assert '|| self.badgeContainerNode.isHidden {' in tabs
        assert 'strings.VoiceOver_Chat_UnreadMessages(Int32(unreadCount))' in tabs
        assert 'self.unreadCount = unreadCount' in tabs
        assert 'self.nebulaSettingsObservation = NebulaSettingsStore.shared.observe { [weak self]' in tabs
        for build in [folder / 'BUILD', temp / 'submodules/SettingsUI/BUILD']:
            assert '"//submodules/NebulaSettingsContract:NebulaSettingsContract"' in build.read_text(encoding='utf-8')
        peer = temp / 'submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/Sources'
        header = (peer / 'PeerInfoHeaderNode.swift').read_text(encoding='utf-8')
        assert '"//submodules/NebulaSettingsContract:NebulaSettingsContract"' in (peer.parent / 'BUILD').read_text(encoding='utf-8')
        assert 'case .user = peer, threadData == nil' in header
        assert 'self.nebulaBadgeUserId == userId else { return }' in header
        assert 'nebulaTitleConstrainedSize.width - 28.0' in header
        assert 'TitleNodeStateRegular)?.view.addSubview(self.nebulaBadgeView)' in header
        assert 'TitleNodeStateExpanded)?.view.addSubview(self.nebulaExpandedBadgeView)' in header
        # All native icon placement code must survive the extra trailing badge.
        original_header = run('git', '-C', str(tree), 'show', revision + ':submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/Sources/PeerInfoHeaderNode.swift').decode('utf-8')
        native_start = original_header.index('        if let statusIconSize = self.statusIconSize,')
        native_end = original_header.index('        var titleFrame: CGRect', native_start)
        assert original_header[native_start:native_end] in header
        artwork = (peer / 'NebulaProfileBadgeArtwork.swift').read_text(encoding='utf-8')
        assert '.alwaysOriginal' in artwork and 'NebulaProfileBadgeImages.star' in artwork
        print('OK: native profile badge states, user scoping, existing status icons, exact branded artwork', flush=True)
        assert 'case nebulaGram' in (peer / 'PeerInfoScreen.swift').read_text(encoding='utf-8')
        assert 'interaction.openSettings(.nebulaGram)' in (peer / 'PeerInfoSettingsItems.swift').read_text(encoding='utf-8')
        assert 'push(nebulaSettingsController(context: self.context))' in (peer / 'PeerInfoScreenSettingsActions.swift').read_text(encoding='utf-8')
        controller = (temp / 'submodules/SettingsUI/Sources/NebulaSettingsController.swift').read_text(encoding='utf-8')
        assert 'makeDefaultPresentationTheme(' in controller
        assert 'UIScreen.main.traitCollection.userInterfaceStyle' in controller
        assert 'presentationData.withUpdated(theme: settingsTheme)' in controller
        assert 'presentationData.theme.withModalBlocksBackground()' not in controller
        assert '.widePosts(ru ?' in controller and 'store.widePosts, !store.hasLoadError)' in controller
        bubble_path = temp / 'submodules/TelegramUI/Components/Chat/ChatMessageBubbleItemNode'
        bubble = (bubble_path / 'Sources/ChatMessageBubbleItemNode.swift').read_text(encoding='utf-8')
        assert 'allowFullWidth || (NebulaSettingsStore.shared.widePosts && !isAd)' in bubble
        assert 'nebulaWidePostsObservation = NebulaSettingsStore.shared.observe' in bubble
        assert 'requestMessageUpdate(item.message.id, false, nil)' in bubble
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (bubble_path / 'BUILD').read_text(encoding='utf-8')
        assert 'hideCounters, !store.hasLoadError)' in controller
        assert 'value: value, enabled: enabled' in controller
        assert 'ItemListSingleLineInputItem(' in controller and 'NebulaSettingsSearch.matches(query, in: title)' in controller
        assert 'if case .footer = entry, failed || store.hasLoadError' in controller
        assert 'case .search: return 19' in controller and 'case .empty: return 20' in controller
        assert 'ItemListDisclosureItem(' in controller
        check_onboarding(temp, tree, revision)
        widget = (temp / 'Telegram/WidgetKitWidget/NebulaQuickActionsWidget.swift').read_text(encoding='utf-8')
        assert '.policy' not in widget or 'Timeline' in widget
        assert 'policy: .never' in widget and 'UserDefaults' not in widget and 'Postbox' not in widget
        assert 'NebulaQuickActionsWidget()' in (temp / 'Telegram/WidgetKitWidget/TodayViewController.swift').read_text(encoding='utf-8')
        routes = (temp / 'submodules/SettingsUI/Sources/NebulaQuickActions.swift').read_text(encoding='utf-8')
        assert 'route == "nebula/settings"' in routes and 'route == "nebula/link"' in routes
        assert 'tunnel.start' not in routes and 'NebulaDeletedArchive' not in routes
        handler = (temp / 'submodules/SettingsUI/Sources/Search/SettingsSearchableItems.swift').read_text(encoding='utf-8')
        assert 'if nebulaOpenQuickAction(context: context, path: path, navigationController: navigationController)' in handler
        app_build = (temp / 'Telegram/BUILD').read_text(encoding='utf-8')
        assert 'NebulaAppShortcuts.swift' in app_build
        # //Telegram:Lib and :WidgetExtensionLib are private to their own package
        # upstream; without the grant the integration check fails Bazel analysis.
        assert app_build.count('visibility = ["//submodules/NebulaIntegrationChecks:__pkg__"],') == 2
        integration = (temp / 'submodules/NebulaIntegrationChecks/BUILD').read_text(encoding='utf-8')
        for target in ['//Telegram:Lib', '//Telegram:WidgetExtensionLib', '//submodules/TelegramUI:TelegramUI']:
            assert target in integration

        # Tab visibility/order must not replace Telegram's original bar, lens, search,
        # gestures, badges, layout or drawing. The only change is an opt-out marker.
        native_tab_path = 'submodules/TelegramUI/Components/TabBarComponent/Sources/TabBarComponent.swift'
        native_tab = (temp / native_tab_path).read_text(encoding='utf-8')
        marker = '            self.backgroundContainer.nebulaPreservesNativeAppearance = true\n'
        assert native_tab.count(marker) == 1
        original_tab = run('git', '-C', str(tree), 'show', revision + ':' + native_tab_path).decode('utf-8')
        assert native_tab.replace(marker, '') == original_tab
        glass = (temp / 'submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/GlassBackgroundComponent.swift').read_text(encoding='utf-8')
        assert 'let reduced = !self.nebulaInNativeContainer && NebulaGlassPolicy.reduced(' in glass
        assert 'while let view = ancestor' in glass and 'ancestor = view.superview' in glass
        assert 'public var nebulaPreservesNativeAppearance: Bool = false' in glass
        assert 'nebulaFallback.clipsToBounds = true' in glass
        root_controller = (temp / 'submodules/TelegramUI/Sources/TelegramRootController.swift').read_text(encoding='utf-8')
        assert 'controllers = nebulaOrderedControllers(controllers)' in root_controller
        assert 'pair.0 !== pair.1' in root_controller and '$0 === old' in root_controller
        assert 'if store.showContactsTab' in root_controller
        print('OK: native Telegram tab bar is unchanged except its scoped adaptive-glass opt-out; order/hiding retain native controllers', flush=True)

        input_panel = (temp / 'submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/Sources/ChatTextInputPanelNode.swift').read_text(encoding='utf-8')
        assert 'let isExpandInputEnabled = self.enableRichTextInput\n' in input_panel
        assert 'let isTallPanel = actualTextFieldFrame.height >= 70.0' in input_panel
        assert 'self.interfaceInteraction?.openExpandedInput()' in input_panel
        chat = (temp / 'submodules/TelegramUI/Sources/ChatController.swift').read_text(encoding='utf-8')
        assert chat.count('guard let chosenReaction = chosenReaction else {\n                        itemNode.openMessageContextMenu()') == 2
        assert 'if !canSendReactionsToChat(strongSelf.presentationInterfaceState)' in chat
        print('OK: native rich editor independent of AI; missing quick reaction opens native menu, permission checks retained', flush=True)

        print(f'OK: {len(patches)} ordered iOS patch(es), {len(paths)} upstream paths, overlay/hooks, pin {revision}', flush=True)
        if args.swift:
            for source in sorted(temp.rglob('*.swift')):
                subprocess.run(['swiftc', '-frontend', '-parse', str(source)], check=True)
            contract = sorted((temp / 'submodules/NebulaSettingsContract/Sources').glob('*.swift'))
            main_file = temp / 'main.swift'
            main_file.write_text('''import Foundation
let catalog = try SettingsCatalog.bundled()
precondition(catalog.settings.count == __CATALOG_COUNT__)
let suite = "NebulaBazelSmoke.\\(UUID().uuidString)"
let defaults = UserDefaults(suiteName: suite)!
defer { defaults.removePersistentDomain(forName: suite) }
let store = NebulaSettingsStore(defaults: defaults)
precondition(!store.hasLoadError && !store.hideTabCounters)
try store.set(.boolean(true), for: "hide_tab_counters")
precondition(NebulaSettingsStore(defaults: defaults).hideTabCounters)
precondition(NebulaSettingsSearch.matches("  СЧЕТЧИКИ  папок ", in: "Счётчики папок"))
precondition(NebulaSettingsSearch.matches("MODEL provider", in: "Provider, model and instructions"))
precondition(NebulaSettingsSearch.matches("", in: "Anything"))
precondition(!NebulaSettingsSearch.matches("glass contacts", in: "Contacts in bottom bar"))
precondition(!NebulaSettingsSearch.matches("Несуществующий параметр", in: "Папки чатов"))
print("OK: embedded catalog and Bazel-side Foundation store compiled and ran")
'''.replace('__CATALOG_COUNT__', str(len(json.loads((ROOT / 'shared/settings/catalog.json').read_text(encoding='utf-8'))['settings']))), encoding='utf-8')
            executable = temp / 'smoke'
            search = temp / 'submodules/SettingsUI/Sources/NebulaSettingsSearch.swift'
            subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', *map(str, contract), str(search), str(main_file), '-o', str(executable)], check=True)
            subprocess.run([str(executable)], check=True)
            # Real SDK typecheck for the UIKit-only transfer adapter. This is not
            # a mock of Telegram, nor a full SettingsUI/Telegram application build.
            sdk = run('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path', text=True).strip()
            ios_flags = ['-swift-version', '5', '-warnings-as-errors', '-sdk', sdk,
                         '-target', 'arm64-apple-ios13.0-simulator']
            subprocess.run(['swiftc', *ios_flags, '-emit-module', '-parse-as-library',
                            '-module-name', 'NebulaSettingsContract', *map(str, contract),
                            '-emit-module-path', str(temp / 'NebulaSettingsContract.swiftmodule')], check=True)
            transfer = temp / 'submodules/SettingsUI/Sources/NebulaSettingsFileTransfer.swift'
            subprocess.run(['swiftc', *ios_flags, '-typecheck', '-I', str(temp), str(transfer)], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck', '-I', str(temp),
                            str(peer / 'NebulaProfileBadgeArtwork.swift'),
                            str(peer / 'NebulaProfileBadgeImages.swift')], check=True)
            print('OK: profile badge artwork typechecked against the real iOS simulator SDK')
            settings_ui = temp / 'submodules/SettingsUI/Sources'
            subprocess.run(['swiftc', *ios_flags, '-typecheck', str(settings_ui / 'NebulaSettingsStyle.swift'), str(settings_ui / 'NebulaSettingsSymbols.swift'),
                            str(settings_ui / 'NebulaSettingsHero.swift')], check=True)
            auth = temp / 'submodules/AuthorizationUI/Sources'
            subprocess.run(['swiftc', *ios_flags, '-typecheck', str(auth / 'NebulaAuthPresentation.swift'),
                            str(auth / 'NebulaWelcomeController.swift')], check=True)
            print('OK: UIKit file-transfer adapter typechecked against the real iOS simulator SDK')
            print('Native hooks parsed only. Full Telegram/Bazel build and iPhone acceptance remain required.')


if __name__ == '__main__':
    main()
