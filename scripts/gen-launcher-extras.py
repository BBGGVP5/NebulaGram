#!/usr/bin/env python3
"""Extend the original code-native Nebula mark; never rewrite the old six icons.

Run: python scripts/gen-launcher-extras.py (Pillow required).
SVG masters and the contact sheet are generated alongside full-bleed 1024px PNGs.
"""
from pathlib import Path
import importlib.util
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'platform/android/overlay/TMessagesProj/src/main/res'
OUT = ROOT / 'design/icon/variants'
spec = importlib.util.spec_from_file_location('nebula_mark', ROOT / 'scripts/gen-icons.py')
mark = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mark)

# key, Russian name, gradient start/end, glyph, trails, optional background motif
VARIANTS = [
    ('ink', 'Чернила', '#101112', '#101112', '#FFFFFF', '#FFFFFF', ''),
    ('paper', 'Бумага', '#FAF6EC', '#FAF6EC', '#272724', '#77736A', ''),
    ('mint', 'Мята', '#C9F8DE', '#81DCC7', '#104E43', '#297E6A', ''),
    ('lavender', 'Лаванда', '#EBDEFF', '#BBA3EE', '#4C2D7B', '#72569E', ''),
    ('tangerine', 'Мандарин', '#FFD687', '#FF8B4E', '#542714', '#854222', ''),
    ('rose', 'Роза', '#FFDCE9', '#E995BC', '#75284E', '#98496E', ''),
    ('orbit', 'Орбита', '#25274F', '#111323', '#FFF4CF', '#C7BCE4', 'orbit'),
    ('blueprint', 'Чертёж', '#2871C5', '#143E81', '#FFFFFF', '#BFDEFF', 'grid'),
    ('nova', 'Нова', '#152E3E', '#091C2A', '#B1F9E7', '#B1F9E7', ''),
    ('monogram', 'Монограмма', '#EAF0F9', '#D2DBEC', '#273957', '#637CBE', ''),
]

# Alternate original logos, expressed once as vector polygons. The same geometry
# drives SVG, the preview/legacy PNGs and the Android themed monochrome layer.
LOGOS = {
    'nova': [
        ([(58,50),(144,50),(157,63),(157,124),(144,138),(94,138),(64,157),(64,138),(48,124),(48,63),(58,50)], False, 9),
        ([(104,68),(112,87),(132,95),(112,103),(104,123),(96,103),(76,95),(96,87)], True, 2),
    ],
    'monogram': [
        ([(59,145),(59,55),(80,55),(122,110),(122,55),(143,55),(143,145),(122,145),(80,90),(80,145)], True, 3),
    ],
}


def logo_paths(key):
    return [('M' + ' L'.join(f'{x},{y}' for x,y in points) + ' Z', filled, width)
            for points, filled, width in LOGOS[key]]


def alternate_mark(canvas, key, scale, inset, ink):
    draw = ImageDraw.Draw(canvas)
    for points, filled, width in LOGOS[key]:
        p = [(x*scale+inset, y*scale+inset) for x,y in points]
        if filled:
            draw.polygon(p, fill=rgb(ink)+(255,))
        mark.stroke(draw, p+[p[0]], width*scale, rgb(ink)+(255,))


def rgb(value):
    return tuple(int(value[i:i+2], 16) for i in (1, 3, 5))


def background(size, recipe):
    _, _, start, end, _, _, motif = recipe
    image = Image.new('RGBA', (size, size))
    draw = ImageDraw.Draw(image)
    a, b = rgb(start), rgb(end)
    for step in range(size * 2):
        t = step / (size * 2 - 1)
        color = tuple(round(x + (y - x) * t) for x, y in zip(a, b))
        draw.line([(step, 0), (0, step)], fill=color + (255,), width=2)
    layer = Image.new('RGBA', image.size)
    draw = ImageDraw.Draw(layer)
    s = size / 200
    if motif == 'grid':
        for n in range(0, 201, 25):
            draw.line([(n*s, 0), (n*s, size)], fill=(255,255,255,28), width=max(1, round(.6*s)))
            draw.line([(0, n*s), (size, n*s)], fill=(255,255,255,28), width=max(1, round(.6*s)))
    elif motif == 'orbit':
        for radius in (75, 111, 147):
            draw.ellipse(tuple(x*s for x in (156-radius, 144-radius, 156+radius, 144+radius)),
                         outline=(209,198,250,52), width=max(1, round(1*s)))
        draw.ellipse((154*s, 65*s, 158*s, 69*s), fill=(255,244,207,230))
    return Image.alpha_composite(image, layer)


