"""Execute prompt timing rules and guard sheet lifecycle/installer integration."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
with tempfile.TemporaryDirectory(prefix='nebula-update-sheet-') as temp:
    work = Path(temp)
    test = work / 'Check.java'
    test.write_text('''import app.nebulagram.ui.NebulaUpdatePromptPolicy;
public class Check {
 static void check(boolean actual, boolean expected) { if(actual!=expected) throw new AssertionError(); }
 public static void main(String[] args) {
  long now=1700000000000L, day=NebulaUpdatePromptPolicy.DAY;
  check(NebulaUpdatePromptPolicy.shouldOffer(0,0,0,now),false);
  check(NebulaUpdatePromptPolicy.shouldOffer(10,9,now,now),true);
  check(NebulaUpdatePromptPolicy.shouldOffer(10,10,0,now),true);
  check(NebulaUpdatePromptPolicy.shouldOffer(10,10,now,now),false);
  check(NebulaUpdatePromptPolicy.shouldOffer(10,10,now,now+day-1),false);
  check(NebulaUpdatePromptPolicy.shouldOffer(10,10,now,now+day),true);
  check(NebulaUpdatePromptPolicy.shouldOffer(10,10,now,now-1),true);
  System.out.println("PASS: update prompt new release, snooze boundary, migration and clock rollback");
 }
}''', encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(UI/'NebulaUpdatePromptPolicy.java'),str(test)],check=True)
    subprocess.run(['java','-cp',str(work),'Check'],check=True)
sheet = (UI/'NebulaUpdateSheet.java').read_text(encoding='utf-8')
updater = (UI/'NebulaTelegramUpdates.java').read_text(encoding='utf-8')
mascot = (UI/'NebulaUpdateMascot.java').read_text(encoding='utf-8')
for required in ['extends BottomSheet', 'new NebulaChangelogView', 'updates.addListener(refresh)',
                 'updates.removeListener(refresh)', 'updates.install(activity)', 'updates.cancelDownload()',
                 'updates.redownload()', 'offeredDocument', 'updates.currentAccountSelected()',
                 'shouldOffer', 'new NebulaUpdateSheet']:
    assert required in sheet + updater, required
assert 'NebulaApkVerifier.prepare' in updater
assert 'new NebulaAnimatedEmoji' in mascot and '"🚀", 88' in mascot
assert 'nebula_launcher_nova_monochrome' not in mascot
assert 'footer.measure(width' in sheet and 'compact?limit:remaining' in sheet
assert 'content.addView(footer' in sheet and 'content.removeView(footer)' in sheet
print('PASS: updater/sheet source guards (not device UI verification)')

settings = (UI/'NebulaUpdateSettingsSheet.java').read_text(encoding='utf-8')
for source in (sheet, settings):
    assert 'import androidx.core.widget.NestedScrollView;' in source
    assert 'new NestedScrollView(activity)' in source
    assert 'scroll.setNestedScrollingEnabled(true)' in source
    # Telegram BottomSheet accepts nested scrolling only with direct swipe disabled.
    assert 'setCanDismissWithSwipe(false)' in source
    assert 'android.widget.ScrollView' not in source
print('PASS: both update sheets participate in native nested dragging/dismissal')
