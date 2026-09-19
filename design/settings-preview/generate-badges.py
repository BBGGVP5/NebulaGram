"""Reuse the exact Android vector paths in the browser design preview."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

here = Path(__file__).resolve().parent
drawables = here.parents[1] / 'platform/android/overlay/TMessagesProj/src/main/res/drawable'
ns = '{http://schemas.android.com/apk/res/android}'
badges = {}
names = {'pathData': 'd', 'fillColor': 'fill', 'strokeColor': 'stroke',
         'strokeWidth': 'stroke-width', 'strokeLineCap': 'stroke-linecap', 'strokeLineJoin': 'stroke-linejoin'}
for kind in ['supporter', 'dev', 'tester', 'star', 'heart']:
    vector = ET.parse(drawables / f'nebula_badge_{kind}.xml').getroot()
    svg = ET.Element('svg', {'viewBox': '0 0 28 28', 'aria-hidden': 'true'})
    for path in vector.findall('path'):
        attrs = {name: path.get(ns + android) for android, name in names.items() if path.get(ns + android) is not None}
        if attrs.get('fill') == '#00000000': attrs['fill'] = 'none'
        ET.SubElement(svg, 'path', attrs)
    badges[kind] = ET.tostring(svg, encoding='unicode')
(here / 'badges.js').write_text('// Generated from Android vector artwork.\nwindow.NEBULA_BADGES = ' + json.dumps(badges, ensure_ascii=False) + ';\n', encoding='utf-8')
