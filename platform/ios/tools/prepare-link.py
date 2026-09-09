#!/usr/bin/env python3
"""Build the pinned local Go binding into a fresh iOS build tree, on macOS only."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tree', required=True, type=Path)
    args = p.parse_args()
    if sys.platform != 'darwin': raise SystemExit('The real iOS XCFramework requires macOS/Xcode')
    tree = args.tree.resolve()
    if tree == ROOT or tree.is_relative_to(ROOT / 'vendor'):
        raise SystemExit('Use a separate prepared build tree, not vendor')
    destination = tree / 'submodules/NebulaLinkCore/NebulaLink.xcframework'
    if destination.exists(): raise SystemExit('Refusing to overwrite an existing framework')
    tooling = tree / 'build-input/nebula-go-tools'
    tooling.mkdir(parents=True, exist_ok=False)
    # Resolve versions through bind/go.mod, not @latest. Go auto-selects its required toolchain.
    for name in ['gomobile', 'gobind']:
        subprocess.run(['go', 'build', '-o', str(tooling / name), 'golang.org/x/mobile/cmd/' + name], cwd=ROOT / 'bind', check=True)
    env = dict(os.environ, PATH=str(tooling) + os.pathsep + os.environ['PATH'])
    subprocess.run([str(tooling / 'gomobile'), 'init'], cwd=ROOT / 'bind', env=env, check=True)
    subprocess.run([str(tooling / 'gomobile'), 'bind', '-target=ios,iossimulator', '-iosversion=13.0',
                    '-ldflags=-s -w', '-o', str(destination), './mobile'], cwd=ROOT / 'bind', env=env, check=True)
    # Fail if Go's exported ABI differs from the Swift consumer; no substitute/stub engine.
    headers = list(destination.rglob('Nebulalink.objc.h'))
    if len(headers) < 2: raise SystemExit('Missing device/simulator binding headers')
    for header in headers:
        text = header.read_text(encoding='utf-8')
        if 'NebulalinkCall(' not in text or 'NebulalinkVersion(' not in text:
            raise SystemExit('Unexpected gomobile API')
    print(json.dumps({'framework': 'NebulaLink', 'source': 'local bind/mobile + runtime/xray', 'slices': len(headers)}))


if __name__ == '__main__': main()
