#!/usr/bin/env python3
"""Export Nebula's existing launcher artwork for iOS alternate icons.

Generation needs Pillow; --check needs only the Python standard library.
"""
import argparse
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'platform/android/overlay/TMessagesProj/src/main/res/mipmap-xxxhdpi'
DEST = ROOT / 'platform/ios/overlay/Telegram/Telegram-iOS'
VARIANTS = ('Blue', 'Ocean', 'Aurora', 'Sunset', 'Graphite', 'Pearl', 'Ink', 'Paper',
            'Mint', 'Lavender', 'Tangerine', 'Rose', 'Orbit', 'Blueprint', 'Nova', 'Monogram')
SIZES = {'@2x': 120, '@3x': 180, 'Ipad': 76, 'Ipad@2x': 152, 'LargeIpad@2x': 167}


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) != 24 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
        raise ValueError(f'Invalid PNG: {path}')
    return struct.unpack('>II', data[16:24])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if not args.check:
        from PIL import Image
    for variant in VARIANTS:
        base = f'Nebula{variant}Icon'
        source = SOURCE / f'nebula_launcher_{variant.lower()}.png'
        if png_size(source) != (192, 192):
            raise SystemExit(f'Unexpected Android source dimensions: {source}')
        folder = DEST / f'{base}.alticon'
        if not args.check:
            folder.mkdir(parents=True, exist_ok=True)
            with Image.open(source) as image:
                image = image.convert('RGB')
                for suffix, size in SIZES.items():
                    image.resize((size, size), Image.Resampling.LANCZOS).save(folder / f'{base}{suffix}.png', optimize=True)
        for suffix, size in SIZES.items():
            output = folder / f'{base}{suffix}.png'
            if not output.exists() or png_size(output) != (size, size):
                raise SystemExit(f'Missing or invalid iOS alternate icon: {output}')
    print(f'OK: {len(VARIANTS)} Nebula alternate icon sets with iPhone/iPad sizes')


if __name__ == '__main__':
    main()
