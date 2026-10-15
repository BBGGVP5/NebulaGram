#!/usr/bin/env python3
"""Typecheck production UIKit components with an installed Darwin Swift SDK.

Run inside WSL/Linux with Swift and the SDK installed by xtool. This is a
component/API check, not a full Telegram/Bazel build or an installable IPA.
The SDK and private Xcode archive are never copied into the output.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[3]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-bundle', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path, help='New directory for modules/cache/receipt')
    parser.add_argument('--swiftc', default='swiftc')
    args = parser.parse_args()
    bundle = args.sdk_bundle.resolve()
    metadata = json.loads((bundle / 'swift-sdk.json').read_text(encoding='utf-8'))
    target = metadata['targetTriples']['arm64-apple-ios']
    sdk = (bundle / target['sdkRootPath']).resolve()
    resources = (bundle / target['swiftResourcesPath']).resolve()
    if not sdk.is_dir() or not resources.is_dir():
        raise SystemExit('Installed SDK resources are missing; finish xtool SDK setup first')
    output = args.output.resolve()
    if output.exists() or args.output.is_symlink():
        raise SystemExit('Output must be a new directory; existing files are never reset or deleted')
    compiler = shutil.which(args.swiftc)
    if compiler is None:
        raise SystemExit('Swift compiler is not available; run in the configured WSL Swift environment')
    output.mkdir(parents=True)
    flags = ['-swift-version', '5', '-target', 'arm64-apple-ios15.0', '-sdk', str(sdk),
             '-resource-dir', str(resources), '-Xfrontend', '-enable-cross-import-overlays',
             '-module-cache-path', str(output / 'cache')]

    def run(*arguments):
        subprocess.run([compiler, *flags, *arguments], check=True)

    def adapter(name, source):
        path = output / (name + '.swift')
        path.write_text(source, encoding='utf-8')
        run('-emit-module', '-parse-as-library', '-module-name', name, str(path),
            '-emit-module-path', str(output / (name + '.swiftmodule')))

    # Module-shape adapters only. UIKit, Foundation and Objective-C APIs are real.
    # The actual Telegram/ASDK module linkage is checked by the full native build.
    adapter('TelegramPresentationData', '''import UIKit
public struct ThemeList {
 public var itemAccentColor: UIColor = .systemTeal
 public var itemPrimaryTextColor: UIColor = .label
 public var itemSecondaryTextColor: UIColor = .secondaryLabel
}
public struct ThemeChatList { public var backgroundColor: UIColor = .systemBackground }
public final class PresentationTheme { public var list = ThemeList(); public var chatList = ThemeChatList() }
''')
    adapter('AsyncDisplayKit', '''import UIKit
public class ASDisplayNode: NSObject { public let view = UIView() }
''')
    overlay = ROOT / 'platform/ios/overlay/submodules'
    contract = sorted((overlay / 'NebulaSettingsContract/Sources').glob('*.swift'))
    if not contract:
        raise SystemExit('Generated Foundation overlay is missing; run generate-overlay.py')
    run('-emit-module', '-parse-as-library', '-module-name', 'NebulaSettingsContract',
        *map(str, contract), '-emit-module-path', str(output / 'NebulaSettingsContract.swiftmodule'))
    components = [
        overlay / 'AuthorizationUI/Sources/NebulaAuthPresentation.swift',
        overlay / 'AuthorizationUI/Sources/NebulaWelcomeController.swift',
        overlay / 'NebulaLinkUI/Sources/NebulaLinkOverviewView.swift',
        overlay / 'TelegramUI/Components/Chat/ChatTextInputPanelNode/Sources/NebulaFormattingToolbar.swift',
        overlay / 'ChatListUI/Sources/NebulaChatListPrivacy.swift',
    ]
    run('-typecheck', '-I', str(output), *map(str, components))
    revision = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()
    changed = subprocess.check_output(['git', '-C', str(ROOT), 'status', '--porcelain', '--',
                                       *[path.relative_to(ROOT).as_posix() for path in contract + components]], text=True).strip()
    receipt = dict(mode='component-typecheck', fullApplication=False, deviceTested=False,
                   deploymentTarget='arm64-apple-ios15.0', sourceRevision=revision,
                   uncommittedProductionSources=bool(changed),
                   swiftVersion=subprocess.check_output([compiler, '--version'], text=True).strip(),
                   sdkRoot=str(sdk), moduleAdapters=['TelegramPresentationData', 'AsyncDisplayKit'],
                   productionSources={path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                                      for path in contract + components})
    (output / 'verification.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(f'{len(contract)} production Foundation files and {len(components)} UIKit components typechecked; full native build remains separate')


if __name__ == '__main__':
    main()
