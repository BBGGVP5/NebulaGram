#!/usr/bin/env python3
"""Execute Android gesture policy and check both platforms' ordered integration patches."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
source = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMessageActions.java'
with tempfile.TemporaryDirectory(prefix='nebula-gestures-') as directory:
    temp = Path(directory)
    test = temp / 'GestureTest.java'
    test.write_text('''import app.nebulagram.ui.NebulaMessageActions;
public class GestureTest {
  static void check(int want, int pref, boolean own, boolean post, boolean edit, boolean text) {
    if (NebulaMessageActions.doubleTap(pref, own, post, edit, text) != want) throw new AssertionError(pref);
  }
  public static void main(String[] args) {
    for (int pref = -1; pref <= 5; pref++) {
      check(0, pref, true, true, true, true);
      check(0, pref, false, false, true, true);
    }
    check(0, 1, true, false, false, true);
    check(1, 1, true, false, true, true);
    check(2, 2, true, false, false, false);
    check(3, 3, true, false, false, true);
    check(0, 3, true, false, false, false);
    check(4, 4, true, false, false, false);
    check(0, 0, true, false, true, true);
    System.out.println("OK: gesture policy (channels, own posts, edit/copy fallback, explicit Nothing)");
  }
}''', encoding='utf-8')
    subprocess.run([str(JAVA / 'javac'), '-d', str(temp), str(source), str(test)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(temp), 'GestureTest'], check=True)

android = (ROOT / 'patches/android/0092-chat-interaction-entry-points.patch').read_text(encoding='utf-8')
ios = (ROOT / 'patches/ios/0016-chat-interaction-entry-points.patch').read_text(encoding='utf-8')
assert android.count('+') > 5 and 'nebulaDoubleTapAction' in android
assert '+            if (!available)' in android
assert '+                    createMenu(view, false, false, x, y, false);' in android
assert 'richEditorAvailable()' in android and '!((ChatActivity) baseFragment).isSecretChat()' in android
assert '+        let isExpandInputEnabled = self.enableRichTextInput' in ios
assert '+                        itemNode.openMessageContextMenu()' in ios
print('OK: native editor and reaction fallback patch guards')
