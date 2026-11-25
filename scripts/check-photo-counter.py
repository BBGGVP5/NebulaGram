"""Glass photo counter: run the visibility and animation rules of NebulaPhotoCounter, then check the setting and the 0376 hooks."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
source = (ui / 'NebulaPhotoCounter.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    depth, i = 0, source.index('{', start)
    while True:
        if source[i] == '{': depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0: return source[start:i + 1]
        i += 1

rules = method('public static boolean visible(boolean enabled,') + '\n' + method('static boolean animate(')
stubs = {
'Rules.java': 'public class Rules {\n' + rules + '\n}',
'Check.java': r'''public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  // Off: exactly Telegram's rule (pulled down and more than 20 photos).
  check(Rules.visible(false,true,25,true)&&!Rules.visible(false,true,5,false)&&!Rules.visible(false,false,25,false),"off follows Telegram");
  // On: every expanded gallery of two or more.
  check(Rules.visible(true,true,2,false)&&Rules.visible(true,true,40,true),"on shows from two photos");
  check(!Rules.visible(true,true,1,false)&&!Rules.visible(true,false,9,false),"single photo or collapsed header: hidden");
  // Digits roll only on a real change of a visible counter.
  check(Rules.animate("3/12","4/12",true),"page change rolls");
  check(!Rules.animate("3/12","3/12",true)&&!Rules.animate(null,"1/2",true)&&!Rules.animate("3/12","4/12",false),"first text, same text or hidden: no roll");
  System.out.println("Photo counter: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-photo-counter-') as work:
    work = Path(work)
    for name, text in stubs.items():
        (work / name).write_bytes(text.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

assert 'NebulaMessagePreferences.enabled(KEY, false)' in source and 'KEY = "profile_photo_counter"' in source
# The text drawable is not one of the host's own drawables, so it invalidates the host itself.
assert '@Override public void invalidateDrawable(Drawable who) { host.invalidate(); }' in source
assert 'new AnimatedTextView.AnimatedTextDrawable(false, true, false)' in source
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'preference(context, R.drawable.msg_photos, NebulaPhotoCounter.KEY, false,' in section and '"Стеклянный счётчик фото", "Glass photo counter"' in section
assert '0376-profile-glass-photo-counter.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
    profile = (tree / 'ProfileActivity.java').read_text(encoding='utf-8')
    indicator = profile[profile.index('private class PagerIndicatorView extends View {'):profile.index('private ActionBarMenuItem getSecondaryMenuItem()')]
    for hook in ['new app.nebulagram.ui.NebulaPhotoCounter(this);',
                 'app.nebulagram.ui.NebulaPhotoCounter.visible(isPulledDown, avatarsViewPager.getRealCount(), isPulledDown && avatarsViewPager.getRealCount() > 20)',
                 'if (nebulaGlass) nebulaCounter.setText(getCurrentTitle(), isIndicatorVisible);',
                 'nebulaGlass ? nebulaCounter.width(textWidth) : textWidth + dpf2(16f)',
                 'nebulaCounter.draw(canvas, indicatorRect);']:
        assert hook in indicator, hook
    assert indicator.index('nebulaCounter.draw(canvas, indicatorRect);') < indicator.index('canvas.drawRoundRect(indicatorRect, radius, radius, backgroundPaint);')
    gallery = (tree / 'Components/ProfileGalleryView.java').read_text(encoding='utf-8')
    assert 'return (getRealPosition(position) + 1) + "/" + getRealCount();' in gallery
print('Photo counter: setting and 0376 hooks verified')
