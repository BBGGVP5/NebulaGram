"""Run the real corner maths in a JVM fixture, then check where it is wired."""
from pathlib import Path
import re
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
shape = (ui / 'NebulaShape.java').read_text(encoding='utf-8')


def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


# The corner table is the feature; copying it into the fixture would let the
# shipped one drift silently, so it is lifted out of the production file.
table = re.search(r'private static final float\[\]\[\] CORNERS = \{.*?\};', shape, re.S)
assert table, 'the shape table must stay a single literal the check can lift'

source = '''
public class ShapeCheck {
 static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
 static void near(float actual, float expected, String why) {
  if (Math.abs(actual - expected) > .001f) throw new AssertionError(why + ": " + actual + " != " + expected);
 }
 static class AndroidUtilities { static int dp(float v) { return Math.round(v * 3); } }
 static class NebulaAppearance { static int percent = 100; static int avatarRound() { return percent; } }
 static int roundnessValue = 100; static boolean uniformValue;
 static int roundness() { return roundnessValue; }
 static boolean uniform() { return uniformValue; }
 static int shapeValue; static int avatarShape() { return shapeValue; }
 static int gapValue = 3; static int sectionGap() { return gapValue; }
 GAP
 TABLE
 static float[] cornerFactors(int s) { return CORNERS[Math.max(0, Math.min(CORNERS.length - 1, s))]; }
 RADIUS
 AVATAR
 public static void main(String[] args) {
  int[] out = new int[4];
  // Круг занимает весь радиус, квадрат — ноль: между ними лежит всё остальное.
  for (int s = 0; s < CORNERS.length; s++) {
   shapeValue = s;
   for (int side : new int[]{24, 44, 120, 300}) {
    avatarRadii(side, out);
    for (int c = 0; c < 4; c++) {
     check(out[c] >= 0 && out[c] <= side / 2 + 1, "corner within half the side");
     near(out[c], Math.round(side * .5f * CORNERS[s][c]), "corner follows its factor");
    }
   }
  }
  shapeValue = 0; // круг
  avatarRadii(100, out);
  check(out[0] == 50 && out[3] == 50, "a circle rounds by half the side");
  shapeValue = 3; // квадрат
  avatarRadii(100, out);
  check(out[0] == 0 && out[2] == 0, "a square keeps its corners");

  // Проценты закругления остаются главными: форма им подчиняется.
  NebulaAppearance.percent = 50; shapeValue = 0;
  avatarRadii(100, out);
  check(out[0] == 25, "the corner percentage scales every shape");
  NebulaAppearance.percent = 0;
  avatarRadii(100, out);
  check(out[0] == 0, "zero percent squares every shape");
  NebulaAppearance.percent = 100;

  // Пока единое скругление выключено, поверхности не меняются ни на пиксель.
  uniformValue = false;
  for (int r : new int[]{0, 35, 70, 100}) {
   roundnessValue = r;
   check(radius(20) == AndroidUtilities.dp(20), "surfaces keep their own radius while uniform corners are off");
  }
  uniformValue = true;
  roundnessValue = 100;
  check(radius(20) == AndroidUtilities.dp(20), "full roundness keeps the natural radius");
  roundnessValue = 0;
  check(radius(20) == 0, "straight corners remove the radius");
  roundnessValue = 50;
  check(radius(20) == AndroidUtilities.dp(10), "half roundness halves the radius");
  // Середина ступеней не должна ничего двигать: настройка сдвигает плотность
  // вокруг нынешних отступов, а не назначает их заново.
  gapValue = 3;
  check(gap(12) == AndroidUtilities.dp(12), "the middle density step keeps today's spacing");
  gapValue = 0;
  check(gap(12) < AndroidUtilities.dp(12), "the lowest step tightens spacing");
  gapValue = 6;
  check(gap(12) > AndroidUtilities.dp(12), "the highest step loosens spacing");
  for (int step = 0; step <= 6; step++) { gapValue = step; check(gap(12) >= 0, "spacing stays positive"); }
  System.out.println("PASS: " + CORNERS.length + " profile photo shapes, uniform corners and density steps");
 }
}
'''
source = source.replace('TABLE', table.group(0).replace('private static final', 'static final'))
source = source.replace('RADIUS', method(shape, 'public static int radius(float naturalDp)')
                        .replace('org.telegram.messenger.AndroidUtilities', 'AndroidUtilities'))
