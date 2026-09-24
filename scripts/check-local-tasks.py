"""Exercise real task persistence and alarm identities with a small Android model."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
work = root / 'build/local-task-check'
work.mkdir(parents=True, exist_ok=True)
jar = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'), root / 'build/ai-protocol-check/json-20240303.jar')
stubs = {
    'android/os/Build.java': '''package android.os; public class Build {public static class VERSION {public static int SDK_INT=35;}}''',
    'android/content/Context.java': '''package android.content;
public class Context {
 public static final String ALARM_SERVICE="alarm";
 public java.io.File directory;
 public final android.app.AlarmManager alarms=new android.app.AlarmManager();
 public java.io.File getNoBackupFilesDir(){return directory;}
 public Object getSystemService(String key){return alarms;}
}''',
    'android/net/Uri.java': '''package android.net;
public class Uri {public final String value; private Uri(String v){value=v;} public static Uri parse(String v){return new Uri(v);}}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {public String data; public Intent(Context c,Class<?> type){} public Intent setData(android.net.Uri uri){data=uri.value;return this;} public Intent putExtra(String k,long v){return this;} public Intent putExtra(String k,String v){return this;}}''',
    'android/app/PendingIntent.java': '''package android.app;
public class PendingIntent {public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2; public String key;
 public static PendingIntent getBroadcast(android.content.Context c,int code,android.content.Intent i,int flags){PendingIntent p=new PendingIntent();p.key=i.data;return p;}}''',
    'android/app/AlarmManager.java': '''package android.app;
public class AlarmManager {public static final int RTC_WAKEUP=0; public boolean idle; public final java.util.Map<String,Long> scheduled=new java.util.HashMap<>();
 public void cancel(PendingIntent p){scheduled.remove(p.key);} public void setAndAllowWhileIdle(int type,long at,PendingIntent p){idle=true;scheduled.put(p.key,at);} public void set(int type,long at,PendingIntent p){idle=false;scheduled.put(p.key,at);}}''',
    'android/util/AtomicFile.java': '''package android.util;
public class AtomicFile {private final java.io.File file; public AtomicFile(java.io.File f){file=f;}
 public java.io.File getBaseFile(){return file;} public byte[] readFully()throws Exception{return java.nio.file.Files.readAllBytes(file.toPath());}
 public java.io.FileOutputStream startWrite()throws Exception{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream s)throws Exception{s.close();} public void failWrite(java.io.FileOutputStream s)throws Exception{s.close();}}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger; public class ApplicationLoader {public static final android.content.Context applicationContext=new android.content.Context();}''',
    'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig {private int account; public static UserConfig getInstance(int a){UserConfig c=new UserConfig();c.account=a;return c;} public long getClientUserId(){return 100+account;}}''',
    'app/nebulagram/ui/NebulaTaskReminder.java': '''package app.nebulagram.ui; public class NebulaTaskReminder {}''',
}
sources = []
for name, source in stubs.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source, encoding='utf-8')
    sources.append(str(path))
sources += [str(root / 'tests/android/TaskStoreCheck.java'), str(root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaTasks.java')]
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), *sources], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'TaskStoreCheck'], check=True)
