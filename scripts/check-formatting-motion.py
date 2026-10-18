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
enter=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
text_transition=method(enter,'public boolean canShowTextMessageTransition()')
apply=method(enter,'private boolean nebulaApplyVoiceText(')
assert apply.index('messageEditText.requestFocus()') < apply.index('resetRecordedState(true)') < apply.index('setFieldText(text)')
reset=method(enter,'private void resetRecordedState(boolean sentAsText)')
assert 'hideRecordedAudioPanel(sentAsText)' in reset and 'recordPannelAnimation.cancel()' in reset
assert 'recordCircle.setSendButtonInvisible()' in reset
item=(tree/'TMessagesProj/src/main/java/org/telegram/ui/recyclerview/ChatListItemAnimator.java').read_text(encoding='utf-8')
insertion=method(item,'public void animateAddImpl(')
send_start=insertion.index('if (activity != null')
send_header=insertion[send_start:insertion.index('{',send_start)]
send_block=send_header+'{'+method(insertion[send_start:],'if (activity != null')+'}'
flight=(tree/'TMessagesProj/src/main/java/org/telegram/ui/TextMessageEnterTransition.java').read_text(encoding='utf-8')
flight_allowed=re.search(r'notificationsLocker\s*=\s*new AnimationNotificationsLocker\((.*?)\);',flight,re.S).group(1).strip() or 'null'
flight_allowed=re.sub(r'//[^\n]*','',flight_allowed).replace('NotificationCenter.','')
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
 static int HEIGHT_DP=52,GAP_DP=4,VISIBLE=0,GONE=8;boolean preferenceEnabled=true,expanded,attached=true;int reservedHeight,visibility=GONE,layouts;float alpha;Animator reveal;
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
 static int[] chatAllowed={ALLOWED};static int[] flightAllowed=FLIGHT_ALLOWED;
}
class ComposerOrigin {
 int nebulaFormattingHeight;
 static int VISIBLE=0;static class Field {int visibility;float alpha=1;boolean layout;int getVisibility(){return visibility;}float getAlpha(){return alpha;}boolean isLayoutRequested(){return layout;}}
 Field messageEditText,recordedAudioPanel;boolean valid(){TEXT_TRANSITION}
}
class SendInsertion {
 static class Cell {Message message=new Message();Message getMessageObject(){return message;}}
 static class Message {boolean voice;boolean isVoice(){return voice;}}
 static class Editor {boolean pending,textEligible;int clears;boolean canShowMessageTransition(){return pending;}boolean canShowTextMessageTransition(){return textEligible;}void startMessageTransition(){clears++;pending=false;}}
 static class Activity {Set<Message> animatingMessageObjects=new HashSet<>();Editor editor=new Editor();Object messageEnterTransitionContainer;Editor getChatActivityEnterView(){return editor;}}
 static class Surface {float translation;int height=100;float getTranslationY(){return translation;}int getMeasuredHeight(){return height;}}
 static class SharedConfig {static int PERFORMANCE_CLASS_LOW=0,performance=1;static int getDevicePerformanceClass(){return performance;}}
 static class VoiceMessageEnterTransition {static int starts;VoiceMessageEnterTransition(Object... args){}void start(){starts++;}}
 static class TextMessageEnterTransition {static int starts;TextMessageEnterTransition(Object... args){}void start(){starts++;}}
 Activity activity=new Activity();Cell chatMessageCell=new Cell();Surface view=new Surface(),recyclerListView=new Surface();Object resourcesProvider;
 void insert(){SEND_BLOCK}
}
class Regression {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[]args){
  for(int height:new int[]{0,1,30,60}) for(int bits=0;bits<32;bits++) {
   ComposerOrigin c=new ComposerOrigin();c.messageEditText=new ComposerOrigin.Field();c.recordedAudioPanel=new ComposerOrigin.Field();
   c.nebulaFormattingHeight=height;
   c.messageEditText.visibility=(bits&1)!=0?8:0;c.messageEditText.alpha=(bits&2)!=0?.5f:1f;c.messageEditText.layout=(bits&4)!=0;c.recordedAudioPanel.visibility=(bits&8)!=0?0:8;
   if((bits&16)!=0)c.messageEditText=null;
   check(c.valid()==(bits==0&&height==0),"text flight crosses toolbar or uses hidden/stale field geometry");
  }
  for(int bits=0;bits<64;bits++) {
   SendInsertion s=new SendInsertion();boolean outgoing=(bits&1)!=0,pending=(bits&2)!=0,voice=(bits&4)!=0,eligible=(bits&8)!=0,low=(bits&16)!=0,far=(bits&32)!=0;
   if(outgoing)s.activity.animatingMessageObjects.add(s.chatMessageCell.message);
   s.activity.editor.pending=pending;s.activity.editor.textEligible=eligible;s.chatMessageCell.message.voice=voice;
   SendInsertion.SharedConfig.performance=low?0:1;s.view.translation=far?400:50;
   SendInsertion.TextMessageEnterTransition.starts=0;SendInsertion.VoiceMessageEnterTransition.starts=0;s.insert();
   check(s.activity.editor.clears==(outgoing&&pending?1:0),"composer clearing incorrectly waits for a text flight");
   check(SendInsertion.TextMessageEnterTransition.starts==(outgoing&&pending&&!voice&&eligible&&!low&&!far?1:0),"text flight eligibility changed");
   check(SendInsertion.VoiceMessageEnterTransition.starts==(outgoing&&pending&&voice&&!far?1:0),"voice flight eligibility changed");
   s.insert();check(s.activity.editor.clears==(outgoing&&pending?1:0),"repeated row callback clears a new draft");
  }
  Motion t=new Motion();check(t.prepare(true)==0&&t.reveal!=null,"first appearance snaps");
  t.reveal.step(.5f);check(t.prepare(true)>0&&t.prepare(true)<56&&t.alpha>0&&t.alpha<1,"no intermediate motion");
  Animator previous=t.reveal;t.prepare(false);previous.step(1);check(t.reservedHeight<56,"cancelled reveal changes height");t.reveal.step(1);check(t.prepare(false)==0&&t.visibility==8,"hidden toolbar reserves space");
  for(int i=0;i<1000;i++){t.prepare(true);t.reveal.step(1);int starts=Animator.starts;check(t.prepare(true)==56,"toolbar loses 4dp gap");t.prepare(true);check(starts==Animator.starts,"typing restarts animation");t.prepare(false);t.reveal.step(1);}
  NebulaGlass.reduced=true;check(t.prepare(true)==56&&t.visibility==0&&t.alpha==1,"reduced motion not immediate");check(t.prepare(false)==0&&t.visibility==8,"reduced hide retains toolbar");
  AckGate gate=new AckGate();gate.allowedNotifications.data.add(new AckGate.AllowedNotifications(AckGate.chatAllowed));gate.postNotificationName(AckGate.messageReceivedByAck);check(gate.admitted,"real ACK waits behind chat animation");
  gate.allowedNotifications.data.add(new AckGate.AllowedNotifications(AckGate.flightAllowed));gate.postNotificationName(AckGate.messageReceivedByAck);check(gate.admitted,"text-flight lock delays real ACK even though chat-list lock allows it");
  gate.postNotificationName(AckGate.messageReceivedByServer);check(!gate.admitted,"structural server event bypasses ordering");
  gate.allowedNotifications.data.add(new AckGate.AllowedNotifications(null));gate.postNotificationName(AckGate.messageReceivedByAck);check(!gate.admitted,"unrelated exclusive animation bypassed");
  System.out.println("128 composer origins, 64 native insertion cases, 1000 reversible reveal/hide cycles, reduced motion, gap and actual native ACK admission passed; no network simulation");
 }
}
'''.replace('SEND_BLOCK',send_block).replace('TEXT_TRANSITION',text_transition).replace('PREPARE',prepare).replace('CONSTANTS',constants).replace('POST',post).replace('FLIGHT_ALLOWED',flight_allowed).replace('ALLOWED',allowed)
with tempfile.TemporaryDirectory(prefix='nebula-motion-') as tmp:
 p=Path(tmp)/'Regression.java';p.write_text(program,encoding='utf-8');subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(p)],check=True);subprocess.run(['java','-cp',tmp,'Regression'],check=True)
