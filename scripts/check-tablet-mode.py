"""Tablet layout in wide windows: run NebulaTabletMode.decide on real window sizes, then check the setting and the 0377 hook."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
source = (ui / 'NebulaTabletMode.java').read_text(encoding='utf-8')
start = source.index('    static boolean decide(')
decide = source[start:source.index('    }\n', start) + 6]
constants = '\n'.join(line for line in source.splitlines() if line.strip().startswith(('public static final int AUTO', 'static final int WIDE_WIDTH_DP', 'static final int WIDE_SMALLEST_DP')))

stubs = {
'Rules.java': 'public class Rules {\n' + constants + '\n' + decide + '\n}',
'Check.java': r'''public class Check {
 static int cases;
 static void is(boolean got,boolean want,String m){if(got!=want)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  int AUTO=Rules.AUTO,WIDE=Rules.WIDE;
  // As in Telegram: only the device class decides.
  is(Rules.decide(AUTO,false,900,600),false,"auto: unfolded phone stays a phone");
  is(Rules.decide(AUTO,true,600,600),true,"auto: tablet stays a tablet");
  // In wide windows.
  is(Rules.decide(WIDE,false,882,673),true,"unfolded foldable (Fold 5 inner)");
  is(Rules.decide(WIDE,false,1280,720),true,"DeX window");
  is(Rules.decide(WIDE,false,900,500),true,"wide free-form window");
  is(Rules.decide(WIDE,false,860,392),false,"phone turned sideways");
  is(Rules.decide(WIDE,false,412,915),false,"phone upright");
  is(Rules.decide(WIDE,false,600,540),false,"narrower than both panes");
  is(Rules.decide(WIDE,false,640,480),true,"exactly the minimum");
  is(Rules.decide(WIDE,true,400,400),true,"a tablet in a small window keeps Telegram's choice");
  System.out.println("Tablet mode: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-tablet-') as work:
    work = Path(work)
    for name, text in stubs.items():
        (work / name).write_bytes(text.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

assert 'prefs().getInt(KEY, AUTO)' in source and 'KEY = "tablet_mode"' in source
assert 'AndroidUtilities.resetTabletFlag();' in source  # a new choice is re-read like a configuration change
assert 'AndroidUtilities.displaySize.x' in source  # the window, not the whole display
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'NebulaText.text("Раскладка планшета", "Tablet layout")' in section and 'NebulaTabletMode.setMode(which);' in section
assert '0377-tablet-layout-in-wide-windows.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram'
    utils = (tree / 'messenger/AndroidUtilities.java').read_text(encoding='utf-8')
    assert 'return app.nebulagram.ui.NebulaTabletMode.tablet(ApplicationLoader.applicationContext != null && ApplicationLoader.applicationContext.getResources().getBoolean(R.bool.isTablet));' in utils
    launch = (tree / 'ui/LaunchActivity.java').read_text(encoding='utf-8')
    # Telegram itself moves the open chats between panes when the flag flips.
    config = launch[launch.index('public void onConfigurationChanged(Configuration newConfig) {'):]
    assert config.index('AndroidUtilities.resetTabletFlag();') < config.index('invalidateTabletMode();')
    assert 'private void invalidateTabletMode() {' in launch
print('Tablet mode: setting and 0377 hook verified')
