"""Compile actual welcome/auth artwork against Android SDK, with no bitmap assets."""
from pathlib import Path
import os,re,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
sdk=Path(os.environ.get('ANDROID_HOME') or str(Path.home()/'AppData/Local/Android/Sdk'))
jars=[p for p in (sdk/'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*',p.parent.name)]
if not jars:raise SystemExit('Android SDK required')
jar=max(jars,key=lambda p:tuple(map(int,p.parent.name.removeprefix('android-').split('.'))))
stubs={
'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities{public static int dp(float n){return(int)n;}public static android.graphics.Typeface bold(){return null;}}',
'app/nebulagram/ui/NebulaTheme.java':'package app.nebulagram.ui;public class NebulaTheme{public static NebulaTheme of(android.content.Context c){return new NebulaTheme();}public int surface(){return 0;}public int onSurface(){return 0;}public int onSurfaceVariant(){return 0;}public int primary(){return 0;}}',
'app/nebulagram/ui/NebulaText.java':'package app.nebulagram.ui;public class NebulaText{public static String text(String a,String b){return a;}}',
'app/nebulagram/ui/NebulaLoginStyle.java':'package app.nebulagram.ui;public class NebulaLoginStyle{public static float vertical(){return 1;}}',
'app/nebulagram/ui/NebulaGlass.java':'package app.nebulagram.ui;public class NebulaGlass{public static boolean animations(){return true;}public static boolean reduced(){return false;}}',
'androidx/annotation/NonNull.java':'package androidx.annotation;public @interface NonNull{}'
}
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-onboarding-art-') as work:
 work=Path(work)
 for name,code in stubs.items():
  p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-cp',str(jar),'-d',str(work),str(ui/'NebulaIntroArt.java'),str(ui/'NebulaAuthArt.java'),*map(str,work.rglob('*.java'))],check=True)
intro=(ui/'NebulaIntroArt.java').read_text(encoding='utf-8')
assert 'BitmapFactory' not in intro and 'drawBitmap(' not in intro
assert 'theme.surface()' in intro and 'theme.onSurface()' in intro
art=(ui/'NebulaAuthArt.java').read_text(encoding='utf-8')
assert 'KIND_PASSWORD' in art and 'drawPassword(canvas, unit)' in art
assert 'new DashPathEffect' not in art
print('Actual welcome/auth artwork compiles against Android SDK; theme palette, vector assets and password scene are registered')
