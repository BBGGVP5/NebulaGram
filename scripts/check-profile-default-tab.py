"""Default profile tab: run NebulaProfileTabs.choose on real profiles, then check the setting row and the 0374 hook."""
from pathlib import Path
import os, re, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

stubs = {
'app/nebulagram/ui/NebulaText.java': 'package app.nebulagram.ui; public final class NebulaText {public static String text(String ru,String en){return en;}}',
'app/nebulagram/ui/NebulaMessagePreferences.java': '''package app.nebulagram.ui; public final class NebulaMessagePreferences {public static int stored;
 public static int number(String k,int f,int min,int max){if(!"profile_default_tab".equals(k))throw new AssertionError(k);return Math.max(min,Math.min(max,stored));}
 public static void set(String k,int v){stored=v;}}''',
'Check.java': r'''import app.nebulagram.ui.*;
public class Check {
 static int cases;
 static void is(int got,int want,String m){if(got!=want)throw new AssertionError(m+": "+got+" != "+want);cases++;}
 static final int PROFILE=1,MEDIA_ACTIVITY=0;
 public static void main(String[] a) throws Exception {
  java.lang.reflect.Method choose=NebulaProfileTabs.class.getDeclaredMethod("choose",int.class,int.class,int.class,int.class,long.class,int[].class,boolean.class,boolean.class);
  choose.setAccessible(true);
  int[] files={0,12,0,3,0,0,0,0,0};       // files and links, no photos
  int[] loading={-1,-1,-1,-1,-1,-1,0,-1,-1}; // counts not loaded yet
  int[] photos={40,0,0,0,0,0,0,0,0};
  Object[][] rows={
   // mode, computed, requested, viewType, topic, media, stories, gifts, expected, why
   {0,8,-1,PROFILE,0L,photos,true,false,8,"Auto keeps Telegram's posts tab"},
   {1,8,-1,PROFILE,0L,photos,true,false,0,"Media over posts"},
   {1,1,-1,PROFILE,0L,files,false,false,1,"Media chosen but none: Telegram's files tab stays"},
   {1,8,-1,PROFILE,0L,loading,true,false,0,"unknown count counts as present, as in Telegram"},
   {2,0,-1,PROFILE,0L,photos,true,false,8,"Posts"},
   {2,0,-1,PROFILE,0L,photos,false,false,0,"Posts chosen, profile has none"},
   {3,0,-1,PROFILE,0L,photos,false,true,14,"Gifts"},
   {3,0,-1,PROFILE,0L,photos,false,false,0,"Gifts chosen, none"},
   {4,0,-1,PROFILE,0L,files,false,false,1,"Files"},
   {5,1,-1,PROFILE,0L,files,false,false,3,"Links"},
   {6,1,-1,PROFILE,0L,files,false,false,1,"Music chosen, none"},
   {7,0,-1,PROFILE,0L,loading,false,false,2,"Voice while loading"},
   {8,0,-1,PROFILE,0L,photos,false,false,0,"GIF chosen, none"},
   {1,14,14,PROFILE,0L,photos,false,true,14,"the gifts button keeps the gifts tab"},
   {1,6,6,PROFILE,0L,photos,false,false,6,"common groups request kept"},
   {1,10,10,PROFILE,0L,photos,false,false,10,"similar channels request kept"},
   {1,11,11,PROFILE,0L,photos,false,false,11,"saved dialogs request kept"},
   {1,8,-1,MEDIA_ACTIVITY,0L,photos,true,false,8,"shared media screen is untouched"},
   {1,8,-1,PROFILE,7L,photos,true,false,8,"forum topic is untouched"},
   {9,8,-1,PROFILE,0L,photos,true,false,8,"out-of-range mode is Auto"},
   {-1,8,-1,PROFILE,0L,photos,true,false,8,"negative mode is Auto"},
   {4,0,-1,PROFILE,0L,null,false,false,0,"no counts"},
  };
  for(Object[] r:rows)is((Integer)choose.invoke(null,r[0],r[1],r[2],r[3],r[4],r[5],r[6],r[7]),(Integer)r[8],(String)r[9]);
  // The stored mode drives initial(); Auto is the default.
  NebulaMessagePreferences.stored=0;is(NebulaProfileTabs.initial(8,-1,PROFILE,0L,photos,true,false),8,"default Auto");
  NebulaProfileTabs.setMode(1);is(NebulaProfileTabs.initial(8,-1,PROFILE,0L,photos,true,false),0,"stored Media");
  NebulaProfileTabs.setMode(99);is(NebulaProfileTabs.mode(),8,"setMode clamps");
  is(NebulaProfileTabs.titles().length,9,"one title per choice");
  System.out.println("Profile default tab: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-profile-tab-') as work:
    work = Path(work)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), *map(str, work.rglob('*.java')), str(ui / 'NebulaProfileTabs.java')], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

tabs = (ui / 'NebulaProfileTabs.java').read_text(encoding='utf-8')
assert 'NebulaText.text("Вкладка' not in tabs  # the row title lives in the settings screen
for ru, en in [('Автоматически', 'Automatic'), ('Медиа', 'Media'), ('Публикации', 'Posts'), ('Подарки', 'Gifts'), ('Голосовые', 'Voice')]:
    assert f'NebulaText.text("{ru}", "{en}")' in tabs, ru
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'NebulaText.text("Вкладка по умолчанию", "Default tab")' in section and 'NebulaProfileTabs.setMode(which)' in section
assert section.index('NebulaGiftOrbit.KEY') < section.index('NebulaProfileTabs.title()') < section.index('NebulaText.text("Информация", "Info")')
assert '0374-profile-default-tab.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    layout = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java').read_text(encoding='utf-8')
    hook = layout.index('this.initialTab = app.nebulagram.ui.NebulaProfileTabs.initial(this.initialTab, initialTab, viewType, topicId, hasMedia,')
    # After Telegram's whole choice, before the strip reads it.
    assert layout.index('this.initialTab = getInitialTab();') < hook < layout.index('onTabProgress(initialTab);', hook)
    assert re.search(r'TAB_PHOTOVIDEO = 0;[\s\S]*TAB_FILES = 1;[\s\S]*TAB_VOICE = 2;[\s\S]*TAB_LINKS = 3;[\s\S]*TAB_AUDIO = 4;[\s\S]*TAB_GIF = 5;[\s\S]*TAB_COMMON_GROUPS = 6;', layout)
    assert 'TAB_STORIES = 8;' in layout and 'TAB_RECOMMENDED_CHANNELS = 10;' in layout and 'TAB_SAVED_DIALOGS = 11;' in layout and 'TAB_GIFTS = 14;' in layout
    assert 'VIEW_TYPE_PROFILE_ACTIVITY = 1;' in layout
    assert 'private void checkCurrentTabValid()' in layout  # a missing tab falls back to the first one
print('Profile default tab: settings row, tab ids and 0374 hook verified')
