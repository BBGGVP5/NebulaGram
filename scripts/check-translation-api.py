"""Compile production translation UI and transports against real Android/ML Kit APIs."""
from pathlib import Path
import os, re, subprocess, tempfile, urllib.request, zipfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
cache=root/'build/translation-api';cache.mkdir(parents=True,exist_ok=True)
artifacts=[('com.google.mlkit','translate','17.0.3'),('com.google.mlkit','language-id','17.0.6'),('com.google.mlkit','language-id-common','16.1.0'),('com.google.mlkit','common','18.11.0'),('com.google.android.gms','play-services-tasks','18.2.0'),('com.google.android.gms','play-services-basement','18.5.0'),('com.google.android.gms','play-services-base','18.5.0')]
jars=[]
for group,name,version in artifacts:
    target=cache/(name+'-'+version+'.jar')
    if not target.exists():
        url='https://dl.google.com/dl/android/maven2/'+group.replace('.','/')+'/'+name+'/'+version+'/'+name+'-'+version+'.aar'
        aar=cache/(name+'-'+version+'.aar');urllib.request.urlretrieve(url,aar)
        with zipfile.ZipFile(aar) as z:target.write_bytes(z.read('classes.jar'))
    jars.append(target)
sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home()/'AppData/Local/Android/Sdk'))
platforms=sorted((p for p in (sdk/'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*',p.parent.name)),key=lambda p:tuple(map(int,p.parent.name[8:].split('.'))))
assert platforms,'Android SDK required'
stubs={
 'org/telegram/messenger/AndroidUtilities.java':'''package org.telegram.messenger; public class AndroidUtilities {public static int dp(float x){return (int)x;}public static void runOnUIThread(Runnable r){}public static void runOnUIThread(Runnable r,int ms){}public static void cancelRunOnUIThread(Runnable r){} }''',
 'org/telegram/messenger/DialogObject.java':'''package org.telegram.messenger;public class DialogObject {public static boolean isEncryptedDialog(long d){return false;}}''',
 'org/telegram/ui/ActionBar/BaseFragment.java':'''package org.telegram.ui.ActionBar;public class BaseFragment {public android.app.Activity getParentActivity(){return null;}public void presentFragment(Object o){} }''',
 'org/telegram/ui/ActionBar/Theme.java':'''package org.telegram.ui.ActionBar;public class Theme {public static android.graphics.drawable.Drawable createSelectorDrawable(int c,int type){return null;}}''',
 'androidx/lifecycle/Lifecycle.java':'''package androidx.lifecycle;public class Lifecycle {public enum Event {ON_DESTROY}}''',
 'androidx/lifecycle/LifecycleObserver.java':'''package androidx.lifecycle;public interface LifecycleObserver {}''',
 'app/nebulagram/ui/NebulaTheme.java':'''package app.nebulagram.ui;public class NebulaTheme {public static NebulaTheme of(android.content.Context c){return null;}public int primary(){return 0;}public int outline(){return 0;}public int modalSurface(){return 0;}public int surfaceContainer(){return 0;}public int onSurfaceVariant(){return 0;}}''',
 'app/nebulagram/ui/NebulaTranslationSettings.java':'''package app.nebulagram.ui;public class NebulaTranslationSettings {public static android.content.SharedPreferences global(){return null;}public static android.content.SharedPreferences prefs(int a){return null;}public static boolean local(){return false;}public static boolean automatic(int a,long d){return true;}public static boolean draft(int a,long d){return true;}public static String draftLanguage(int a,long d){return "en";}public static String translationIdentity(){return "";}public static boolean translationAvailable(){return true;}public static int delay(int a,long d){return 150;}public static void choose(org.telegram.ui.ActionBar.BaseFragment h,String s,java.util.function.Consumer<String> c){} }''',
 'app/nebulagram/ui/NebulaAiClient.java':'''package app.nebulagram.ui;public class NebulaAiClient {public static final int NANO=4;public void cancel(){}public String generate(int p,String e,String k,String m,String prompt,String input)throws Exception{return input;}}''',
 'app/nebulagram/ui/NebulaAiSecrets.java':'''package app.nebulagram.ui;public class NebulaAiSecrets {public static String read(int p){return "";}}''',
 'app/nebulagram/ui/NebulaNanoAi.java':'''package app.nebulagram.ui;public class NebulaNanoAi {public static String generate(String p,String i,java.util.function.BooleanSupplier c,boolean interactive){return i;}public static String responseErrorText(Exception e){return "error";}}''',
 'app/nebulagram/ui/NebulaTranslationFragment.java':'''package app.nebulagram.ui;public class NebulaTranslationFragment {public NebulaTranslationFragment(int a,long d){} }''',
 'app/nebulagram/ui/NebulaTasks.java':'''package app.nebulagram.ui;public class NebulaTasks {public static long user(int a){return a+100;}}''',
 'app/nebulagram/ui/NebulaText.java':'''package app.nebulagram.ui;public class NebulaText {public static String text(String r,String e){return e;}}''',
 'app/nebulagram/ui/NebulaGlass.java':'''package app.nebulagram.ui;public class NebulaGlass {public static boolean reduced(){return false;}}''',
}
with tempfile.TemporaryDirectory(prefix='nebula-translation-api-') as folder:
    work=Path(folder)
    for name,source in stubs.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
    production=['NebulaLocalTranslation.java','NebulaTranslationClient.java','NebulaTranslationText.java','NebulaDraftTranslation.java','NebulaDraftOriginal.java','NebulaDraftRequestGate.java']
    subprocess.run(['javac','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[platforms[-1],*jars])),'-d',folder,*map(str,work.rglob('*.java')),*[str(ui/n) for n in production]],check=True)
print('Actual draft UI and language-pack transport compile against '+platforms[-1].parent.name+' and ML Kit Translation 17.0.3')
