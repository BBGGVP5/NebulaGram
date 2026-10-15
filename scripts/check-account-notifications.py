"""Exercise full notification identities and compile the real helper against Android SDK."""
from pathlib import Path
import os,re,shutil,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
helper=root/'platform/android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/AccountNotificationIdentity.java'
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
native=(tree/'TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java').read_text(encoding='utf-8')
assert not re.search(r'notificationManager\.(?:notify|cancel)\((?:notificationId|id|wearNotificationsIds\.valueAt\(a\))[,)]',native)
for call in re.findall(r'PendingIntent\.get(?:Activity|Broadcast|Service)\(ApplicationLoader.applicationContext,\s*[^,]+,\s*([^\n]+)',native):
 assert call.startswith('AccountNotificationIdentity.scope('),call
assert 'AccountNotificationIdentity.dialog(currentAccount, dialogId, story)' in native
files={
'android/os/Bundle.java':'package android.os;public class Bundle extends java.util.HashMap<String,Object>{}',
'android/content/Intent.java':'''package android.content;public class Intent {public final java.util.Set<String> categories=new java.util.HashSet<>();private final android.os.Bundle extras=new android.os.Bundle();public Intent addCategory(String c){categories.add(c);return this;}public android.os.Bundle getExtras(){return extras;}public Intent put(String k,Object v){extras.put(k,v);return this;}}''',
'android/content/Context.java':'''package android.content;public class Context {public static final String NOTIFICATION_SERVICE="notifications";public android.app.NotificationManager manager;public Object getSystemService(String s){return manager;}}''',
'android/app/Notification.java':'''package android.app;public class Notification {public static final String CATEGORY_MESSAGE="msg";public static final int FLAG_ONGOING_EVENT=2;public String category;public int flags;public Notification(String c,int f){category=c;flags=f;}}''',
'android/service/notification/StatusBarNotification.java':'''package android.service.notification;public class StatusBarNotification {final int id;final String tag;final android.app.Notification n;public StatusBarNotification(int id,String tag,android.app.Notification n){this.id=id;this.tag=tag;this.n=n;}public String getTag(){return tag;}public int getId(){return id;}public android.app.Notification getNotification(){return n;}}''',
'android/app/NotificationManager.java':'''package android.app;public class NotificationManager {public android.service.notification.StatusBarNotification[] values;public java.util.ArrayList<Integer> cancelled=new java.util.ArrayList<>();public android.service.notification.StatusBarNotification[] getActiveNotifications(){return values;}public void cancel(int id){cancelled.add(id);}}''',
'org/telegram/messenger/FileLog.java':'package org.telegram.messenger;public class FileLog {public static void e(Exception e){throw new AssertionError(e);}}',
'IdentityCheck.java':r'''
import org.telegram.messenger.AccountNotificationIdentity;import android.app.*;import android.content.*;import android.service.notification.*;import java.util.*;
public class IdentityCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  Set<String> tags=new HashSet<>();Set<Set<String>> intents=new HashSet<>();
  // These full dialog IDs collide under Telegram's old low/high integer sum.
  long[] dialogs={1L,4294967296L,-1L,Long.MAX_VALUE,Long.MIN_VALUE};
  for(int account=0;account<128;account++){
   check(tags.add(AccountNotificationIdentity.summary(account)));check(tags.add(AccountNotificationIdentity.dialog(account,0,true)));
   for(long dialog:dialogs){check(tags.add(AccountNotificationIdentity.dialog(account,dialog,false)));
    Intent intent=new Intent().put("dialog_id",dialog).put("topic_id",0L);
    check(AccountNotificationIdentity.scope(intent,account)==intent);check(intents.add(new HashSet<>(intent.categories)));
    Set<String> original=new HashSet<>(intent.categories);AccountNotificationIdentity.scope(intent,account);check(original.equals(intent.categories));
   }
  }
  check(!AccountNotificationIdentity.summary(Integer.MAX_VALUE).equals(AccountNotificationIdentity.summary(0)));
  try{AccountNotificationIdentity.summary(-1);throw new AssertionError();}catch(IllegalArgumentException expected){}
  Context context=new Context();context.manager=new NotificationManager();
  context.manager.values=new StatusBarNotification[]{
   new StatusBarNotification(1,null,new Notification(Notification.CATEGORY_MESSAGE,0)),
   new StatusBarNotification(5,null,new Notification("service",2)),
   new StatusBarNotification(6,null,new Notification(Notification.CATEGORY_MESSAGE,2)),
   new StatusBarNotification(7,"nebula.messages.account:0:summary",new Notification(Notification.CATEGORY_MESSAGE,0)),
   new StatusBarNotification(8,"nebula-task",new Notification(Notification.CATEGORY_MESSAGE,0))};
  AccountNotificationIdentity.removeLegacyMessages(context);check(context.manager.cancelled.equals(Arrays.asList(1)));
  System.out.println("Notification identities: 128 accounts, colliding full dialog IDs, intent idempotence, max ID and legacy-message migration passed");
 }
}'''}
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'));javac=shutil.which('javac') or str(java_home/'bin/javac.exe');java=shutil.which('java') or str(java_home/'bin/java.exe')
with tempfile.TemporaryDirectory(prefix='nebula-notification-') as tmp:
 tmp=Path(tmp)
 for name,text in files.items():
  p=tmp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
 subprocess.run([javac,'--release','8','-encoding','UTF-8','-d',str(tmp),str(helper),*map(str,tmp.rglob('*.java'))],check=True)
 subprocess.run([java,'-cp',str(tmp),'IdentityCheck'],check=True)
 sdk=Path(os.environ.get('ANDROID_HOME') or str(Path.home()/'AppData/Local/Android/Sdk'))
 jars=[p for p in (sdk/'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*',p.parent.name)]
 if not jars:raise SystemExit('Android SDK platform required')
 jar=max(jars,key=lambda p:tuple(map(int,p.parent.name.removeprefix('android-').split('.'))))
 subprocess.run([javac,'-encoding','UTF-8','-cp',str(jar),'-d',str(tmp/'sdk'),str(helper),str(tmp/'org/telegram/messenger/FileLog.java')],check=True)
 print('Production notification helper compiled against Android SDK')
