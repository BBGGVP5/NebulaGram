"""Run production draft substitution with deterministic IME/provider/lifecycle doubles."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source=(ui/'NebulaDraftTranslation.java').read_text(encoding='utf-8')
source='\n'.join(line for line in source.splitlines() if not line.startswith(('import android.','import org.telegram.')))
a=source.index('    private TextView action(');b=source.index('    public void stop()',a)
source=source[:a]+'''    private void show(String answer,String source,String error,boolean loading) { }
'''+source[b:]
stubs=r'''package app.nebulagram.ui;
import java.util.*;import java.util.concurrent.*;
class NebulaTasks {static long user(int a){return 100+a;}}
class View {}class BaseFragment {}class PopupWindow {void dismiss(){}}class LinearLayout {}class TextView {}class ProgressBar {}
class EditText {String text="";int start,end;boolean composing;String getText(){return text;}int length(){return text.length();}int getSelectionStart(){return start;}int getSelectionEnd(){return end;}void setSelection(int i){start=end=i;}void setSelection(int a,int b){start=a;end=b;}void text(String s){text=s;setSelection(s.length());}}
class BaseInputConnection {static EditText field;static int getComposingSpanStart(String s){return field.composing?0:-1;}}
class DialogObject {static boolean isEncryptedDialog(long d){return d<0;}}
class AndroidUtilities {static final BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();static int dp(int n){return n;}static void runOnUIThread(Runnable r){ui.add(r);}static void runOnUIThread(Runnable r,int delay){ui.add(r);}static void cancelRunOnUIThread(Runnable r){ui.remove(r);}}
class NebulaTranslationSettings {static String identity="nano:stable",lang="en";static boolean available=true,enabled=true,automatic=true;static String draftLanguage(int a,long d){return lang;}static String translationIdentity(){return identity;}static boolean translationAvailable(){return available;}static boolean draft(int a,long d){return enabled;}static boolean automatic(int a,long d){return automatic;}static int delay(int a,long d){return 150;}}
class NebulaTranslationClient {static final List<String> inputs=new CopyOnWriteArrayList<>();static volatile CountDownLatch wait;boolean cancelled;void cancel(){cancelled=true;}static String errorText(Exception e){return "failed";}String translate(String s,String lang,boolean interactive,java.util.function.Consumer<String> phase)throws Exception{inputs.add(s);if(wait!=null)while(!wait.await(5,TimeUnit.MILLISECONDS))if(cancelled)throw new java.io.InterruptedIOException();if(cancelled)throw new java.io.InterruptedIOException();return s.replace("Привет","Hello").replace("мир","world");}}
public class DraftOriginalCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void ui()throws Exception{Runnable r=AndroidUtilities.ui.poll(3,TimeUnit.SECONDS);check(r!=null,"UI callback expected");r.run();}
 public static void main(String[] args)throws Exception{
  EditText field=BaseInputConnection.field=new EditText();NebulaDraftTranslation[] holder=new NebulaDraftTranslation[1];
  holder[0]=new NebulaDraftTranslation(new BaseFragment(),field,new View(),text->{field.text(text);holder[0].changed(0,9,true);});NebulaDraftTranslation draft=holder[0];
  field.text("Привет");draft.changed(0,9,true);ui();ui();check(field.text.equals("Hello"),"automatic insertion");
  int calls=NebulaTranslationClient.inputs.size();draft.changed(0,9,true);check(AndroidUtilities.ui.isEmpty()&&calls==NebulaTranslationClient.inputs.size(),"inserted text never translates itself");
  field.text("Hello мир");draft.changed(0,9,true);ui();ui();check(field.text.equals("Hello world"),"continue typing after automatic insertion");
  check(NebulaTranslationClient.inputs.get(calls).equals("Привет мир"),"appending uses the complete original language draft");
  var f=NebulaDraftTranslation.class.getDeclaredField("original");f.setAccessible(true);NebulaDraftOriginal original=(NebulaDraftOriginal)f.get(draft);
  check(original.restore(field.text).equals("Привет мир"),"full original preserved");
  draft.stop();draft.changed(0,9,true);check(AndroidUtilities.ui.isEmpty(),"resume keeps translated field and original");
  field.text("");draft.changed(0,9,true);check(!original.hasOriginal(),"send/empty discards old original");
  field.text("Привет IME");field.composing=true;draft.changed(0,9,true);ui();check(field.text.equals("Привет IME")&&!AndroidUtilities.ui.isEmpty(),"IME composition waits without replacement");
  field.composing=false;ui();ui();check(field.text.equals("Hello IME"),"finished IME composition translates");
  field.text("Привет A");NebulaTranslationClient.wait=new CountDownLatch(1);draft.changed(0,9,true);ui();
  long deadline=System.currentTimeMillis()+3000;while(!NebulaTranslationClient.inputs.contains("Привет A")&&System.currentTimeMillis()<deadline)Thread.sleep(5);
  field.text("Привет B");draft.changed(0,9,true);NebulaTranslationClient.wait.countDown();NebulaTranslationClient.wait=null;
  ui();ui();ui();check(field.text.equals("Hello B"),"late A cannot overwrite B");
  field.text("Привет model");NebulaTranslationClient.wait=new CountDownLatch(1);draft.changed(0,9,true);ui();
  deadline=System.currentTimeMillis()+3000;while(!NebulaTranslationClient.inputs.contains("Привет model")&&System.currentTimeMillis()<deadline)Thread.sleep(5);
  check(NebulaTranslationClient.inputs.contains("Привет model"),"model request entered transport");
  NebulaTranslationSettings.identity="nano:preview";NebulaTranslationClient.wait.countDown();NebulaTranslationClient.wait=null;ui();check(field.text.equals("Привет model"),"model change rejects stale answer");
  draft.stop();field.text("Привет C");draft.changed(1,10,true);ui();ui();check(original.restore(field.text).equals("Привет C"),"account and dialog have separate original");
  field.text("Привет off");draft.changed(1,10,true);draft.stop();check(AndroidUtilities.ui.isEmpty(),"pause cancels pending debounce");
  NebulaDraftOriginal state=new NebulaDraftOriginal();state.replaced("исходный","translated");
  check(state.restore("prefix translated").equals("prefix исходный"),"prefix additions preserved");
  check(state.restore("trans-edited").equals("исходный"),"editing translated phrase never destroys its original");
  state.source("trans-edited");state.replaced("trans-edited","edited translation");
  check(state.restore("edited translation").equals("исходный"),"retranslating edits keeps the earlier source intact");
  System.out.println("Actual draft requests: automatic insertion, original, continued typing, IME, A/B race, model/account changes and pause passed");System.exit(0);
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-draft-original-') as folder:
 p=Path(folder);(p/'NebulaDraftTranslation.java').write_text(source,encoding='utf-8');(p/'DraftOriginalCheck.java').write_text(stubs,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(p/'NebulaDraftTranslation.java'),str(p/'DraftOriginalCheck.java'),str(ui/'NebulaDraftOriginal.java'),str(ui/'NebulaDraftRequestGate.java')],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.DraftOriginalCheck'],check=True,timeout=20)
