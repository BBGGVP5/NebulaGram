"""Execute the real provider before Application.onCreate, then sparse-account lifecycle.

Android framework fixtures model the main Looper. Catalogue/persistence fixtures are
shared with check-account-catalog; provider and account lifecycle code are production.
"""
from pathlib import Path
import ast
import os
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
production = root / 'platform/android/overlay/TMessagesProj/src/main/java/org/telegram/messenger'
module = ast.parse((root / 'scripts/check-account-catalog.py').read_text(encoding='utf-8'))
fixtures = next(ast.literal_eval(node.value) for node in module.body
                if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'files' for t in node.targets))
files = {name: value for name, value in fixtures.items() if name.startswith('android/')}
files.update({
    'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
    'android/os/Handler.java': '''package android.os; public class Handler {
      public static final java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();public static boolean executing;
      public Handler(Looper looper){}public boolean post(Runnable r){queue.add(r);return true;}
      public void removeCallbacks(Runnable r){queue.removeIf(item->item==r);}
      public static void drain(){executing=true;try{while(!queue.isEmpty())queue.remove().run();}finally{executing=false;}}
    }''',
    'android/content/ContentProvider.java': '''package android.content; public abstract class ContentProvider {
      public abstract boolean onCreate();public void shutdown(){}
      public abstract android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o);
      public abstract String getType(android.net.Uri u);public abstract android.net.Uri insert(android.net.Uri u,ContentValues v);
      public abstract int delete(android.net.Uri u,String s,String[] a);public abstract int update(android.net.Uri u,ContentValues v,String s,String[] a);
      public String[] getStreamTypes(android.net.Uri u,String f){return null;}
      public android.os.ParcelFileDescriptor openFile(android.net.Uri u,String m)throws java.io.FileNotFoundException{return null;}
    }''',
    'android/content/ContentValues.java': 'package android.content;public class ContentValues {}',
    'android/content/UriMatcher.java': '''package android.content;public class UriMatcher {public static final int NO_MATCH=-1;public UriMatcher(int i){}public void addURI(String a,String p,int c){}public int match(android.net.Uri u){return -1;}}''',
    'android/database/Cursor.java': 'package android.database;public interface Cursor {}',
    'android/net/Uri.java': '''package android.net;public class Uri {public static Uri fromFile(java.io.File f){return new Uri();}public java.util.List<String> getPathSegments(){return java.util.Collections.emptyList();}public String getQueryParameter(String s){return null;}}''',
    'android/os/ParcelFileDescriptor.java': '''package android.os;public class ParcelFileDescriptor {public static final int MODE_READ_ONLY=1;public static ParcelFileDescriptor open(java.io.File f,int m){return new ParcelFileDescriptor();}}''',
    'android/text/TextUtils.java': 'package android.text;public class TextUtils {public static boolean isEmpty(String s){return s==null||s.isEmpty();}}',
    'androidx/annotation/NonNull.java': 'package androidx.annotation;public @interface NonNull {}',
    'androidx/annotation/Nullable.java': 'package androidx.annotation;public @interface Nullable {}',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;public class ApplicationLoader {
      public static android.content.Context applicationContext;public static java.io.File files;
      public static java.io.File getFilesDirFixed(){return files;}public static String getApplicationId(){return "app.nebulagram.messenger";}public static void postInitApplication(){}
    }''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;public class AndroidUtilities {
      public static void runOnUIThread(Runnable r){new android.os.Handler(android.os.Looper.getMainLooper()).post(r);}
      public static void runOnUIThread(Runnable r,long d){runOnUIThread(r);}public static boolean isInternalUri(android.net.Uri u){return false;}
    }''',
    # The old provider calls this before applicationContext exists, reproducing 1000568.
    'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig {public static int getActivatedAccountsCount(){return AccountDirectory.ids().length;}}''',
    'org/telegram/messenger/BuildVars.java': 'package org.telegram.messenger;public class BuildVars {public static final boolean LOGS_ENABLED=false;}',
    'org/telegram/messenger/FileLog.java': 'package org.telegram.messenger;public class FileLog {public static void w(String s){}}',
    'org/telegram/messenger/NotificationCenter.java': '''package org.telegram.messenger;import java.util.*;public class NotificationCenter {
      public interface NotificationCenterDelegate {void didReceivedNotification(int id,int account,Object... args);}
      public static final int fileLoaded=1;public static final Map<Integer,Set<NotificationCenterDelegate>> observers=new TreeMap<>();
      final int account;NotificationCenter(int a){account=a;}
      public static NotificationCenter getInstance(int a){if(ApplicationLoader.applicationContext==null)throw new AssertionError("before Application.onCreate");if(!android.os.Handler.executing)throw new AssertionError("not on main Looper");return new NotificationCenter(a);}
      public void addObserver(NotificationCenterDelegate d,int event){if(!observers.computeIfAbsent(account,k->new HashSet<>()).add(d))throw new AssertionError("duplicate observer");}
      public void removeObserver(NotificationCenterDelegate d,int event){Set<NotificationCenterDelegate> set=observers.get(account);if(set==null||!set.remove(d))throw new AssertionError("unbound observer");if(set.isEmpty())observers.remove(account);}
    }''',
    'org/telegram/messenger/ProviderStartupCheck.java': r'''
package org.telegram.messenger;
import java.nio.file.*;import java.util.*;import android.os.Handler;
public class ProviderStartupCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void accounts(Integer... ids){check(NotificationCenter.observers.keySet().equals(new TreeSet<>(Arrays.asList(ids))));}
 public static void main(String[] args)throws Exception {
  // Framework creates ContentProviders before Application.onCreate.
  check(ApplicationLoader.applicationContext==null);
  NotificationImageProvider cancelled=new NotificationImageProvider();check(cancelled.onCreate());cancelled.shutdown();cancelled.shutdown();
  NotificationImageProvider provider=new NotificationImageProvider();check(provider.onCreate());accounts();
  Path temporary=Files.createTempDirectory("nebula-provider-startup-");
  try {
   Path prefs=Files.createDirectory(temporary.resolve("shared_prefs"));
   Files.write(prefs.resolve("userconfing.xml"),new byte[0]);Files.write(prefs.resolve("userconfig9.xml"),new byte[0]);
   Path nativeFiles=Files.createDirectory(temporary.resolve("files"));Files.createDirectory(nativeFiles.resolve("account5"));
   ApplicationLoader.files=nativeFiles.toFile();ApplicationLoader.applicationContext=new android.content.Context();ApplicationLoader.applicationContext.info.dataDir=temporary.toString();
   check(ApplicationLoader.applicationContext.values.isEmpty());
   Handler.drain();accounts(0,5,9);for(Set<?> observers:NotificationCenter.observers.values())check(observers.size()==1);
   check(Arrays.equals(AccountDirectory.ids(),new int[]{0,5,9}));
   check(AccountDirectory.allocate()==10);Handler.drain();accounts(0,5,9,10);
   AccountDirectory.retire(5);Handler.drain();accounts(0,9,10);
   check(AccountDirectory.allocate()==11);provider.shutdown();provider.shutdown();Handler.drain();accounts();
   // A queued callback cannot resurrect a provider closed before registration.
   NotificationImageProvider closed=new NotificationImageProvider();check(closed.onCreate());closed.shutdown();Handler.drain();accounts();
   AccountDirectory.allocate();Handler.drain();accounts();
   NotificationImageProvider reopened=new NotificationImageProvider();check(reopened.onCreate());Handler.drain();accounts(0,9,10,11,12);
   reopened.shutdown();Handler.drain();accounts();
  } finally {try(java.util.stream.Stream<Path> paths=Files.walk(temporary)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(Exception e){throw new RuntimeException(e);}});}}
  System.out.println("Provider: pre-Application startup, sparse migration, late accounts, logout, cancelled registration and UI-thread shutdown passed");
 }
}
''',
})
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-provider-') as directory:
    work = Path(directory)
    for name, content in files.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    for name in ['AccountCatalog.java', 'AccountDirectory.java']:
        shutil.copy2(production / name, work / 'org/telegram/messenger' / name)
    shutil.copy2(tree / 'TMessagesProj/src/main/java/org/telegram/messenger/NotificationImageProvider.java', work / 'org/telegram/messenger/NotificationImageProvider.java')
    subprocess.run([shutil.which('javac') or str(java_home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([shutil.which('java') or str(java_home / 'bin/java.exe'), '-cp', str(work), 'org.telegram.messenger.ProviderStartupCheck'], check=True, timeout=30)
