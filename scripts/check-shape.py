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
  System.out.println("PASS: " + CORNERS.length + " profile photo shapes and uniform corner scaling");
 }
}
'''
source = source.replace('TABLE', table.group(0).replace('private static final', 'static final'))
source = source.replace('RADIUS', method(shape, 'public static int radius(float naturalDp)')
                        .replace('org.telegram.messenger.AndroidUtilities', 'AndroidUtilities'))
source = source.replace('AVATAR', method(shape, 'public static void avatarRadii(int side, int[] out)'))

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
print('PASS: shape settings reach profile photos, bubbles, cards, menus and the preview')
