"""Execute native record-button touch routing, lock state and queued-release races."""
from pathlib import Path
import subprocess
import sys
import tempfile

tree = Path(sys.argv[1])
source = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')

def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

touch = method(source[source.index('audioVideoButtonContainer = new FrameLayout(context)'):],
               'public boolean onTouchEvent(MotionEvent motionEvent)')
lock = method(source, 'private void startLockTransition()')
java = r'''
import java.util.*;
class RecordingTapCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class MotionEvent {
  static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
  final int action;final float x,y;MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
  int getAction(){return action;}float getX(){return x;}float getY(){return y;}
 }
 static class Parent {void requestDisallowInterceptTouchEvent(boolean b){}}
 static class View {static final int VISIBLE=0,GONE=8;int visibility=GONE;Parent parent=new Parent();
  Parent getParent(){return parent;}int getVisibility(){return visibility;}float getX(){return 0;}void invalidate(){}
  int getMeasuredWidth(){return 400;}}
 static class AndroidUtilities {
  static final List<Runnable> tasks=new ArrayList<>();
  static void runOnUIThread(Runnable r,int delay){tasks.add(r);}
  static void cancelRunOnUIThread(Runnable r){tasks.removeIf(t->t==r);}
  static void drain(){List<Runnable> copy=new ArrayList<>(tasks);tasks.clear();copy.forEach(Runnable::run);}
 }
 static class TLRPC {static class Chat{}static class UserFull{boolean voice_messages_forbidden;}}
 static class ChatObject {static boolean canSendVoice(Object c){return true;}static boolean canSendRoundVideo(Object c){return true;}}
 static class Fragment {TLRPC.Chat getCurrentChat(){return null;}TLRPC.UserFull getCurrentUserInfo(){return null;}long getDialogId(){return 1;}}
 static class MediaController {
  static final MediaController instance=new MediaController();int sends,cancels,pauses;
  static MediaController getInstance(){return instance;}
  void stopRecording(int state,boolean notify,int date,boolean once,long stars){if(state==0)cancels++;else sends++;}
  void toggleRecordingPause(boolean once){pauses++;}
 }
 static class CameraController {static final CameraController c=new CameraController();
  static CameraController getInstance(){return c;}void cancelOnInitRunnable(Runnable r){}}
 static class AlertsCreator {
  static boolean paid;static Pay confirmation;static Schedule schedule;
  interface Pay{void accept(long stars);}interface Schedule{void accept(boolean notify,int date,int repeat);}
  static boolean needsPaidMessageAlert(int a,long d){return paid;}
  static void ensurePaidMessageConfirmation(int a,long d,int count,Pay p){confirmation=p;}
  static void createScheduleDatePickerDialog(Object a,long d,Schedule p,Runnable cancel,Object resources){schedule=p;}
 }
 static class HapticFeedbackConstants{static int KEYBOARD_TAP=1,FLAG_IGNORE_GLOBAL_SETTING=2;}
 static class AccessibilityEvent{static int TYPE_VIEW_CLICKED=1;}
 static class ViewConfiguration{static int getLongPressTimeout(){return 500;}}
 static class ObjectAnimator {
  static ObjectAnimator ofFloat(Object o,String p,float... f){return new ObjectAnimator();}
  void setStartDelay(int d){}ObjectAnimator setDuration(int d){return this;}void setInterpolator(Object o){}
 }
 static class AnimatorSet {void playTogether(ObjectAnimator... a){}void start(){}}
 static class CubicBezierInterpolator{static final Object EASE_OUT_QUINT=new Object();}
 static class SharedConfig {static void removeLockRecordAudioVideoHint(){}}
 static class Slide extends View {void setEnabled(boolean b){}void setSlideX(float x){}void setCancelToProgress(float x){}}
 static class Sender {void setEffect(long effect){}}
 static class Host extends View {
  static final int RECORD_STATE_SENDING=1,RECORD_STATE_CANCEL_BY_GESTURE=2;
  boolean isLiveComment,nebulaCameraChoicePending,nebulaIgnoreCameraChoiceRelease,nebulaRecordActionTap;
  boolean hasRecordVideo=true,calledRecordRunnable,recordAudioVideoRunnableStarted,recordingAudioVideo;
  boolean sendVoiceEnabled=true,sendRoundEnabled=true,sendButtonVisible,voiceOnce,shouldDrawBackground=true;
  boolean video=true,scheduled,messageTransitionIsRunning;int currentAccount,starts,switches,videoSends,videoCancels,paidSends;
  long dialog_id=1,effectId;float startedDraggingX=-1,distCanMove=140,slideToCancelProgress=1,startTranslation;
  long millisecondsRecorded;View recordedAudioPanel=new View(),sizeNotifierLayout=new View(),controlsView=new View();
  View audioVideoButtonContainer=this;Fragment parentFragment=new Fragment();Object parentActivity=new Object(),resourcesProvider;
  TLRPC.UserFull userInfo;Slide slideText=new Slide();Sender sendButton=new Sender();Circle recordCircle=new Circle();
  Runnable moveToSendStateRunnable,onFinishInitCameraRunnable=()->{};
  final Runnable recordAudioVideoRunnable=()->{starts++;calledRecordRunnable=true;recordAudioVideoRunnableStarted=false;recordingAudioVideo=true;};
  final Delegate delegate=new Delegate();
  class Circle {
   boolean isSendButtonVisible(){return sendButtonVisible;}
   int setLockTranslation(float y){if(y<-57){sendButtonVisible=true;return 2;}return 1;}
   void setMovingCords(float x,float y){}void invalidate(){}
  }
  class Delegate {
   void needStartRecordVideo(int state,boolean notify,int date,int repeat,int once,long effect,long stars){
    if(state==1){videoSends++;sendButtonVisible=false;}if(state==2)videoCancels++;
   }
   void needStartRecordAudio(int state){}void toggleVideoRecordingPause(){}
   void needShowMediaBanHint(){}void onSwitchRecordMode(boolean video){switches++;}
  }
  void createRecordCircle(){}boolean isInVideoMode(){return video;}boolean isInScheduleMode(){return scheduled;}
  void setRecordVideoButtonVisible(boolean value,boolean animated){video=value;}
  void performHapticFeedback(int a){}void performHapticFeedback(int a,int b){}void sendAccessibilityEvent(int a){}
  void updateRecordInterface(int state,boolean animate){}void setSlideToCancelProgress(float p){slideToCancelProgress=p;}
  int dp(int n){return n;}Object getContext(){return this;}
  boolean sendMessageInternal(boolean notify,int date,int repeat,long stars,boolean fromKeyboard){paidSends++;return false;}
''' + touch + chr(10) + lock + r'''
 }
 static void event(Host h,int action){h.onTouchEvent(new MotionEvent(action,10,10));}
 static Host fresh(){AndroidUtilities.tasks.clear();AlertsCreator.paid=false;AlertsCreator.confirmation=null;AlertsCreator.schedule=null;
  MediaController.instance.sends=MediaController.instance.cancels=MediaController.instance.pauses=0;return new Host();}
 public static void main(String[] args){int cases=0;
  for(boolean video:new boolean[]{false,true})for(boolean locked:new boolean[]{false,true})for(boolean called:new boolean[]{false,true}) {
   Host h=fresh();h.video=video;h.recordingAudioVideo=true;h.sendButtonVisible=locked;h.calledRecordRunnable=called;
   h.recordAudioVideoRunnableStarted=!called;if(!called)AndroidUtilities.tasks.add(h.recordAudioVideoRunnable);
   event(h,MotionEvent.ACTION_DOWN);event(h,MotionEvent.ACTION_UP);AndroidUtilities.drain();
   check(h.switches==0&&h.video==video,"Tap during recording switches the voice/video mode");
   check((video?h.videoSends:MediaController.instance.sends)==1,"Active tap does not finish exactly once");
   check(h.starts==0&&!h.recordingAudioVideo,"Delayed start restarts a finished recording");cases++;
  }
  Host auto=fresh();auto.recordingAudioVideo=true;auto.calledRecordRunnable=true;auto.startLockTransition();
  check(auto.sendButtonVisible,"Camera-choice auto-lock only animates; actual send state is missing");
  event(auto,MotionEvent.ACTION_DOWN);event(auto,MotionEvent.ACTION_UP);check(auto.videoSends==1&&auto.switches==0,"Auto-lock tap fails");cases++;
  for(boolean video:new boolean[]{false,true}) {
   Host idle=fresh();idle.video=video;event(idle,MotionEvent.ACTION_DOWN);event(idle,MotionEvent.ACTION_UP);AndroidUtilities.drain();
   check(idle.switches==1&&idle.video!=video&&idle.starts==0,"Idle short tap must still switch mode");cases++;
   Host hold=fresh();hold.video=video;event(hold,MotionEvent.ACTION_DOWN);AndroidUtilities.drain();event(hold,MotionEvent.ACTION_UP);AndroidUtilities.drain();
   check(hold.starts==1&&hold.switches==0&&(video?hold.videoSends:MediaController.instance.sends)==1,"Hold/release routing changed");cases++;
   Host cancel=fresh();cancel.video=video;event(cancel,MotionEvent.ACTION_DOWN);event(cancel,MotionEvent.ACTION_CANCEL);AndroidUtilities.drain();
   check(cancel.starts==0&&cancel.switches==0,"Cancelled pending hold changes mode");cases++;
   Host paid=fresh();paid.video=video;paid.recordingAudioVideo=true;paid.calledRecordRunnable=true;paid.startLockTransition();AlertsCreator.paid=true;
   event(paid,MotionEvent.ACTION_DOWN);event(paid,MotionEvent.ACTION_UP);AndroidUtilities.drain();
   check(paid.switches==0&&paid.videoSends==0&&MediaController.instance.sends==0&&AlertsCreator.confirmation!=null,"Paid confirmation bypassed or mode changed");
   AlertsCreator.confirmation.accept(5);check(paid.paidSends==1,"Paid confirmation send duplicated");cases++;
   Host interrupted=fresh();interrupted.video=video;interrupted.recordingAudioVideo=true;interrupted.calledRecordRunnable=true;
   event(interrupted,MotionEvent.ACTION_DOWN);AndroidUtilities.drain();event(interrupted,MotionEvent.ACTION_CANCEL);
   check(interrupted.switches==0&&(video?interrupted.videoSends:MediaController.instance.sends)==1,"Release after UI reset finishes twice");cases++;
   Host drag=fresh();drag.video=video;event(drag,MotionEvent.ACTION_DOWN);AndroidUtilities.drain();drag.startedDraggingX=0;
   drag.onTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,-150,10));
   check((video?drag.videoCancels:MediaController.instance.cancels)==1&&!drag.recordingAudioVideo,"Slide-to-cancel changed");cases++;
  }
  Host chooser=fresh();chooser.nebulaCameraChoicePending=true;event(chooser,MotionEvent.ACTION_DOWN);event(chooser,MotionEvent.ACTION_UP);
  check(chooser.starts==0&&chooser.switches==0,"Chooser release leaks into mode switching");cases++;
  System.out.println(cases+" native recording tap/lock/payment/hold/cancel cases passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-record-taps-') as folder:
    work=Path(folder)
    file=work/'RecordingTapCheck.java'
    file.write_text(java,encoding='utf-8')
    camera=work/'app/nebulagram/ui/NebulaRoundCamera.java'
    camera.parent.mkdir(parents=True)
    camera.write_text('package app.nebulagram.ui;public class NebulaRoundCamera {public static final int ASK=3;public static int mode(){return ASK;}}')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(file),str(camera)],check=True)
    subprocess.run(['java','-cp',str(work),'RecordingTapCheck'],check=True)
