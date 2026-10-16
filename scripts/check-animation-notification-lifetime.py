"""Execute production notification-lock ownership and expiry timer code with a virtual UI clock."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile
root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
base=tree/'TMessagesProj/src/main/java/org/telegram/messenger'
locker=(base/'AnimationNotificationsLocker.java').read_text(encoding='utf-8').replace('package org.telegram.messenger;','')
center=(base/'NotificationCenter.java').read_text(encoding='utf-8')
start=center.index('    private void checkForExpiredNotifications() {');end=center.index('    public void updateAllowedNotifications(',start)
expiry=center[start:end]
start=center.index('    public void onAnimationFinish(int index) {');end=center.index('    public void runDelayedNotifications()',start)
finish=center[start:end]
source=r'''
import java.util.*;
class UserConfig {static int selectedAccount;}
class NotificationCenter {
 static final Map<Integer,NotificationCenter> centers=new HashMap<>();static final NotificationCenter global=new NotificationCenter();
 final Set<Integer> locks=new HashSet<>();int next;
 static NotificationCenter getInstance(int owner){return centers.computeIfAbsent(owner,k->new NotificationCenter());}
 static NotificationCenter getGlobalInstance(){return global;}
 int setAnimationInProgress(int old,int[] allowed){onAnimationFinish(old);int token=++next;locks.add(token);return token;}
 void onAnimationFinish(int token){locks.remove(token);}
 void postNotificationName(int event,int flags){}
}
LOCKER
class SystemClock {static long now;static long elapsedRealtime(){return now;}}
class AndroidUtilities {
 record Task(long due,Runnable action){}static PriorityQueue<Task> ui=new PriorityQueue<>(Comparator.comparingLong(Task::due));
 static void runOnUIThread(Runnable r,long delay){ui.add(new Task(SystemClock.now+delay,r));}
 static void cancelRunOnUIThread(Runnable r){ui.removeIf(t->t.action==r);}
 static void advance(long now){SystemClock.now=now;while(!ui.isEmpty()&&ui.peek().due<=now)ui.remove().action.run();}
}
class Sparse {
 final TreeMap<Integer,AllowedNotifications> data=new TreeMap<>();int size(){return data.size();}
 AllowedNotifications valueAt(int i){return new ArrayList<>(data.values()).get(i);}int keyAt(int i){return new ArrayList<>(data.keySet()).get(i);}
 AllowedNotifications get(int i){return data.get(i);}void delete(int i){data.remove(i);}
}
class AllowedNotifications {long time;AllowedNotifications(long time){this.time=time;}}
class Expiry {
 final long EXPIRE_NOTIFICATIONS_TIME=5017;final Sparse allowedNotifications=new Sparse();Runnable checkForExpiredNotifications;
 int animationInProgressCount=1,delayedAcks=1,deliveredAcks;final ArrayList<Integer> heavyOperationsCounter=new ArrayList<>();int startAllHeavyOperations;
 FINISH
 void runDelayedNotifications(){deliveredAcks+=delayedAcks;delayedAcks=0;}
 EXPIRY
 void run(){checkForExpiredNotifications();}
}
class NotificationLifetimeCheck {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[]args){
  if(args.length==0){
  UserConfig.selectedAccount=0;AnimationNotificationsLocker old=new AnimationNotificationsLocker();old.lock();
  UserConfig.selectedAccount=7;AnimationNotificationsLocker unrelated=new AnimationNotificationsLocker();unrelated.lock();
  int unrelatedToken=unrelated.notificationsIndex;old.lock();
  check(NotificationCenter.getInstance(0).locks.isEmpty(),"Account switch leaks the old animation lock");
  check(NotificationCenter.getInstance(7).locks.contains(unrelatedToken),"Account switch releases another account's unrelated token");
  old.unlock();unrelated.unlock();check(NotificationCenter.global.locks.isEmpty(),"Global locks retained");
  }
  for(int delta=1;delta<=1000;delta++){
   AndroidUtilities.ui.clear();SystemClock.now=5017;Expiry e=new Expiry();e.animationInProgressCount=2;
   e.allowedNotifications.data.put(0,new AllowedNotifications(0));e.allowedNotifications.data.put(1,new AllowedNotifications(5017-delta));e.run();
   check(e.animationInProgressCount==1&&e.deliveredAcks==0&&!AndroidUtilities.ui.isEmpty(),"Recent animation expired immediately");
   AndroidUtilities.advance(10034);
   check(e.animationInProgressCount==0&&e.deliveredAcks==1&&e.allowedNotifications.size()==0,"Expiry timer only assigns its callback; delayed acknowledgements remain queued");
  }
  AndroidUtilities.ui.clear();SystemClock.now=5017;Expiry ended=new Expiry();ended.allowedNotifications.data.put(1,new AllowedNotifications(4500));ended.run();
  ended.onAnimationFinish(1);AndroidUtilities.advance(10034);
  check(ended.checkForExpiredNotifications==null&&AndroidUtilities.ui.isEmpty(),"Finished animation leaves a ghost expiry callback");
  System.out.println("Native lock ownership with colliding account tokens and 1000 late-lock expiry timers passed");
 }
}
'''.replace('LOCKER',locker.replace('public class AnimationNotificationsLocker','class AnimationNotificationsLocker')).replace('EXPIRY',expiry).replace('FINISH',finish)
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-notification-lifetime-') as work:
    work=Path(work);test=work/'NotificationLifetimeCheck.java';test.write_text(source,encoding='utf-8')
    subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(work),str(test)],check=True)
    subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'NotificationLifetimeCheck',*sys.argv[2:]],check=True)
print('No ACK, server update, network timeout or protocol ordering bypass was added.')
