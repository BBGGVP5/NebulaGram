"""Execute production toolbar motion and native acknowledgement admission offline."""
from pathlib import Path
import re, subprocess, tempfile, sys
root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
bar=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingToolbar.java').read_text(encoding='utf-8')
def method(s, token):
 start=s.index('{',s.index(token));end=start+1;depth=1
 while depth:depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start+1:end-1]
prepare=method(bar,'public int prepare(').replace('android.animation.ValueAnimator','Animator').replace('new android.view.animation.DecelerateInterpolator()','new Object()')
chat=(tree/'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
allowed=chat.split('allowedNotificationsDuringChatListAnimations = new int[]{',1)[1].split('};',1)[0]
allowed=re.sub(r'//[^\n]*','',allowed).replace('NotificationCenter.','')
center=(tree/'TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java').read_text(encoding='utf-8')
post=method(center,'public void postNotificationName(final int id,')
names=set(re.findall(r'\b(?:messageReceivedBy\w+|startAllHeavyOperations|stopAllHeavyOperations|didReplacedPhotoInMemCache|closeChats|invalidateMotionBackground|needCheckSystemBarColors)\b',post))
names.update(re.findall(r'\b[a-zA-Z]\w*\b',allowed));names.add('messageReceivedByServer')
constants=';'.join(f'static final int {n}={i+1}' for i,n in enumerate(sorted(names)))+';'
program=r'''
import java.util.*;
class View {boolean isLaidOut(){return true;}}
class AndroidUtilities {static int dp(int x){return x;}}
class NebulaGlass {static boolean reduced;static boolean reduced(){return reduced;}}
class Animator {
 int start,end,value;boolean cancelled;java.util.function.Consumer<Animator> listener;static int starts;
 static Animator ofInt(int a,int b){Animator r=new Animator();r.start=r.value=a;r.end=b;return r;}
 void cancel(){cancelled=true;}void setDuration(int x){}void setInterpolator(Object x){}void addUpdateListener(java.util.function.Consumer<Animator> l){listener=l;}
 void start(){starts++;}Object getAnimatedValue(){return value;}void step(float f){if(!cancelled){value=Math.round(start+(end-start)*f);listener.accept(this);}}
}
class Motion {
 static int HEIGHT_DP=52,GAP_DP=8,VISIBLE=0,GONE=8;boolean preferenceEnabled=true,expanded,attached=true;int reservedHeight,visibility=GONE,layouts;float alpha;Animator reveal;
 Runnable layoutChanged=()->layouts++;Object getParent(){return new View();}boolean isAttachedToWindow(){return attached;}void setVisibility(int v){visibility=v;}void setAlpha(float a){alpha=a;}void updatePalette(){}
 int prepare(boolean eligible){PREPARE}
}
class SystemClock {static long elapsedRealtime(){return 100;}}
class BuildVars {static boolean DEBUG_VERSION=false;}
class AckGate {
 CONSTANTS
 static int EXPIRE_NOTIFICATIONS_TIME=5017;int currentHeavyOperationFlags;boolean admitted;
 static class AllowedNotifications {int[] allowedIds;long time;AllowedNotifications(int[] a){allowedIds=a;}}
 static class Sparse {ArrayList<AllowedNotifications> data=new ArrayList<>();int size(){return data.size();}AllowedNotifications valueAt(int i){return data.get(i);}int keyAt(int i){return i;}}
 Sparse allowedNotifications=new Sparse();boolean shouldDebounce(int id,Object[] args){return false;}void postNotificationDebounced(int id,Object[]args){throw new AssertionError();}
 void postNotificationNameInternal(int id,boolean allowed,Object...args){admitted=allowed;}void onAnimationFinish(int i){throw new AssertionError("unexpected expiry");}
 void postNotificationName(final int id,Object...args){POST}
 static int[] chatAllowed={ALLOWED};
}
class Regression {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[]args){
  Motion t=new Motion();check(t.prepare(true)==0&&t.reveal!=null,"first appearance snaps");
  t.reveal.step(.5f);check(t.prepare(true)>0&&t.prepare(true)<60&&t.alpha>0&&t.alpha<1,"no intermediate motion");
  Animator previous=t.reveal;t.prepare(false);previous.step(1);check(t.reservedHeight<60,"cancelled reveal changes height");t.reveal.step(1);check(t.prepare(false)==0&&t.visibility==8,"hidden toolbar reserves space");
  for(int i=0;i<1000;i++){t.prepare(true);t.reveal.step(1);int starts=Animator.starts;check(t.prepare(true)==60,"toolbar loses 8dp gap");t.prepare(true);check(starts==Animator.starts,"typing restarts animation");t.prepare(false);t.reveal.step(1);}
  NebulaGlass.reduced=true;check(t.prepare(true)==60&&t.visibility==0&&t.alpha==1,"reduced motion not immediate");check(t.prepare(false)==0&&t.visibility==8,"reduced hide retains toolbar");
  AckGate gate=new AckGate();gate.allowedNotifications.data.add(new AckGate.AllowedNotifications(AckGate.chatAllowed));gate.postNotificationName(AckGate.messageReceivedByAck);check(gate.admitted,"real ACK waits behind chat animation");
  gate.postNotificationName(AckGate.messageReceivedByServer);check(!gate.admitted,"structural server event bypasses ordering");
  gate.allowedNotifications.data.add(new AckGate.AllowedNotifications(null));gate.postNotificationName(AckGate.messageReceivedByAck);check(!gate.admitted,"unrelated exclusive animation bypassed");
  System.out.println("1000 reversible reveal/hide cycles, reduced motion, gap and actual native ACK admission passed; no network simulation");
 }
}
'''.replace('PREPARE',prepare).replace('CONSTANTS',constants).replace('POST',post).replace('ALLOWED',allowed)
with tempfile.TemporaryDirectory(prefix='nebula-motion-') as tmp:
 p=Path(tmp)/'Regression.java';p.write_text(program,encoding='utf-8');subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(p)],check=True);subprocess.run(['java','-cp',tmp,'Regression'],check=True)
