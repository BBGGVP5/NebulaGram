#!/usr/bin/env python3
"""Apply the ordered patch series in a disposable tree; never reset the vendor checkout."""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent


def check_series(platform, tree, ref='HEAD'):
    tree = Path(tree).resolve()
    revision = subprocess.check_output(['git', '-C', str(tree), 'rev-parse', '--verify', '--end-of-options', ref + '^{commit}'], text=True).strip()
    patches = sorted((ROOT / 'patches' / platform).glob('*.patch'))
    if not patches:
        print(f'{platform}: no patches; UI/API compatibility NOT tested')
        return
    paths = set()
    for patch in patches:
        for a, b in re.findall(r'^diff --git a/(\S+) b/(\S+)$', patch.read_text(encoding='utf-8'), re.M):
            paths.update((a, b))
    for path in paths:
        if Path(path).is_absolute() or '..' in Path(path).parts:
            raise ValueError('Unsafe patch path: ' + path)
    existing = subprocess.check_output(['git', '-C', str(tree), 'ls-tree', '-r', '--name-only', revision, '--', *sorted(paths)], text=True).splitlines()
    with tempfile.TemporaryDirectory(prefix='nebula-upstream-') as temp:
        temp = Path(temp)
        subprocess.run(['git', 'init', '--quiet', str(temp)], check=True)
        for path in existing:
            target = temp / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(subprocess.check_output(['git', '-C', str(tree), 'show', revision + ':' + path]))
        for patch in patches:
            result = subprocess.run(['git', '-C', str(temp), 'apply', '--whitespace=nowarn', str(patch)], capture_output=True)
            if result.returncode:
                raise SystemExit(patch.name + ':\n' + result.stderr.decode('utf-8', errors='replace'))
    print(f'{platform}: {len(patches)} ordered patches apply to {revision}; vendor untouched. Compile/runtime validation still required.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('platform', choices=['android', 'ios', 'desktop'])
    parser.add_argument('--tree', required=True, help='Local upstream git checkout (may contain unrelated working changes)')
    parser.add_argument('--ref', default='HEAD', help='Already fetched revision; this command never fetches')
    args = parser.parse_args()
    check_series(args.platform, args.tree, args.ref)
