"""Run the persistent account allocator and Android subscription lifecycle, without sessions."""
from pathlib import Path
import os,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
production=root/'platform/android/overlay/TMessagesProj/src/main/java/org/telegram/messenger'
files={
'android/content/pm/ApplicationInfo.java':'package android.content.pm; public class ApplicationInfo {public String dataDir;}',
'android/content/SharedPreferences.java':'''package android.content; public interface SharedPreferences {boolean contains(String k);String getString(String k,String d);Editor edit();interface Editor{Editor putString(String k,String v);boolean commit();}}''',
'android/content/Context.java':'''package android.content; import java.util.*; public class Context {
 public static final int MODE_PRIVATE=0;public final Map<String,String> values=new HashMap<>();
 public android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();
 public android.content.pm.ApplicationInfo getApplicationInfo(){return info;}
 public SharedPreferences getSharedPreferences(String name,int mode){return new SharedPreferences(){
  public boolean contains(String k){return values.containsKey(name+":"+k);}public String getString(String k,String d){return values.getOrDefault(name+":"+k,d);}
  public Editor edit(){return new Editor(){String key,value;public Editor putString(String k,String v){key=k;value=v;return this;}public boolean commit(){values.put(name+":"+key,value);return true;}};}
 };}}
''',
'org/telegram/messenger/ApplicationLoader.java':'''package org.telegram.messenger;public class ApplicationLoader {public static android.content.Context applicationContext;public static java.io.File files;public static java.io.File getFilesDirFixed(){return files;}}''',
'org/telegram/messenger/AndroidUtilities.java':'''package org.telegram.messenger; public class AndroidUtilities {public static final java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();public static void runOnUIThread(Runnable r){queue.add(r);} public static void runOnUIThread(Runnable r,long delay){queue.add(r);} public static void drain(){while(!queue.isEmpty())queue.remove().run();}}''',
'org/telegram/messenger/AccountCatalogCheck.java':r'''
package org.telegram.messenger;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
public class AccountCatalogCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Memory implements AccountCatalog.Storage {String value;boolean fail;public String read(){return value;}public boolean write(String s){if(fail)return false;value=s;return true;}}
 public static void main(String[] args)throws Exception {
  Memory storage=new Memory();AccountCatalog catalog=new AccountCatalog(storage,new int[]{0,2,9});
  check(Arrays.equals(catalog.snapshot(),new int[]{0,2,9}));check(catalog.allocate()==10);
  catalog.retire(9);catalog.retire(10);catalog=new AccountCatalog(storage,new int[]{0});check(catalog.allocate()==11);
  int[] leaked=catalog.snapshot();leaked[0]=999;check(catalog.contains(0)&&!catalog.contains(999));
  String previous=storage.value;storage.fail=true;
  try{catalog.allocate();throw new AssertionError();}catch(IllegalStateException expected){}
  check(previous.equals(storage.value)&&!catalog.contains(12));
  try{catalog.retire(2);throw new AssertionError();}catch(IllegalStateException expected){}
  check(catalog.contains(2));storage.fail=false;check(catalog.allocate()==12);
  final AccountCatalog concurrent=catalog;ExecutorService workers=Executors.newFixedThreadPool(8);Set<Integer> identities=ConcurrentHashMap.newKeySet();List<Future<?>> jobs=new ArrayList<>();
  for(int t=0;t<8;t++)jobs.add(workers.submit(()->{for(int i=0;i<32;i++)check(identities.add(concurrent.allocate()));}));
  for(Future<?> job:jobs)job.get();workers.shutdown();check(identities.size()==256);
  AccountCatalog restarted=new AccountCatalog(storage,new int[0]);check(restarted.snapshot().length==catalog.snapshot().length);
  for(String broken:new String[]{"", "1\n1\n1","1\n3\n0,0","2\n3\n0","1\n-1\n","1\n3\n-1","1\n3\nx"}){
   Memory invalid=new Memory();invalid.value=broken;
   try{new AccountCatalog(invalid,new int[]{0});throw new AssertionError(broken);}catch(IllegalStateException expected){}
  }
  Memory edge=new Memory();edge.value="1\n2147483647\n0";AccountCatalog last=new AccountCatalog(edge,new int[0]);check(last.allocate()==Integer.MAX_VALUE&&!last.canAllocate());
  try{last.allocate();throw new AssertionError();}catch(IllegalStateException expected){}
  Path temporary=Files.createTempDirectory("nebula-account-migration-");
  try {
   Path prefs=Files.createDirectories(temporary.resolve("shared_prefs"));
   for(String name:new String[]{"userconfing.xml","userconfig2.xml","logininfo2_9.xml","userconfig2147483648.xml","unrelated.xml"})Files.write(prefs.resolve(name),new byte[0]);
   Path nativeFiles=Files.createDirectory(temporary.resolve("files"));Files.createDirectory(nativeFiles.resolve("account5"));
   ApplicationLoader.files=nativeFiles.toFile();ApplicationLoader.applicationContext=new android.content.Context();ApplicationLoader.applicationContext.info.dataDir=temporary.toString();
   check(Arrays.equals(AccountDirectory.ids(),new int[]{0,2,5,9}));
   List<Integer> added=new ArrayList<>(),removed=new ArrayList<>();
   AccountDirectory.Subscription subscription=AccountDirectory.observe(added::add,removed::add);
   check(added.equals(Arrays.asList(0,2,5,9)));int id=AccountDirectory.allocate();check(id==10);
   // A new observer created before a queued addition receives that ID once.
   List<Integer> late=new ArrayList<>();AccountDirectory.Subscription lateSubscription=AccountDirectory.observe(late::add,null);
   AndroidUtilities.drain();check(Collections.frequency(added,10)==1&&Collections.frequency(late,10)==1);
   AccountDirectory.retire(10);AndroidUtilities.drain();check(removed.equals(Arrays.asList(10)));
   int next=AccountDirectory.allocate();check(next==11);subscription.close();subscription.close();AndroidUtilities.drain();
   check(!added.contains(11)&&removed.size()==5);lateSubscription.close();
   AccountDirectory.retire(11);AndroidUtilities.drain();check(AccountDirectory.allocate()==12);
  } finally {try(java.util.stream.Stream<Path> paths=Files.walk(temporary)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(Exception e){throw new RuntimeException(e);}});}}
  System.out.println("Persistent IDs: 256 concurrent allocations, migration/restart, retired-ID isolation, failed commits, corrupt metadata and late/closed observers passed");
 }
}
'''}
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-catalog-') as work:
 work=Path(work)
 for name,text in files.items():
  p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
 for name in ['AccountCatalog.java','AccountDirectory.java']:shutil.copy2(production/name,work/'org/telegram/messenger'/name)
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'--release','8','-encoding','UTF-8','-d',str(work),*map(str,work.rglob('*.java'))],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'org.telegram.messenger.AccountCatalogCheck'],check=True,timeout=60)

if len(__import__('sys').argv) > 1:
 native=Path(__import__('sys').argv[1])/'TMessagesProj/src/main/java'
 for path in Path(__import__('sys').argv[1]).rglob('*.java'):
  text=path.read_text(encoding='utf-8')
  assert 'MAX_ACCOUNT_COUNT' not in text, path
 login=(native/'org/telegram/ui/LoginActivity.java').read_text(encoding='utf-8')
 assert 'currentAccount = UserConfig.allocateAccount()' in login
 logout=(native/'org/telegram/messenger/MessagesController.java').read_text(encoding='utf-8')
 logout=logout[logout.index('public void performLogout('):logout.index('public void registerForPush(')]
 assert logout.index('AccountDirectory.retire(currentAccount)') < logout.index('postNotificationName(NotificationCenter.appDidLogout)')
 for name in ['ImageLoader','MediaController','ProxyRotationController','ImportingService','MusicPlayerService']:
  assert 'AccountDirectory.observe(' in (native/('org/telegram/messenger/'+name+'.java')).read_text(encoding='utf-8'),name
 print('Native account enumeration, login allocation, logout order and process subscriptions verified')