source = source.replace('AVATAR', method(shape, 'public static void avatarRadii(float side, int[] out)'))
source = source.replace('GAP', method(shape, 'public static int gap(float naturalDp)')
                        .replace('org.telegram.messenger.AndroidUtilities', 'AndroidUtilities'))

with tempfile.TemporaryDirectory(prefix='nebula-shape-') as directory:
    work = Path(directory)
    target = work / 'ShapeCheck.java'
    target.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(target)], check=True)
    subprocess.run(['java', '-cp', str(work), 'ShapeCheck'], check=True)

# A setting nothing reads is the failure mode this project has already had once.
image = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java').read_text(encoding='utf-8')
assert 'NebulaShape.avatarRadii(Math.min(imageW, imageH), nebulaAvatarCorners)' in image, \
    'profile photos must take their corners from the shape setting'
assert 'private final int[] nebulaAvatarCorners' in image, \
    'the draw path must reuse one array rather than allocate per frame'
assert 'new int[4]' in image and 'avatarRadii(Math.min' in image
cell = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java').read_text(encoding='utf-8')
assert 'if (!app.nebulagram.ui.NebulaShape.tails()) drawPinnedBottom = true;' in cell, \
    'turning tails off must reach the bubble that draws them'
card = (ui / 'NebulaCard.java').read_text(encoding='utf-8')
assert 'NebulaShape.radius(20)' in card, 'settings cards must follow the uniform corner setting'
menu = (ui / 'NebulaMenuStyle.java').read_text(encoding='utf-8')
assert 'NebulaShape.radius(' in menu, 'menus must follow the uniform corner setting'
preview = (ui / 'NebulaShapePreview.java').read_text(encoding='utf-8')
assert 'NebulaShape.radius(' in preview and 'NebulaShape.avatarRadii(' in preview, \
    'the preview must read the same values the app does, not redraw its own guess'
# Each of the three sliders has to move something different, or they are one
# slider wearing three labels.
assert 'NebulaMediaSize.stickerScale()' in cell, 'stickers must read their own scale'
assert 'NebulaMediaSize.emojiScale()' in cell, 'animated emoji must read their own scale'
assert 'NebulaMediaSize.gifScale()' in cell, 'GIFs must read their own scale'
assert 'animatedEmojisZoom' in cell, 'the emoji scale must multiply Telegram zoom, not replace it'
assert 'messageObject.type == MessageObject.TYPE_GIF' in cell and 'nebulaGif' in cell,     'the GIF scale must apply only to GIFs, not to the shared photo and video box'
assert 'nebulaMaxWidth' in cell and 'maxWidth * nebulaSticker' in cell,     'the sticker box must be scaled in locals, leaving maxWidth for its other readers'
size = (ui / 'NebulaMediaSize.java').read_text(encoding='utf-8')
for name in ('stickerScale', 'emojiScale', 'gifScale'):
    assert name in size, name + ' must exist for the slider that sets it'
