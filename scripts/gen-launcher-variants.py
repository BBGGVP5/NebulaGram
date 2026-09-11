"""Generate original Android launcher choices from the shared NebulaGram mark.

Run: python scripts/gen-launcher-variants.py
No activity identities or upstream resources are renamed by this generator.
"""
from pathlib import Path
import importlib.util
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'platform/android/overlay/TMessagesProj/src/main/res'
spec = importlib.util.spec_from_file_location('nebula_mark', ROOT / 'scripts/gen-icons.py')
mark = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mark)

# key, gradient start/end, ink, trail ink, English/Russian titles
VARIANTS = [
    ('blue', '3C8DF0', '0A317A', 'EEF4FF', 'BBD6FF', 'Nebula', 'Nebula'),
    ('ocean', '2BD6C1', '086A83', 'F0FFFD', 'BEFFF3', 'Ocean', 'Океан'),
    ('aurora', 'B977F1', '40328E', 'FAF3FF', 'E4C7FF', 'Aurora', 'Аврора'),
    ('sunset', 'FFAE75', 'BD385E', 'FFFAEE', 'FFE4C9', 'Sunset', 'Закат'),
    ('graphite', '525D70', '171D29', 'F0F4FA', 'C4CFDF', 'Graphite', 'Графит'),
    ('pearl', 'F8F4ED', 'BDCFDE', '315376', '6C89A7', 'Pearl', 'Жемчуг'),
]
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

def rgb(value):
    return tuple(bytes.fromhex(value))

def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8', newline='\n')

def generate():
    sheet = Image.new('RGB', (900, 220), '#171D29')
    mono = (ROOT / 'platform/android/overlay/TMessagesProj_AppStandalone/src/main/res/drawable/nebula_icon_plane.xml').read_text(encoding='utf-8')
    write(RES / 'drawable/nebula_launcher_monochrome.xml', mono)
    for index, (key, start, end, ink, trail, en, ru) in enumerate(VARIANTS):
        mark.GRADIENT_FROM, mark.GRADIENT_TO = rgb(start), rgb(end)
        mark.INK, mark.TRAIL_INK = rgb(ink), rgb(trail)
        for density, factor in DENSITIES.items():
            folder = RES / f'mipmap-{density}'
            folder.mkdir(parents=True, exist_ok=True)
            mark.square_icon(round(48 * factor)).save(folder / f'nebula_launcher_{key}.png', optimize=True)
            mark.foreground_layer(round(108 * factor)).save(folder / f'nebula_launcher_{key}_foreground.png', optimize=True)
        write(RES / f'drawable/nebula_launcher_{key}_background.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:startColor="#{start}" android:endColor="#{end}" android:angle="315" />
</shape>
''')
        write(RES / f'mipmap-anydpi-v26/nebula_launcher_{key}.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/nebula_launcher_{key}_background" />
    <foreground android:drawable="@mipmap/nebula_launcher_{key}_foreground" />
    <monochrome android:drawable="@drawable/nebula_launcher_monochrome" />
</adaptive-icon>
''')
        tile = mark.rounded(mark.square_icon(120))
        sheet.paste(tile, (15 + index * 150, 24), tile)
        ImageDraw.Draw(sheet).text((25 + index * 150, 165), en, fill='white')
    for directory, language_index in [('values', 5), ('values-ru', 6)]:
        strings = '\n'.join(f'    <string name="NebulaLauncher{v[0].title()}">{v[language_index]}</string>' for v in VARIANTS)
        write(RES / directory / 'nebula_launcher.xml', f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{strings}\n</resources>\n')
    (ROOT / 'build').mkdir(exist_ok=True)
    sheet.save(ROOT / 'build/nebula-launcher-variants.png')
    print('Generated six launcher variants, five densities, adaptive and monochrome layers.')

if __name__ == '__main__':
    generate()
