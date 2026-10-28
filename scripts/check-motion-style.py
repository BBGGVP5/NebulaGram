"""Run the real motion curve in both styles, then check the choice reaches it."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
motion = ui / 'NebulaMenuMotion.java'

# The curve must stay free of Android and of preferences: it runs per frame and
# is compiled on its own here. A stray import would fail this compilation.
source = '''
import app.nebulagram.ui.NebulaMenuMotion;
public class MotionCheck {
 static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
 static void near(float actual, float expected, String why) {
  if (Math.abs(actual - expected) > .0001f) throw new AssertionError(why + ": " + actual + " != " + expected);
 }
 public static void main(String[] args) {
  check(NebulaMenuMotion.isExpressive(), "the spring must stay the default, as it was before the setting");
  int samples = 0;
  for (boolean expressive : new boolean[]{true, false}) {
   NebulaMenuMotion.setExpressive(expressive);
   near(NebulaMenuMotion.response(0), 0, "the start is exact in both styles");
   near(NebulaMenuMotion.response(1), 1, "the end is exact in both styles");
   float previous = 0, peak = 0;
   for (int i = 0; i <= 1000; i++) {
    float value = NebulaMenuMotion.response(i / 1000f);
    check(value >= -0.001f, "the response never runs backwards past the start");
    peak = Math.max(peak, value);
    previous = value;
    samples++;
   }
   if (expressive) {
    check(peak > 1f && peak <= 1.025f, "Expressive overshoots, but no further than 2.5%");
   } else {
    check(peak <= 1.0001f, "Standard must not overshoot at all");
   }
  }
  // Спокойная кривая обязана быть монотонной: перелёта нет, значит и
  // возвращаться неоткуда, и любое снижение было бы видно как рывок.
  NebulaMenuMotion.setExpressive(false);
  float last = -1;
  for (int i = 0; i <= 1000; i++) {
   float value = NebulaMenuMotion.response(i / 1000f);
   check(value >= last, "Standard must rise all the way to its end");
   last = value;
  }
  NebulaMenuMotion.setExpressive(true);
  System.out.println("PASS: " + samples + " motion samples across both styles");
 }
}
'''

with tempfile.TemporaryDirectory(prefix='nebula-motion-') as directory:
    work = Path(directory)
    target = work / 'MotionCheck.java'
    target.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(motion), str(target)], check=True)
    subprocess.run(['java', '-cp', str(work), 'MotionCheck'], check=True)

curve = motion.read_text(encoding='utf-8')
assert 'SharedPreferences' not in curve and 'ApplicationLoader' not in curve, \
    'the per-frame curve must not read preferences; the value is pushed into it'
store = (ui / 'NebulaMotion.java').read_text(encoding='utf-8')
assert 'NebulaMenuMotion.setExpressive(value)' in store, 'changing the setting must reach the curve at once'
assert 'NebulaMenuMotion.setExpressive(expressive())' in store, 'apply() must carry the stored choice'
loader = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java').read_text(encoding='utf-8')
assert 'NebulaMotion.apply()' in loader, 'the choice must survive a restart, so startup has to apply it'
print('PASS: the motion choice reaches the curve at startup and on change')
