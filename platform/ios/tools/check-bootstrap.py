#!/usr/bin/env python3
"""Check the pinned iOS patch/overlay in a temporary tree, never reset the vendor.

--swift additionally parses native hooks (NOT a Telegram typecheck/build) and
compiles/runs the actual Bazel-side Foundation sources without SWIFT_PACKAGE.
"""
import argparse
import json
import re
import shutil
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
    subprocess.run([sys.executable, str(ROOT / 'platform/ios/tools/generate-app-icons.py'), '--check'], check=True)
    subprocess.run([sys.executable, str(ROOT / 'platform/ios/tools/generate-primary-icon.py'), '--check'], check=True)
    entry = run('git', '-C', str(ROOT), 'ls-files', '--stage', '--', 'vendor/telegram-ios', text=True).split()
    if not entry or entry[0] != '160000':
        raise SystemExit('Missing pinned iOS gitlink')
    revision = entry[1]
    actual = run('git', '-C', str(tree), 'rev-parse', 'HEAD', text=True).strip()
    if actual != revision:
        raise SystemExit(f'Upstream revision mismatch: expected {revision}, found {actual}')
    subprocess.run([sys.executable, str(ROOT / 'scripts/generate-settings-icons.py'), '--check'], check=True)
    subprocess.run([sys.executable, str(ROOT / 'scripts/generate-role-badges.py'), '--check'], check=True)
    subprocess.run([sys.executable, str(ROOT / 'scripts/generate-ios-icon-packs.py'), '--check'], check=True)
    patches = sorted((ROOT / 'patches/ios').glob('*.patch'))
    paths = set()
    for patch in patches:
        pairs = re.findall(r'^diff --git a/(\S+) b/(\S+)$', patch.read_text(encoding='utf-8'), re.M)
        if not pairs:
            raise SystemExit(f'Empty patch: {patch.name}')
        for a, b in pairs:
            if a != b or not (a.startswith('submodules/') or a in {'third-party/ZipArchive/PublicHeaders/ZipArchive/ZipArchive.h', 'third-party/ZipArchive/Sources/SSZipArchive.m', 'Telegram/NotificationService/Sources/NotificationService.swift', 'Telegram/NotificationService/BUILD', 'Telegram/BUILD', 'Telegram/WidgetKitWidget/TodayViewController.swift', 'Telegram/Telegram-iOS/AlternateIcons.plist', 'Telegram/Telegram-iOS/AlternateIcons-iPad.plist'}) or '..' in Path(a).parts or '\\' in a:
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
        assert 'nebulaTitleConstrainedSize.width - 19.0' in header
        assert 'let badgeSize: CGFloat = 16.0' in header
        assert 'let expandedBadgeSize: CGFloat = 14.0' in header
        assert 'TitleNodeStateRegular)?.view.addSubview(self.nebulaBadgeView)' in header
        assert 'TitleNodeStateExpanded)?.view.addSubview(self.nebulaExpandedBadgeView)' in header
        assert 'UITapGestureRecognizer(target: self, action: #selector(self.nebulaBadgeTapped))' in header
        assert header.index('super.init()', header.index('private let nebulaBadgeView')) < header.index('view.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(self.nebulaBadgeTapped)))')
        assert 'UIAccessibility.isReduceMotionEnabled' in header
        assert 'customUndoText: isRussian ? "Подробнее" : "Learn more"' in header
        assert 'ActionSheetTextItem(title: details)' in header
        assert 'NebulaBadgeArtworkActionSheetItem(badge: badge)' in header
        details_artwork = (peer / 'NebulaBadgeArtworkActionSheetItem.swift').read_text(encoding='utf-8')
        assert 'NebulaProfileBadgeArtwork.image(for: badge)' in details_artwork
        assert 'UIAccessibility.isReduceMotionEnabled' in details_artwork
        assert 'controller.present(sheet, in: .window(.root))' in header
        ai_menu = (temp / 'submodules/TelegramUI/Sources/ChatInterfaceStateContextMenus.swift').read_text(encoding='utf-8')
        assert 'NebulaAiSettings.shared' in ai_menu
        assert 'NebulaAiEditorController(source: richSource' in ai_menu
        assert 'NebulaAiChatController(russian: russian, initialText: selectedText, action: .summarize, theme: chatPresentationInterfaceState.theme)' in ai_menu
        assert '!isCopyProtected && messages.count == 1' in ai_menu
        draft_menu = (temp / 'submodules/TelegramUI/Sources/ChatController.swift').read_text(encoding='utf-8')
        assert 'NebulaAiEditorController(source: original' in draft_menu and 'nebulaApplyRichDraft(source: original, result: value)' in draft_menu
        assert 'withUpdatedEffectiveInputState(ChatTextInputState(inputText: result))' in draft_menu
        assert 'effectiveInputState.inputText.isEqual(to: source) else { return }' in draft_menu
        assert 'composerVisible && !nebulaToolsVisible' in draft_menu
        assert 'NebulaToolsNavigationController(root: controller, onDismiss:' in draft_menu
        assert 'self?.nebulaApplyRichDraft(source: original, result: value)' in draft_menu
        assert 'NebulaToolsNavigationController(root: NebulaMessageToolsController' in ai_menu
        assert 'NebulaChatLockEditorController(account:' in draft_menu
        assert 'NebulaChatLockGate.attach(to: self' in draft_menu
        assert 'NebulaChatLockGate.seal(host: self)' in draft_menu
        lock_store = (temp / 'submodules/NebulaSettingsContract/Sources/NebulaChatLocks.swift').read_text(encoding='utf-8')
        assert 'app.nebulagram.chatlocks' in lock_store and 'CCKeyDerivationPBKDF' in lock_store
        assert 'kSecAttrAccessibleWhenUnlockedThisDeviceOnly' in (temp / 'submodules/NebulaSettingsContract/Sources/NebulaAiSecrets.swift').read_text(encoding='utf-8')
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
        assert 'makeDefaultPresentationTheme(' not in controller
        assert 'presentationData.withUpdated(theme:' not in controller
        assert 'let data = ItemListPresentationData(presentationData)' in controller
        assert '.widePosts(ru ?' in controller and 'store.widePosts, !store.hasLoadError)' in controller
        bubble_path = temp / 'submodules/TelegramUI/Components/Chat/ChatMessageBubbleItemNode'
        bubble = (bubble_path / 'Sources/ChatMessageBubbleItemNode.swift').read_text(encoding='utf-8')
        assert 'allowFullWidth || (NebulaSettingsStore.shared.widePosts && isBroadcastPost && !isAd)' in bubble
        assert 'case .broadcast = channel.info' in bubble
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
        assert 'composer_icon_folders = ["NebulaGram"]' in app_build
        theme_defaults = (temp / 'submodules/TelegramUIPreferences/Sources/PresentationThemeSettings.swift').read_text(encoding='utf-8')
        assert 'day.index: PresentationThemeAccentColor(index: -1, baseColor: .blue, accentColor: 0x3C8DF0)' in theme_defaults
        assert 'night.index: PresentationThemeAccentColor(index: -1, baseColor: .blue, accentColor: 0xA8C7FA)' in theme_defaults
        chat_lock = (temp / 'submodules/SettingsUI/Sources/NebulaChatLockController.swift').read_text(encoding='utf-8')
        assert 'private let newCredential = UITextField()' in chat_lock and 'private let next = UITextField()' not in chat_lock
        round_video = (temp / 'submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/VideoMessageCameraScreen.swift').read_text(encoding='utf-8')
        assert 'self.nebulaZoomSlider.onZoomChanged' in round_video
        assert (temp / 'submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/NebulaVideoZoomSlider.swift').is_file()
        primary_icon = temp / 'Telegram/Telegram-iOS/NebulaGram.icon'
        assert (primary_icon / 'icon.json').is_file()
        assert (primary_icon / 'Assets/NebulaMark.svg').is_file()
        assert 'NebulaAppShortcuts.swift' in app_build
        for variant in ('Blue', 'Ocean', 'Aurora', 'Sunset', 'Graphite', 'Pearl', 'Ink', 'Paper',
                        'Mint', 'Lavender', 'Tangerine', 'Rose', 'Orbit', 'Blueprint', 'Nova', 'Monogram'):
            name = 'Nebula' + variant + 'Icon'
            assert '"' + name + '"' in app_build
            for suffix in ('', '-iPad'):
                icon_plist = (temp / ('Telegram/Telegram-iOS/AlternateIcons' + suffix + '.plist')).read_text(encoding='utf-8')
                assert '<key>' + name + '</key>' in icon_plist
            assert (temp / ('Telegram/Telegram-iOS/' + name + '.alticon/' + name + '@3x.png')).is_file()
        assert 'setAlternateIconName(name)' in (temp / 'submodules/SettingsUI/Sources/NebulaIconController.swift').read_text(encoding='utf-8')
        build_info = (temp / 'submodules/SettingsUI/Sources/NebulaBuildInfoController.swift').read_text(encoding='utf-8')
        assert revision in build_info and 'CFBundleShortVersionString' in build_info and 'CFBundleVersion' in build_info
        upstream_version = json.loads((tree / 'versions.json').read_text(encoding='utf-8'))['app']
        assert 'sourceVersion = "' + upstream_version + '"' in build_info
        assert 'arguments.openBuildInfo' in controller and 'arguments.openIcons' in controller
        assert 'arguments.openTransitions' in controller
        navigation_source = (temp / 'submodules/Display/Source/Navigation/NavigationController.swift').read_text(encoding='utf-8')
        assert 'NebulaSettingsStore.shared.transitionStyle == 1' in navigation_source
        assert 'NebulaSettingsStore.shared.transitionStyle == 2' in navigation_source
        assert 'UIAccessibility.isReduceMotionEnabled' in navigation_source
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (temp / 'submodules/Display/BUILD').read_text(encoding='utf-8')
        # //Telegram:Lib and :WidgetExtensionLib are private to their own package
        # upstream; without the grant the integration check fails Bazel analysis.
        assert app_build.count('visibility = ["//submodules/NebulaIntegrationChecks:__pkg__"],') == 2
        integration = (temp / 'submodules/NebulaIntegrationChecks/BUILD').read_text(encoding='utf-8')
        for target in ['//Telegram:Lib', '//Telegram:WidgetExtensionLib', '//submodules/TelegramUI:TelegramUI']:
            assert target in integration

        # Navigation preferences retain Telegram's native bar, lens, search,
        # gestures, badges and drawing; only width and label visibility change.
        native_tab_path = 'submodules/TelegramUI/Components/TabBarComponent/Sources/TabBarComponent.swift'
        native_tab = (temp / native_tab_path).read_text(encoding='utf-8')
        marker = '            self.backgroundContainer.nebulaPreservesNativeAppearance = true\n'
        assert native_tab.count(marker) == 1
        original_tab = run('git', '-C', str(tree), 'show', revision + ':' + native_tab_path).decode('utf-8')
        expected_tab = original_tab.replace('import Foundation\n', 'import Foundation\nimport NebulaSettingsContract\n', 1)
        expected_tab = expected_tab.replace('            self.addSubview(self.backgroundContainer)\n', '            self.addSubview(self.backgroundContainer)\n' + marker, 1)
        expected_tab = expected_tab.replace('            let availableSize = CGSize(width: min(500.0, availableSize.width), height: availableSize.height)\n',
            '            let preferredWidth = NebulaSettingsStore.shared.compactBottomBar ? max(160.0, CGFloat(component.items.count) * 72.0 + 8.0) : 500.0\n'
            '            let availableSize = CGSize(width: min(preferredWidth, availableSize.width), height: availableSize.height)\n', 1)
        expected_tab = expected_tab.replace('                alphaTransition.setAlpha(view: titleView, alpha: component.isCompact ? 0.0 : 1.0)\n',
            '                alphaTransition.setAlpha(view: titleView, alpha: component.isCompact || !NebulaSettingsStore.shared.showTabLabels ? 0.0 : 1.0)\n', 1)
        assert native_tab == expected_tab
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (temp / 'submodules/TelegramUI/Components/TabBarComponent/BUILD').read_text(encoding='utf-8')
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (temp / 'submodules/TabBarUI/BUILD').read_text(encoding='utf-8')
        glass = (temp / 'submodules/TelegramUI/Components/GlassBackgroundComponent/Sources/GlassBackgroundComponent.swift').read_text(encoding='utf-8')
        assert 'let reduced = !self.nebulaInNativeContainer && NebulaGlassPolicy.reduced(' in glass
        assert 'while let view = ancestor' in glass and 'ancestor = view.superview' in glass
        assert 'public var nebulaPreservesNativeAppearance: Bool = false' in glass
        assert 'nebulaFallback.clipsToBounds = true' in glass
        root_controller = (temp / 'submodules/TelegramUI/Sources/TelegramRootController.swift').read_text(encoding='utf-8')
        assert 'controllers = nebulaOrderedControllers(controllers)' in root_controller
        assert 'pair.0 !== pair.1' in root_controller and '$0 === old' in root_controller
        for key in ['showContactsTab', 'showProfileTab', 'showSettingsTab']:
            assert f'(store.{key} || !store.showBottomBar)' in root_controller
        assert 'nebulaNavigationButton' in (temp / 'submodules/TabBarUI/Sources/TabBarContollerNode.swift').read_text(encoding='utf-8')
        assert root_controller.index('self.nebulaProfileController = profileController') < root_controller.index('controllers = nebulaOrderedControllers(controllers)')
        assert 'self.pushViewController(settings, animated: true)' in root_controller
        print('OK: native Telegram tab bar retains its lens, search, gestures and badges with live labels, compact width and native profile tab', flush=True)

        input_panel = (temp / 'submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/Sources/ChatTextInputPanelNode.swift').read_text(encoding='utf-8')
        assert 'let isExpandInputEnabled = self.enableRichTextInput\n' in input_panel
        assert 'let isTallPanel = actualTextFieldFrame.height >= 70.0' in input_panel
        assert input_panel.count('if !NebulaSettingsStore.shared.hideSendAs, let sendAsPeers = interfaceState.sendAsPeers') == 3
        assert 'self?.requestLayout()' in input_panel
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (temp / 'submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/BUILD').read_text(encoding='utf-8')
        chat_title = (temp / 'submodules/TelegramUI/Components/ChatTitleView/Sources/ChatTitleView.swift').read_text(encoding='utf-8')
        assert chat_title.count('NebulaSettingsStore.shared.centeredChatHeader') >= 3
        assert 'self.requestUpdate?(.immediate)' in chat_title
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (temp / 'submodules/TelegramUI/Components/ChatTitleView/BUILD').read_text(encoding='utf-8')
        assert 'self.interfaceInteraction?.openExpandedInput()' in input_panel
        chat = (temp / 'submodules/TelegramUI/Sources/ChatController.swift').read_text(encoding='utf-8')
        assert '!NebulaSettingsStore.shared.disableNextChannel && contentData.state.offerNextChannelToRead' in chat
        assert 'self.updateNextChannelToReadVisibility()' in chat
        assert 'self.updateChatPresentationInterfaceState(transition: .immediate, interactive: false, force: true)' in chat
        message_times = (temp / 'submodules/TelegramUI/Components/Chat/ChatMessageDateAndStatusNode/Sources/StringForMessageTimestampStatus.swift').read_text(encoding='utf-8')
        assert message_times.count('withSeconds: showSeconds') == 5
        assert '//submodules/NebulaSettingsContract:NebulaSettingsContract' in (temp / 'submodules/TelegramUI/Components/Chat/ChatMessageDateAndStatusNode/BUILD').read_text(encoding='utf-8')
        message_menu = (temp / 'submodules/TelegramUI/Sources/ChatInterfaceStateContextMenus.swift').read_text(encoding='utf-8')
        assert message_menu.count('chatPresentationInterfaceState.strings.baseLanguageCode.lowercased().hasPrefix("ru")') >= 2
        assert 'let russian = presentationData.strings.baseLanguageCode' not in message_menu
        assert chat.count('guard let chosenReaction = chosenReaction else {\n                        itemNode.openMessageContextMenu()') == 2
        assert 'if !canSendReactionsToChat(strongSelf.presentationInterfaceState)' in chat
        print('OK: native rich editor independent of AI; missing quick reaction opens native menu, permission checks retained', flush=True)

        capture = (temp / 'submodules/TelegramCore/Sources/State/NebulaDeletedCapture.swift').read_text(encoding='utf-8')
        assert 'NebulaRetentionPolicy.receivedOrSaved(' in capture and 'scope = .saved' in capture
        assert 'id.peerId != accountPeerId' not in capture
        assert 'archive.scheduleReplace(entries, account: account)' in capture
        assert 'snapshot.keys.contains(key(id))' in capture and 'for id in newlyRetained' in capture
        assert 'archive.shouldPrune(account: account)' in capture
        choice = (temp / 'submodules/SettingsUI/Sources/NebulaChoiceController.swift').read_text(encoding='utf-8')
        assert 'cell.accessoryType = .none' in choice and 'cell.accessibilityTraits.insert(.selected)' in choice
        assert '.checkmark' not in choice and '.custom' in choice and 'withAlphaComponent(0.6)' in choice and '.automaticDimension' in choice
        assert 'NebulaGlassController(russian: ru,' in controller
        assert 'theme: context.sharedContext.currentPresentationData.with { $0 }.theme' in controller
        glass_preview = (temp / 'submodules/SettingsUI/Sources/NebulaGlassController.swift').read_text(encoding='utf-8')
        assert 'GlassBackgroundView(frame: .zero)' in glass_preview and 'glass.update(size:' in glass_preview
        assert '"ios_glass_tint"' in glass_preview and '"ios_glass_style"' in glass_preview
        assert 'if nebulaCustom { nativeView?.effect = nil }' in glass
        assert 'nativeParamsView?.isHidden = nebulaCustom' not in glass  # Must keep labels/buttons visible.
        assert 'nebulaMaterialState != state' in glass  # No effect allocation on every pan frame.
        zoom = (temp / 'submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/NebulaVideoZoomSlider.swift').read_text(encoding='utf-8')
        assert 'device.neutralZoomFactor' not in zoom
        assert 'AVCaptureDevice.default' not in zoom and 'setRange(minimum:' in zoom
        camera = (temp / 'submodules/Camera/Sources/Camera.swift').read_text(encoding='utf-8')
        assert 'maximumDeviceFactor: Double(device.maxAvailableVideoZoomFactor)' in camera
        assert 'private let nebulaZoomLock = NSLock()' in camera
        print('OK: Saved Messages retention, async archive hook, native choice sheets, live glass preview and public zoom API', flush=True)
        ai_chat = (temp / 'submodules/SettingsUI/Sources/NebulaAiChatController.swift').read_text(encoding='utf-8')
        ai_settings = (temp / 'submodules/SettingsUI/Sources/NebulaAiController.swift').read_text(encoding='utf-8')
        ai_home = (temp / 'submodules/ChatListUI/Sources/ChatListController.swift').read_text(encoding='utf-8')
        ai_root = (temp / 'submodules/TelegramUI/Sources/TelegramRootController.swift').read_text(encoding='utf-8')
        assert 'NebulaAiMarkdown.parse(raw)' in ai_chat and 'self.gate.accepts(id)' in ai_chat
        assert 'view.keyboardLayoutGuide.topAnchor' in ai_chat and 'work?.cancel()' in ai_chat
        assert 'homeShortcut' in ai_settings and 'Timer.scheduledTimer' in ai_settings
        assert 'nebulaStoryAvailability' in ai_home and 'NebulaAiSettings.openHomeChat' in ai_home
        assert 'NebulaAiChatController.presentSheet(from: self' in ai_root and 'host === self' in ai_root
        assert 'func move(_ gesture:' not in glass_preview
        print('OK: iOS chat lifecycle, Markdown, live local readiness and scoped home shortcut hooks', flush=True)

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
            app_bundle = temp / 'submodules/AppBundle'
            header_path = 'submodules/AppBundle/PublicHeaders/AppBundle/AppBundle.h'
            header = temp / header_path
            header.parent.mkdir(parents=True, exist_ok=True)
            header.write_bytes(run('git', '-C', str(tree), 'show', revision + ':' + header_path))
            subprocess.run(['xcrun', '--sdk', 'iphonesimulator', 'clang', '-fsyntax-only',
                            '-fobjc-arc', '-fmodules', '-Werror', '-target', 'arm64-apple-ios13.0-simulator',
                            '-isysroot', sdk, '-I', str(app_bundle / 'PublicHeaders'),
                            str(app_bundle / 'Sources/AppBundle/AppBundle.m')], check=True)
            icon_catalog = temp / 'IconPacks.xcassets'
            icon_catalog.mkdir()
            (icon_catalog / 'Contents.json').write_text('{"info":{"author":"xcode","version":1}}', encoding='utf-8')
            for asset in (temp / 'submodules/TelegramUI/Images.xcassets').glob('NebulaPack*.imageset'):
                shutil.copytree(asset, icon_catalog / asset.name)
            icon_output = temp / 'compiled-icons'
            icon_output.mkdir()
            subprocess.run(['xcrun', 'actool', '--compile', str(icon_output), '--platform', 'iphonesimulator',
                            '--minimum-deployment-target', '13.0', '--target-device', 'iphone',
                            '--output-format', 'human-readable-text', str(icon_catalog)], check=True)
            ios_flags = ['-swift-version', '5', '-warnings-as-errors', '-sdk', sdk,
                         '-target', 'arm64-apple-ios13.0-simulator']
            # Compile the actual composer hook against its declared UIView type.
            # Parsing alone cannot detect UIButton-only APIs used on that hook.
            composer = temp / 'submodules/TelegramUI/Components/Chat/ChatTextInputPanelNode/Sources/ChatTextInputPanelNode.swift'
            composer_text = composer.read_text(encoding='utf-8')
            hook_start = composer_text.index('        if let button = self.nebulaToolsButton {')
            hook_end = composer_text.index('{', hook_start) + 1
            depth = 1
            while depth:
                depth += (composer_text[hook_end] == '{') - (composer_text[hook_end] == '}')
                hook_end += 1
            hook = composer_text[hook_start:hook_end]
            composer_check = temp / 'ComposerUIKitCheck.swift'
            composer_check.write_text('''import UIKit
final class Background { let contentView = UIView() }
struct Transition {
    func updateFrame(layer: CALayer, frame: CGRect) { layer.frame = frame }
    func updateAlpha(layer: CALayer, alpha: CGFloat) { layer.opacity = Float(alpha) }
}
final class Composer {
    var nebulaToolsButton: UIView?
    var nebulaToolsWidth: CGFloat = 40
    let textInputContainerBackgroundView = Background()
    func layout(transition: Transition, nextButtonTopRight: inout CGPoint,
                minimalInputHeight: CGFloat, audioRecordingItemsAlpha: CGFloat) {
''' + hook + '''
    }
}
''', encoding='utf-8')
            subprocess.run(['swiftc', *ios_flags, '-typecheck', str(composer_check)], check=True)
            print('OK: actual composer layout hook typechecked as UIView against UIKit')
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
            # These views have no Telegram module dependency. Typecheck them
            # against UIKit early, including warnings-as-errors, before Bazel.
            zoom_slider = temp / 'submodules/TelegramUI/Components/VideoMessageCameraScreen/Sources/NebulaVideoZoomSlider.swift'
            subprocess.run(['swiftc', *ios_flags, '-typecheck', str(zoom_slider)], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck', '-I', str(temp),
                            str(settings_ui / 'NebulaActionGrid.swift')], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck',
                            str(settings_ui / 'NebulaToolsNavigationController.swift')], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck', '-I', str(temp),
                            str(temp / 'submodules/Display/Source/NebulaSwitchControl.swift')], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck',
                            str(temp / 'submodules/Display/Source/NebulaAIOutline.swift')], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck', '-I', str(temp),
                            str(settings_ui / 'NebulaAiService.swift'), str(settings_ui / 'NebulaAiModelCatalog.swift'), str(settings_ui / 'NebulaAudioService.swift')], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck', str(settings_ui / 'NebulaEditorSegments.swift')], check=True)
            subprocess.run(['swiftc', *ios_flags, '-typecheck', '-I', str(temp),
                            str(temp / 'submodules/NebulaBrowserCore/Sources/NebulaBrowserContentRules.swift')], check=True)
            ai_sources = ['NebulaSettingsStyle.swift', 'NebulaSettingsSymbols.swift', 'NebulaSettingsHero.swift',
                          'NebulaAudioTranscriptionController.swift', 'NebulaCloudSettingsController.swift', 'NebulaCloudSettingsSync.swift', 'NebulaChoiceController.swift', 'NebulaAiChatController.swift', 'NebulaAiController.swift',
                          'NebulaAiService.swift', 'NebulaAiHistoryController.swift', 'NebulaActionGrid.swift',
                          'NebulaResultLanguage.swift', 'NebulaMessageToolsController.swift', 'NebulaTasksController.swift',
                          'NebulaCommunity.swift', 'NebulaSupportController.swift', 'NebulaAiServicesController.swift',
                          'NebulaAiRolesController.swift', 'NebulaAnimatedSettingsEmoji.swift',
                          'NebulaSettingsIntroItem.swift', 'NebulaLinkPresentation.swift', 'NebulaAiEditorController.swift',
                          'NebulaEditorSegments.swift', 'NebulaDraftTranslation.swift', 'NebulaBrowserController.swift', 'NebulaMessageControlsController.swift', 'NebulaMessageFilterController.swift', 'NebulaPresentationPreviewController.swift', 'NebulaImportedIcons.swift', 'NebulaIconPacksController.swift']
            # These views now use Telegram's PresentationTheme module. Parse them
            # here, then typecheck against the real module graph in ios-native.yml.
            subprocess.run(['swiftc', '-frontend', '-parse', '-swift-version', '5',
                            *[str(settings_ui / name) for name in ai_sources]], check=True)
            print('OK: AI chat/settings/service parsed; full module typecheck runs in ios-native.yml')
            auth = temp / 'submodules/AuthorizationUI/Sources'
            subprocess.run(['swiftc', *ios_flags, '-typecheck', str(auth / 'NebulaAuthPresentation.swift'),
                            str(auth / 'NebulaWelcomeController.swift')], check=True)
            print('OK: UIKit file-transfer adapter typechecked against the real iOS simulator SDK')
            print('Native hooks parsed only. Full Telegram/Bazel build and iPhone acceptance remain required.')


if __name__ == '__main__':
    main()