def render(size, recipe, foreground=False):
    big = size * 2
    canvas = Image.new('RGBA', (big,big)) if foreground else background(big, recipe)
    glyph = Image.new('RGBA', (big,big))
    mark.INK, mark.TRAIL_INK = rgb(recipe[4]), rgb(recipe[5])
    scale = big / 200 * (.6 if foreground else .9)
    inset = (big - 200 * scale) / 2
    if recipe[0] in LOGOS:
        alternate_mark(glyph, recipe[0], scale, inset, recipe[4])
    else:
        mark.draw_mark(glyph, scale, (inset,inset), opaque=False)
    return Image.alpha_composite(canvas, glyph).resize((size,size), Image.Resampling.LANCZOS)


def svg(recipe):
    key, _, start, end, ink, trail, motif = recipe
    patterns = ''
    if motif == 'grid':
        patterns = ''.join(f'<path d="M{n} 0V200 M0 {n}H200"/>' for n in range(0,201,25))
        patterns = f'<g fill="none" stroke="white" stroke-opacity=".11" stroke-width=".6">{patterns}</g>'
    elif motif == 'orbit':
        patterns = ''.join(f'<circle cx="156" cy="144" r="{r}"/>' for r in (75,111,147))
        patterns = f'<g fill="none" stroke="#D1C6FA" stroke-opacity=".204">{patterns}</g><circle cx="156" cy="67" r="2" fill="#FFF4CF"/>'
    paths = ''.join(f'<path d="{d}" fill="{ink if filled else "none"}" stroke="{ink}" stroke-width="{width}"/>'
                    for d,filled,width in logo_paths(key)) if key in LOGOS else f'''
<path d="M148 52 96 138 88 104 54 96Z" fill="{ink}" stroke="{ink}" stroke-width="13"/>
<path d="M44 148c14-6 26-9 38-9" fill="none" stroke="{trail}" stroke-width="9" opacity=".855"/>
<path d="M40 124c9-4 17-6 25-6" fill="none" stroke="{trail}" stroke-width="7" opacity=".549"/>'''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="1024" height="1024">
<title>NebulaGram — {key.title()}</title>
<defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="{start}"/><stop offset="1" stop-color="{end}"/></linearGradient></defs>
<path fill="url(#bg)" d="M0 0H200V200H0Z"/>{patterns}
<g transform="translate(10 10) scale(.9)" stroke-linecap="round" stroke-linejoin="round">
{paths}
</g></svg>
'''


def xml_background(recipe):
    _, _, start, end, _, _, motif = recipe
    shape = f'<shape android:shape="rectangle"><gradient android:startColor="{start}" android:endColor="{end}" android:angle="315" /></shape>'
    patterns = ''
    if motif == 'grid':
        path = ' '.join(f'M{n},0 L{n},200 M0,{n} L200,{n}' for n in range(0,201,25))
        patterns = f'<path android:pathData="{path}" android:strokeColor="#1CFFFFFF" android:strokeWidth="0.6" android:fillColor="#00000000" />'
    elif motif == 'orbit':
        for r in (75,111,147):
            path = f'M{156-r},144 a{r},{r} 0,1 0,{r*2},0 a{r},{r} 0,1 0,{-r*2},0'
            patterns += f'<path android:pathData="{path}" android:strokeColor="#34D1C6FA" android:strokeWidth="1" android:fillColor="#00000000" />'
        patterns += '<path android:pathData="M154,67 a2,2 0,1 0,4,0 a2,2 0,1 0,-4,0" android:fillColor="#E6FFF4CF" />'
    vector = f'<item><vector android:width="108dp" android:height="108dp" android:viewportWidth="200" android:viewportHeight="200">{patterns}</vector></item>' if patterns else ''
    return f'<?xml version="1.0" encoding="utf-8"?>\n<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item>{shape}</item>{vector}</layer-list>\n'


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8', newline='\n')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for recipe in VARIANTS:
        key = recipe[0]
        write(OUT / f'{key}.svg', svg(recipe))
        render(1024, recipe).convert('RGB').save(OUT / f'{key}-1024.png', optimize=True)
        write(RES / f'drawable/nebula_launcher_{key}_background.xml', xml_background(recipe))
        monochrome = 'nebula_launcher_monochrome'
        if key in LOGOS:
            monochrome = f'nebula_launcher_{key}_monochrome'
            paths = ''.join(f'<path android:pathData="{d}" android:fillColor="{"#FFFFFFFF" if filled else "#00000000"}" android:strokeColor="#FFFFFFFF" android:strokeWidth="{width}" android:strokeLineJoin="round" android:strokeLineCap="round" />' for d,filled,width in logo_paths(key))
            write(RES / f'drawable/{monochrome}.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="200" android:viewportHeight="200">
<group android:scaleX="0.6" android:scaleY="0.6" android:pivotX="100" android:pivotY="100">{paths}</group></vector>
''')
        write(RES / f'mipmap-anydpi-v26/nebula_launcher_{key}.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/nebula_launcher_{key}_background" />
    <foreground android:drawable="@mipmap/nebula_launcher_{key}_foreground" />
    <monochrome android:drawable="@drawable/{monochrome}" />
</adaptive-icon>
''')
        for density, factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
            folder = RES / f'mipmap-{density}'
            folder.mkdir(parents=True, exist_ok=True)
            render(round(48*factor), recipe).convert('RGB').save(folder / f'nebula_launcher_{key}.png', optimize=True)
            render(round(108*factor), recipe, True).save(folder / f'nebula_launcher_{key}_foreground.png', optimize=True)
    for locale in ('values','values-ru'):
        path = RES / locale / 'nebula_launcher.xml'
        source = path.read_text(encoding='utf-8')
        for recipe in VARIANTS:
            key, ru = recipe[:2]
            name = f'NebulaLauncher{key.title()}'
            if ET.fromstring(source).find(f"string[@name='{name}']") is None:
                label = ru if locale == 'values-ru' else key.title()
                source = source.replace('</resources>', f'    <string name="{name}">{label}</string>\n</resources>')
        write(path, source)
    # A presentation of the code-native assets, not a simulated app screenshot.
    board = Image.new('RGB', (1480,790), '#101318')
    draw = ImageDraw.Draw(board)
    font_path = next((p for p in [Path('C:/Windows/Fonts/segoeui.ttf'), Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')] if p.exists()), None)
    font = lambda n: ImageFont.truetype(str(font_path), n) if font_path else ImageFont.load_default()
    draw.text((54,32), 'NEBULAGRAM / ICON COLLECTION', font=font(24), fill='#BDC6D5')
    for i, recipe in enumerate(VARIANTS):
        x, y = 55 + (i%5)*290, 110 + (i//5)*320
        icon = mark.rounded(render(220, recipe))
        board.paste(icon, (x,y), icon)
        draw.text((x+110,y+244), recipe[1], anchor='mt', font=font(25), fill='#EDF2F9')
    board.save(OUT / 'collection.png', optimize=True)
    write(OUT / 'README.md', '# NebulaGram — ten additional launcher icons\n\nGenerated by `python scripts/gen-launcher-extras.py` from the existing NebulaGram vector geometry plus two original vector logos: Nova (star/message) and Monogram (N). SVGs and opaque 1024px PNGs are full bleed; corner masks belong to the launcher. `collection.png` is an asset contact sheet, not a device screenshot. Android resources include legacy densities, adaptive layers and themed monochrome (the new logos retain their own shape). Existing six choices and aliases are unchanged. iOS-ready masters are provided, but this change only registers new choices on Android.\n')
    print('Generated ten variants, Android resources and design/icon/variants/collection.png')


if __name__ == '__main__':
    main()
