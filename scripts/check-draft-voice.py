"""Execute production draft/WAV policy and native pause/apply hooks; SDK-check its UI."""
from pathlib import Path
import os, re, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'


def method(source, name):
    start = source.index(name)
    start = source.rfind('\n', 0, start) + 1
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


with tempfile.TemporaryDirectory(prefix='nebula-draft-voice-') as folder:
    work = Path(folder)
    # Read the pinned source and apply all native patches to this owned fixture.
    path = 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java'
    pin = subprocess.check_output(['git', 'rev-parse', 'HEAD:vendor/telegram-android'], cwd=ROOT, text=True).strip()
    dest = work / path; dest.parent.mkdir(parents=True)
    upstream = Path(os.environ.get('NEBULA_ANDROID_UPSTREAM_TREE', ROOT / 'vendor/telegram-android'))
    dest.write_bytes(subprocess.check_output(['git', '-C', str(upstream), 'show', pin + ':' + path]))
    subprocess.run(['git', 'init', '--quiet', str(work)], check=True)
    for patch in sorted((ROOT / 'patches/android').glob('*.patch')):
        subprocess.run(['git', '-C', str(work), 'apply', '--whitespace=nowarn', '--include=' + path, '-'],
                       input=patch.read_text(encoding='utf-8').encode('utf-8'), check=True)
    source = dest.read_text(encoding='utf-8')
    methods = '\n'.join(method(source, name) for name in ['nebulaVoiceSnapshot()', 'nebulaCancelVoiceTranscription()', 'nebulaRequestVoiceTranscription()',
        'private boolean nebulaPrepareVoiceSend(', 'private boolean nebulaVoiceSendCurrent(', 'private boolean nebulaApplyVoiceText(', 'public int getMessagesCount()'])
    # The first occurrences are declarations, not calls.
    assert 'MediaController.getInstance().toggleRecordingPause(voiceOnce)' in methods
    assert 'resetRecordedState();' in methods and methods.index('resetRecordedState();') < methods.index('setFieldText(text);')
    assert 'SendMessagesHelper' not in method(source, 'nebulaRequestVoiceTranscription()'), 'Aa must not auto-send'
    for signature in ['private void resetRecordedState()', 'public void onPause()', 'public void onDestroy()', 'public void setDialogId(long id, int account)']:
        body = method(source, signature)
        assert body.index('nebulaCancelVoiceTranscription();') < 150
    assert 'if (id == 5 && action == AccessibilityNodeInfoCompat.ACTION_CLICK) nebulaRequestVoiceTranscription();' in source
    ui = (UI / 'NebulaDraftVoiceTranscription.java').read_text(encoding='utf-8')
    assert 'if(!ready&&!changed)start();' in ui and 'voiceOnce' not in ui
    assert 'gate.complete(token,current.get())' in ui and 'scopeCheck' in ui
    assert 'new File(source.path)' in ui and 'source.path).delete' not in ui

    stubs = {
        'app/nebulagram/ui/NebulaDraftVoiceTranscription.java': '''package app.nebulagram.ui;
import java.util.function.*;
public class NebulaDraftVoiceTranscription {
 public static boolean allowed=true;public static int starts;
 public final NebulaDraftVoicePolicy.Snapshot source;public final Supplier<NebulaDraftVoicePolicy.Snapshot> current;
 public final Consumer<String> use;public final BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember;public final String cached;public final boolean changed;public boolean cancelled;public String edited;
 public static boolean enabled(){return allowed;}public static String serviceKey(){return "device|ru";}
 public NebulaDraftVoiceTranscription(Object host,NebulaDraftVoicePolicy.Snapshot s,Supplier<NebulaDraftVoicePolicy.Snapshot> c,String cache,boolean changed,Consumer<String> use,BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember){starts++;source=s;current=c;cached=cache;this.changed=changed;this.use=use;this.remember=remember;}
 public boolean isShowing(){return !cancelled;}
 public boolean isTextSelected(){return !cancelled;}
 public String textForSend(){return edited==null||edited.trim().isEmpty()?null:edited;}
 public NebulaDraftVoiceTranscription(Object host,NebulaDraftVoicePolicy.Snapshot s,Supplier<NebulaDraftVoicePolicy.Snapshot> c,String cache,boolean changed,Consumer<String> use,BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember,Object inline){this(host,s,c,cache,changed,use,remember);}
 public void cancel(){cancelled=true;}
}''',
        'app/nebulagram/ui/NebulaDraftVoiceInlineHost.java': '''package app.nebulagram.ui;public class NebulaDraftVoiceInlineHost {public NebulaDraftVoiceInlineHost(Object anchor,Object parent,java.util.function.Supplier<Integer> top,Runnable closed){}}''',
        'app/nebulagram/ui/NebulaAudioPreferences.java': '''package app.nebulagram.ui;
public class NebulaAudioPreferences {public static boolean available=true;public static boolean hasTranscriptionService(){return available;}}''',
        'app/nebulagram/ui/NebulaAudioSettingsFragment.java': 'package app.nebulagram.ui;public class NebulaAudioSettingsFragment {}'
    }
    for name, text in stubs.items():
        p = work / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text, encoding='utf-8')
    fixture = r'''import java.io.*;
import java.nio.file.*;
import java.util.*;
import app.nebulagram.ui.*;
class Rect {float top;void setEmpty(){}boolean isEmpty(){return false;}}
class PreviewParams {int forwarded;int getForwardedMessagesCount(){return forwarded;}}
class Parent {int settings;PreviewParams messagePreviewParams=new PreviewParams();void presentFragment(Object value){settings++;}}
class TextUtils {static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}
class SendMessagesHelper {static String getTrimmedString(String s){return s.trim();}}
class MessageInput {String value="";CharSequence getText(){return value;}}
class Controller {int getMaxMessageLength(){return 4096;}}
class Account {Controller getMessagesController(){return new Controller();}}
class MediaController {static final MediaController shared=new MediaController();int pauses;static MediaController getInstance(){return shared;}void toggleRecordingPause(boolean once){pauses++;}}
class UserConfig {static long owner=101;static UserConfig getInstance(int account){return new UserConfig();}long getClientUserId(){return owner;}}
class AndroidUtilities {static final List<Runnable> now=new ArrayList<>(),later=new ArrayList<>();static void runOnUIThread(Runnable r){now.add(r);}static void runOnUIThread(Runnable r,long delay){later.add(r);}static void drain(){List<Runnable> p=new ArrayList<>(now);now.clear();for(Runnable r:p)r.run();}}
class Timeline {long left,right;long getAudioLeftMs(){return left;}long getAudioRightMs(){return right;}}
class Control {void invalidate(){}void setEnabled(boolean value){}boolean isShown(){return true;}void getLocationOnScreen(int[] position){}}
class Delegate {void needStartRecordAudio(int value){}}
class Hooks {
 Parent parentFragment=new Parent();boolean isPaused,recordingAudioVideo,video,sendButtonVisible=true,voiceOnce,nebulaVoicePending;
 int currentAccount,recordingGuid=7,resets;long dialog_id=30,nebulaVoiceRevision;
 Object audioToSend=new Object();String audioToSendPath,text="";boolean focused;
 final Rect nebulaAaRect=new Rect(),pauseRect=new Rect();Timeline audioTimelineView=new Timeline();Control controlsView=new Control(),slideText=new Control();Delegate delegate=new Delegate();Object sizeNotifierLayout=new Object();
 NebulaDraftVoicePolicy.Snapshot nebulaVoiceSendSource;String nebulaVoiceSendText;Object nebulaVoiceSendAttempt;
 MessageInput messageEditText=new MessageInput();Account accountInstance=new Account();
 NebulaDraftVoiceTranscription nebulaVoiceSheet;NebulaDraftVoicePolicy.Snapshot nebulaVoiceResultSource;String nebulaVoiceResult;
 boolean isInVideoMode(){return video;}
 void resetRecordedState(){resets++;nebulaCancelVoiceTranscription();audioToSend=null;}
 void setFieldText(String value){text=value;}void setFieldFocused(boolean value){focused=value;}
 void updateSendButtonPaid(){}
 boolean hasAudioToSend(){return audioToSend!=null;}
 boolean prepareSend(){return nebulaPrepareVoiceSend(true);}
 boolean currentSend(Object attempt,NebulaDraftVoicePolicy.Snapshot source){return nebulaVoiceSendCurrent(attempt,source);}
 boolean applySend(Object attempt,NebulaDraftVoicePolicy.Snapshot source,String text){return nebulaApplyVoiceText(attempt,source,text);}
 METHODS
 void request(){nebulaRequestVoiceTranscription();}void cancel(){nebulaCancelVoiceTranscription();}
 void completePause(){recordingAudioVideo=false;audioToSend=new Object();if(nebulaVoicePending){nebulaVoicePending=false;AndroidUtilities.runOnUIThread(this::nebulaRequestVoiceTranscription);}}
}
public class DraftVoiceCheck {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static NebulaDraftVoicePolicy.Snapshot snapshot(File f,long revision,long left,long right){return new NebulaDraftVoicePolicy.Snapshot(0,101,30,7,revision,f,left,right,"device|ru");}
 public static void main(String[] args)throws Exception {
  File root=new File(args[0]);File voice=new File(root,"original.ogg");Files.write(voice.toPath(),new byte[]{1,2,3,4});byte[] original=Files.readAllBytes(voice.toPath());
  Hooks h=new Hooks();h.audioToSendPath=voice.getPath();h.recordingAudioVideo=true;h.audioToSend=null;
  h.request();h.request();check(MediaController.shared.pauses==1&&h.nebulaVoicePending&&NebulaDraftVoiceTranscription.starts==0,"double Aa must pause once and wait for the file");
  h.completePause();AndroidUtilities.drain();check(h.nebulaVoiceSheet!=null&&!h.recordingAudioVideo,"Aa opens only after native pause completion");
  NebulaDraftVoiceTranscription first=h.nebulaVoiceSheet;first.remember.accept(first.source,"hello");check("hello".equals(h.nebulaVoiceResult),"completed transcript is cached for this draft");
  h.request();check(h.nebulaVoiceSheet==null&&h.resets==0,"Aa toggles back to original voice without discarding it");
  h.request();check("hello".equals(h.nebulaVoiceSheet.cached),"same recording reuses reviewed result");
  h.audioTimelineView.left=1;h.request();h.request();check("hello".equals(h.nebulaVoiceSheet.cached)&&h.nebulaVoiceSheet.changed,"trim displays stale text and requires fresh recognition");
  first.use.accept("stale");check(h.resets==0&&h.text.isEmpty(),"old trim must not replace audio");
  NebulaDraftVoiceTranscription trimmed=h.nebulaVoiceSheet;h.cancel();h.recordingAudioVideo=true;trimmed.use.accept("stale after resume");check(h.resets==0,"resume invalidates a pending text action");
  h.recordingAudioVideo=false;h.audioTimelineView.left=0;h.request();NebulaDraftVoiceTranscription good=h.nebulaVoiceSheet;good.use.accept("reviewed");check(h.resets==1&&"reviewed".equals(h.text)&&h.focused,"explicit text choice replaces audio and focuses composer");
  check(Arrays.equals(original,Files.readAllBytes(voice.toPath())),"recognition and cancellation must preserve original recording");
  Hooks sending=new Hooks();sending.audioToSendPath=voice.getPath();sending.request();
  check(!sending.prepareSend()&&sending.audioToSend!=null&&sending.resets==0,"loading/empty text cannot send or discard original voice");
  sending.nebulaVoiceSheet.edited="x".repeat(8193);sending.parentFragment.messagePreviewParams.forwarded=2;
  check(sending.prepareSend()&&sending.getMessagesCount()==5&&sending.resets==0,"paid text chunks and forwarded messages counted before confirmation");
  Object attempt=sending.nebulaVoiceSendAttempt;NebulaDraftVoicePolicy.Snapshot chosen=sending.nebulaVoiceSendSource;String chosenText=sending.nebulaVoiceSendText;
  sending.nebulaVoiceSheet.edited="changed while confirmation is open";
  check(sending.currentSend(attempt,chosen)&&chosenText.length()==8193,"confirmation captures reviewed text instead of reading mutable UI later");
  check(sending.applySend(attempt,chosen,chosenText)&&sending.resets==1&&sending.audioToSend==null&&sending.text.equals(chosenText)&&!sending.voiceOnce,"confirmed text uses native text path exactly once");
  check(!sending.applySend(attempt,chosen,chosenText)&&sending.resets==1,"duplicate confirmation cannot discard or send twice");
  for(int mutation=0;mutation<4;mutation++){
   Hooks pending=new Hooks();pending.audioToSendPath=voice.getPath();pending.request();pending.nebulaVoiceSheet.edited="reviewed";
   check(pending.prepareSend(),"reviewed text prepares");Object token=pending.nebulaVoiceSendAttempt;NebulaDraftVoicePolicy.Snapshot selected=pending.nebulaVoiceSendSource;
   switch(mutation){case 0:pending.cancel();break;case 1:pending.audioTimelineView.left=1;break;case 2:pending.dialog_id++;break;case 3:pending.prepareSend();break;}
   check(!pending.currentSend(token,selected)&&!pending.applySend(token,selected,"stale")&&pending.resets==0&&pending.audioToSend!=null,"cancel/trim/peer/new attempt invalidates old confirmation "+mutation);
  }
  Hooks missing=new Hooks();missing.audioToSendPath=voice.getPath();NebulaAudioPreferences.available=false;missing.request();check(missing.parentFragment.settings==1&&missing.resets==0,"missing service opens settings with voice kept");NebulaAudioPreferences.available=true;
  for(int field=0;field<8;field++) {
   Hooks owner=new Hooks();owner.audioToSendPath=voice.getPath();owner.request();NebulaDraftVoiceTranscription job=owner.nebulaVoiceSheet;
   switch(field){case 0:owner.currentAccount++;break;case 1:UserConfig.owner++;break;case 2:owner.dialog_id++;break;case 3:owner.recordingGuid++;break;case 4:owner.nebulaVoiceRevision++;break;case 5:owner.audioTimelineView.right=1;break;case 6:owner.isPaused=true;break;case 7:owner.video=true;break;}
   job.use.accept("stale owner");check(owner.resets==0&&owner.text.isEmpty(),"owner/lifecycle change rejects output "+field);UserConfig.owner=101;
  }
  NebulaDraftVoicePolicy.Gate gate=new NebulaDraftVoicePolicy.Gate();NebulaDraftVoicePolicy.Snapshot s=snapshot(voice,0,0,0);
  for(int i=0;i<100;i++) {
   int old=gate.begin(s);gate.cancel();int fresh=gate.begin(s);
   check(!gate.complete(old,s)&&gate.accepts(fresh,s),"cancelled completion cannot end current work");
   check(!gate.accepts(fresh,snapshot(voice,1,0,0)),"recording revision invalidates result");
   check(!gate.accepts(fresh,snapshot(voice,0,1,0)),"trim invalidates result");
   check(gate.complete(fresh,s)&&!gate.complete(fresh,s),"completion is consumed once");
  }
  File pcm=new File(root,"decoded.pcm"),wave=new File(root,"fragment.wav");byte[] bytes=new byte[32000];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)i;Files.write(pcm.toPath(),bytes);
  NebulaDraftVoicePolicy.wave(pcm,wave,125,875,14000000,()->false);byte[] result=Files.readAllBytes(wave.toPath());
  check(result.length==24044&&new String(result,0,4,"US-ASCII").equals("RIFF")&&new String(result,8,4,"US-ASCII").equals("WAVE"),"valid mono 16 kHz WAV header/region size");
  check(Arrays.equals(Arrays.copyOfRange(result,44,result.length),Arrays.copyOfRange(bytes,4000,28000)),"only selected PCM samples are recognized");
  check(Arrays.equals(bytes,Files.readAllBytes(pcm.toPath())),"PCM source is immutable");
  long[] full=NebulaDraftVoicePolicy.region(32000,Long.MIN_VALUE,Long.MAX_VALUE);check(full[0]==0&&full[1]==32000,"extreme trim clamps without overflow");
  try{NebulaDraftVoicePolicy.wave(pcm,wave,0,0,100,()->false);throw new AssertionError("size limit ignored");}catch(IOException expected){checks++;}
  try{NebulaDraftVoicePolicy.wave(pcm,wave,0,0,14000000,()->true);throw new AssertionError("cancel ignored");}catch(InterruptedIOException expected){checks++;}
  System.out.println("OK: "+checks+" production/native draft pause, stale owner/trim/resume, explicit text/audio and selected WAV checks");
 }
}'''.replace('METHODS', methods)
    java = work / 'DraftVoiceCheck.java'; java.write_text(fixture, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(UI / 'NebulaDraftVoicePolicy.java'), str(java), *map(str, (work / 'app').rglob('*.java'))], check=True)
    subprocess.run(['java', '-cp', str(work), 'DraftVoiceCheck', str(work)], check=True, timeout=20)
print('OK: owned pinned Android patch application; source lifetime/accessibility guards')
