#!/usr/bin/env python3
"""Read-only patch-surface inventory, NOT proof of source/API compatibility."""
import argparse
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def report(platform):
    tree = {'android': 'vendor/telegram-android', 'ios': 'vendor/telegram-ios', 'desktop': 'vendor/tdesktop'}[platform]
    entry = subprocess.check_output(['git', '-C', str(ROOT), 'ls-tree', 'HEAD', '--', tree], text=True).strip()
    revision = entry.split()[2] if entry.startswith('160000 ') else None
    patches = []
    for path in sorted((ROOT / 'patches' / platform).glob('*.patch')):
        source = path.read_text(encoding='utf-8')
        files = sorted(set(re.findall(r'^\+\+\+ b/(.+)$', source, re.M)))
        patches.append({'patch': path.name, 'files': files,
                        'added': sum(line.startswith('+') and not line.startswith('+++') for line in source.splitlines()),
                        'removed': sum(line.startswith('-') and not line.startswith('---') for line in source.splitlines())})
    return {'platform': platform, 'pinned_revision': revision,
            'baseline_status': 'pinned' if revision else 'not_configured',
            'compatibility': 'not_tested', 'patches': patches,
            'touched_files': sorted({f for p in patches for f in p['files']})}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('platform', choices=['android', 'ios', 'desktop'])
    args = parser.parse_args()
    print(json.dumps(report(args.platform), ensure_ascii=False, indent=2))
