#!/usr/bin/env python3
"""Check the pinned iOS patch/overlay in a temporary tree, never reset the vendor.

--swift additionally parses native hooks (NOT a Telegram typecheck/build) and
compiles/runs the actual Bazel-side Foundation sources without SWIFT_PACKAGE.
"""
import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

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
    entry = run('git', '-C', str(ROOT), 'ls-files', '--stage', '--', 'vendor/telegram-ios', text=True).split()
    if not entry or entry[0] != '160000':
        raise SystemExit('Missing pinned iOS gitlink')
    revision = entry[1]
    actual = run('git', '-C', str(tree), 'rev-parse', 'HEAD', text=True).strip()
    if actual != revision:
        raise SystemExit(f'Upstream revision mismatch: expected {revision}, found {actual}')
    patches = sorted((ROOT / 'patches/ios').glob('*.patch'))
    paths = set()
    for patch in patches:
        pairs = re.findall(r'^diff --git a/(\S+) b/(\S+)$', patch.read_text(encoding='utf-8'), re.M)
        if not pairs:
            raise SystemExit(f'Empty patch: {patch.name}')
        for a, b in pairs:
            if a != b or not a.startswith('submodules/') or '..' in Path(a).parts or '\\' in a:
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
        assert 'case nebulaGram' in (peer / 'PeerInfoScreen.swift').read_text(encoding='utf-8')
        assert 'interaction.openSettings(.nebulaGram)' in (peer / 'PeerInfoSettingsItems.swift').read_text(encoding='utf-8')
        assert 'push(nebulaSettingsController(context: self.context))' in (peer / 'PeerInfoScreenSettingsActions.swift').read_text(encoding='utf-8')
        print(f'OK: {len(patches)} ordered iOS patch(es), {len(paths)} upstream paths, overlay/hooks, pin {revision}', flush=True)
        if args.swift:
            for source in sorted(temp.rglob('*.swift')):
                subprocess.run(['swiftc', '-frontend', '-parse', str(source)], check=True)
            contract = sorted((temp / 'submodules/NebulaSettingsContract/Sources').glob('*.swift'))
            main_file = temp / 'main.swift'
            main_file.write_text('''import Foundation
let catalog = try SettingsCatalog.bundled()
precondition(catalog.settings.count == 63)
let suite = "NebulaBazelSmoke.\\(UUID().uuidString)"
let defaults = UserDefaults(suiteName: suite)!
defer { defaults.removePersistentDomain(forName: suite) }
let store = NebulaSettingsStore(defaults: defaults)
precondition(!store.hasLoadError && !store.hideTabCounters)
try store.set(.boolean(true), for: "hide_tab_counters")
precondition(NebulaSettingsStore(defaults: defaults).hideTabCounters)
print("OK: embedded catalog and Bazel-side Foundation store compiled and ran")
''', encoding='utf-8')
            executable = temp / 'smoke'
            subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', *map(str, contract), str(main_file), '-o', str(executable)], check=True)
            subprocess.run([str(executable)], check=True)
            print('Native hooks parsed only. Full Telegram/Bazel build and iPhone acceptance remain required.')


if __name__ == '__main__':
    main()