card_text = card
assert 'NebulaShape.gap(18)' in card_text, 'group headers must follow the density setting'
controls = (ui / 'NebulaFeatureControls.java').read_text(encoding='utf-8')
assert 'NebulaShape.gap(12)' in controls, 'the gap between cards must follow the density setting'
dialog = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Cells/DialogCell.java').read_text(encoding='utf-8')
assert 'NebulaChatCards.draw(canvas, this, translateY, resourcesProvider)' in dialog,     'the chat card backdrop must reach the cell that draws a chat row'
assert dialog.index('NebulaChatCards.draw') < dialog.index('boolean needInvalidate'),     'the backdrop must be drawn before the row, not over it'
cards = (ui / 'NebulaChatCards.java').read_text(encoding='utf-8')
assert 'stateLayer' in cards, 'the card must tint whatever background is there, not paint its own colour'
strip = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ScrollSlidingTextTabStrip.java').read_text(encoding='utf-8')
assert 'NebulaTabButtons.draw(canvas, tabsContainer, resourcesProvider)' in strip,     'profile tab buttons must reach the strip that draws the tabs'
assert strip.index('NebulaTabButtons.draw') < strip.index('super.dispatchDraw'),     'tab backdrops belong under the labels, not over them'
style = (ui / 'NebulaComposerStyle.java').read_text(encoding='utf-8')
assert 'NebulaComposer.fieldSurface()' in style, 'the Buttons style must reach the surfaces it removes'
preview_composer = (ui / 'NebulaComposerPreview.java').read_text(encoding='utf-8')
assert 'if (fieldSurface) surfaces[1].draw(canvas)' in preview_composer,     'the composer preview must show the missing field surface, not only the chat'
composer = (ui / 'NebulaComposer.java').read_text(encoding='utf-8')
assert 'setIosComposer(style != TELEGRAM)' in composer,     'picking Telegram must switch the layout back, not only the surfaces'
chat = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
assert chat.count('NebulaDeleteEffect.dust(LiteMode.isEnabled(LiteMode.FLAG_CHAT_THANOS))') == 2,     'both gates on the dust effect must consult the setting, or one of them still refuses'
assert 'ThanosEffect.supports()' in chat, 'the device check must stay: the effect runs on the GPU'
dialogs = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
assert dialogs.count('NebulaEdgeCompression.apply(') == 2,     'edge compression needs both hooks: scrolling moves the rows, layout paints the first frame'
edge = (ui / 'NebulaEdgeCompression.java').read_text(encoding='utf-8')
assert 'animator.isRunning()' in edge, 'edge compression must yield to the list animator, which owns row alpha while it runs'
assert 'child.setScaleX(1f)' in edge,     'turning it off must restore recycled rows, which the list reuses rather than rebuilds'
util = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java').read_text(encoding='utf-8')
assert 'NebulaFont.substitute(assetPath)' in util, 'the font choice must sit in the one funnel every typeface goes through'
assert 'nebulaClearTypefaces' in util, 'the typeface cache must be clearable, or the old font survives the change'
font = (ui / 'NebulaFont.java').read_text(encoding='utf-8')
assert 'Typeface.MONOSPACE' in font, 'monospace must stay monospace: code in messages is aligned by it'
row = (ui / 'NebulaRow.java').read_text(encoding='utf-8')
assert 'public NebulaRow tile(int resource, int color)' in row, 'section rows need a coloured tile'
assert 'icon.setColorFilter(0xFFFFFFFF' in row, 'an icon inside a coloured tile must be white, not themed'
root = (ui / 'NebulaSettingsFragment.java').read_text(encoding='utf-8')
assert root.count('NebulaRow.TILE_') >= 7, 'every top-level section must carry its own tile colour'
sheet = (ui / 'NebulaRoundCameraSheet.java').read_text(encoding='utf-8')
assert 'RadioColorCell' in sheet, 'the camera choice is a choice, so it shows Telegram radios'
assert 'key_dialogBackground' in sheet, 'the camera card must take the dialog surface'
dlg = (ui / 'NebulaDialog.java').read_text(encoding='utf-8')
assert 'RadioColorCell' in dlg, 'choice dialogs must use the cell Telegram builds its own choices from'
assert 'Theme.createSelectorDrawable' in dlg and 'key_listSelector' in dlg,     'rows must take Telegram list ripple, not a hand-rolled one'
assert 'Theme.createEditTextDrawable(context, true)' in dlg,     'a dialog text field is underlined in Telegram, not a filled box'
assert 'GradientDrawable' not in dlg, 'the Material card behind choices is what this replaced'
print('PASS: shape and the three media sizes each reach a different place in the app')
