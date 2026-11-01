"""Check right status icon placement in centered and native chat headers."""
from pathlib import Path
import argparse


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tree', required=True, type=Path)
args = parser.parse_args()
source = (args.tree / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/SimpleTextView.java').read_text(encoding='utf-8')

start = source.index('if (rightDrawable != null && rightDrawableOutside) {', source.index('protected void onDraw'))
end = source.index('public int getRightDrawableX()', start)
blocks = source[start:end]
assert blocks.count('int centeredOffset =') == 2, 'Both outside icons must account for centering before clamping'
assert 'if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL) x += offsetX;' not in blocks, 'Late center offset bypasses the right-edge clamp'

for offset in (0, 14, 28, 60, 80, 100):
    text_width = 80
    view_width = 180
    icon_width = 24
    padding = 4
    max_text_width = view_width - icon_width - padding
    old_x = min(text_width + padding, max_text_width + padding) + offset
    new_x = min(text_width + padding + offset, max_text_width + padding)
    if offset in (80, 100):
        assert old_x + icon_width > view_width, f'Fixture no longer exposes old clipping at offset {offset}'
    assert new_x + icon_width <= view_width, f'Icon is clipped at offset {offset}'
    expected_x = min(text_width + padding + offset, max_text_width + padding)
    assert new_x == expected_x, f'Centered icon moved away from its title at offset {offset}'

# Native left-aligned headers retain the existing clamp.
for text_width in (40, 80, 160, 200):
    view_width = 180
    icon_width = 24
    padding = 4
    max_text_width = view_width - icon_width - padding
    x = min(text_width + padding, max_text_width + padding)
    assert x + icon_width <= view_width

print('Centered and native status-icon bounds passed; old centered clipping reproduced')
