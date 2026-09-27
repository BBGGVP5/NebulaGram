#!/usr/bin/env python3
"""Package the Nebula mark as iOS's primary Icon Composer asset."""
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
VENDOR = ROOT / 'vendor/telegram-ios'
OUT = ROOT / 'platform/ios/overlay/Telegram/Telegram-iOS/NebulaGram.icon'


def upstream(path):
    return subprocess.check_output(['git', '-C', str(VENDOR), 'show', 'HEAD:' + path])


MARK = '''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 200 200">
  <title>NebulaGram mark</title>
  <g transform="translate(10 10) scale(.9)" stroke-linecap="round" stroke-linejoin="round">
    <path d="M148 52 96 138 88 104 54 96Z" fill="#EEF4FF" stroke="#EEF4FF" stroke-width="13"/>
    <path d="M44 148c14-6 26-9 38-9" fill="none" stroke="#BBD6FF" stroke-width="9" opacity=".855"/>
    <path d="M40 124c9-4 17-6 25-6" fill="none" stroke="#BBD6FF" stroke-width="7" opacity=".549"/>
  </g>
</svg>
'''.encode('utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    base = 'Telegram/Telegram-iOS/Telegram.icon/'
    icon = upstream(base + 'icon.json').replace(b'Plane.svg', b'NebulaMark.svg')
    # Telegram's glass composition remains native; only the mark and brand blue change.
    icon = icon.replace(b'srgb:0.11373,0.57647,0.82353,1.00000', b'srgb:0.23529,0.55294,0.94118,1.00000')
    icon = icon.replace(b'srgb:0.00000,0.47843,1.00000,1.00000', b'srgb:0.03922,0.19216,0.47843,1.00000')
    expected = {
        OUT / 'icon.json': icon,
        OUT / 'Assets/Oval.svg': upstream(base + 'Assets/Oval.svg'),
        OUT / 'Assets/NebulaMark.svg': MARK,
    }
    for path, data in expected.items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise SystemExit(f'Primary iOS icon mismatch: {path}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    print('OK: NebulaGram is the primary iOS Icon Composer asset')


if __name__ == '__main__':
    main()
