"""Execute sparse account UI state and persisted theme cache from the native patch tree."""
from pathlib import Path
import os,re,shutil,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
base=tree/'TMessagesProj/src/main/java'
def source(name):return (base/name).read_text(encoding='utf-8')
def method(s,signature):
 start=s.index(signature);brace=s.index('{',start);depth=1;end=brace+1
 while depth:
  depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end]
theme=source('org/telegram/ui/ActionBar/Theme.java')
state=method(theme,'private static final class RemoteThemeState')
registry=re.search(r'private static final org.telegram.messenger.AccountInstanceRegistry<RemoteThemeState>[^;]+;',theme)[0]
get=method(theme,'private static RemoteThemeState remoteThemeState(')
save=method(theme,'private static void saveOtherThemes(boolean full, boolean migration)')
start=save.index('        for (RemoteThemeState state : remoteThemeStates.valuesSnapshot())')
save_loop=method(save[start:],'for (RemoteThemeState state : remoteThemeStates.valuesSnapshot())')
dialogs=source('org/telegram/ui/DialogsActivity.java')
flag=re.search(r'private static final java.util.Set<Integer> dialogsLoaded[^;]+;',dialogs)[0]
setter=method(dialogs,'public static void setDialogsLoaded(')
context=source('org/telegram/ui/Components/FragmentContextView.java')
remove=method(context,'private void removeObservers(')
assert not re.search(r'new .+\[UserConfig.MAX_ACCOUNT_COUNT\]',theme+dialogs+context+source('org/telegram/ui/SelectAnimatedEmojiDialog.java'))
assert 'DialogsActivity.dialogsLoaded[' not in source('org/telegram/messenger/MessagesController.java')
harness=r'''
import java.util.*;
public class AccountStateCheck {
 static class Activity {static final int MODE_PRIVATE=0;}
 static class SharedPreferences {
  final Map<String,Number> values=new HashMap<>();
  int getInt(String k,int d){return values.containsKey(k)?values.get(k).intValue():d;}
  long getLong(String k,long d){return values.containsKey(k)?values.get(k).longValue():d;}
  class Editor {Editor putInt(String k,int v){values.put(k,v);return this;}Editor putLong(String k,long v){values.put(k,v);return this;}}
  Editor edit(){return new Editor();}
 }
 static class Context {final SharedPreferences prefs=new SharedPreferences(); SharedPreferences getSharedPreferences(String n,int mode){return prefs;}}
 static class ApplicationLoader {static Context applicationContext=new Context();}
 STATE
 REGISTRY
 GET
 FLAG
 SETTER
 static void save(){SharedPreferences.Editor editor=ApplicationLoader.applicationContext.prefs.edit();SAVE}
 static class NotificationCenter {static class ObserversGroup {int removed;void removeAllObservers(){removed++;}}}
 private final HashMap<Integer,NotificationCenter.ObserversGroup> observersGroup=new HashMap<>();
 private NotificationCenter.ObserversGroup globalObserversGroup;
 REMOVE
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  SharedPreferences prefs=ApplicationLoader.applicationContext.prefs;
  prefs.values.put("remote_version",1);prefs.values.put("2remoteThemesHash",71L);
  prefs.values.put("2remoteThemesHash127",9127L);prefs.values.put("lastLoadingThemesTime127",42);
  prefs.values.put("2remoteThemesHash999",9999L);
  check(remoteThemeStates.valuesSnapshot().isEmpty());
  check(remoteThemeState(127).hash==9127L&&remoteThemeState(127).lastLoadingTime==42);
  check(remoteThemeState(0).hash==71L);
  remoteThemeState(127).loading=true;remoteThemeState(127).hash=111L;
  check(!remoteThemeState(0).loading);
  for(int id=0;id<128;id++){setDialogsLoaded(id,true);check(dialogsLoaded.contains(id));}
  setDialogsLoaded(127,false);check(!dialogsLoaded.contains(127)&&dialogsLoaded.contains(126));
  setDialogsLoaded(1000000,true);check(dialogsLoaded.contains(1000000));
  try{setDialogsLoaded(-1,true);throw new AssertionError();}catch(IllegalArgumentException expected){}
  // An unloaded account's persisted state survives unrelated account saves.
  save();check(prefs.getLong("2remoteThemesHash999",0)==9999L&&prefs.getLong("2remoteThemesHash127",0)==111L);
  // Accounts added after Theme initialization restore their own saved values.
  check(remoteThemeState(999).hash==9999L);
  AccountStateCheck instance=new AccountStateCheck();
  List<NotificationCenter.ObserversGroup> groups=new ArrayList<>();
  for(int id:new int[]{0,9,127,1000000}){NotificationCenter.ObserversGroup group=new NotificationCenter.ObserversGroup();groups.add(group);instance.observersGroup.put(id,group);}
  instance.globalObserversGroup=new NotificationCenter.ObserversGroup();groups.add(instance.globalObserversGroup);
  instance.removeObservers();instance.removeObservers();
  check(instance.observersGroup.isEmpty()&&instance.globalObserversGroup==null);
  for(NotificationCenter.ObserversGroup group:groups)check(group.removed==1);
  System.out.println("Sparse account flags, late theme restore, cache persistence and observer teardown passed");
 }
}'''
for key,value in {'STATE':state,'REGISTRY':registry,'GET':get,'FLAG':flag,'SETTER':setter,'SAVE':save_loop,'REMOVE':remove}.items():harness=harness.replace(key,value)
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-account-state-') as tmp:
 tmp=Path(tmp);path=tmp/'AccountStateCheck.java';path.write_text(harness,encoding='utf-8')
 registry_path=root/'platform/android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/AccountInstanceRegistry.java'
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(tmp),str(path),str(registry_path)],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(tmp),'AccountStateCheck'],check=True)
