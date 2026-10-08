"""Run production draft substitution with deterministic IME/provider/lifecycle doubles."""
from pathlib import Path
import subprocess,tempfile,runpy
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source=(ui/'NebulaDraftTranslation.java').read_text(encoding='utf-8').replace('org.telegram.tgnet.TLRPC','TLRPC')
source='\n'.join(line for line in source.splitlines() if not line.startswith(('import android.','import org.telegram.')))
a=source.index('    private TextView action(');b=source.index('    public void stop()',a)
source=source[:a]+'''    private void show(CharSequence answer,CharSequence source,String error,boolean loading) { shownSource=NebulaRichText.key(activeAccount,source);shownAnswer=answer; }
'''+source[b:]
stubs=r'''package app.nebulagram.ui;
import java.util.*;import java.util.concurrent.*;
class NebulaTasks {static long user(int a){return 100+a;}}
class View {}class BaseFragment {}class PopupWindow {void dismiss(){}}class LinearLayout {}class TextView {}class ProgressBar {}
class EditText {String text="";int start,end;boolean composing;CharSequence rich="";CharSequence getText(){return rich;}Paint getPaint(){return new Paint();}static class Paint{Object getFontMetricsInt(){return null;}}int length(){return text.length();}int getSelectionStart(){return start;}int getSelectionEnd(){return end;}void setSelection(int i){start=end=i;}void setSelection(int a,int b){start=a;end=b;}void text(CharSequence s){text=s.toString();rich=new android.text.SpannableStringBuilder(s);setSelection(s.length());}}
class BaseInputConnection {static EditText field;static int getComposingSpanStart(CharSequence s){return field.composing?0:-1;}}
class DialogObject {static boolean isEncryptedDialog(long d){return d<0;}}
class AndroidUtilities {static final BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();static int dp(int n){return n;}static void runOnUIThread(Runnable r){ui.add(r);}static void runOnUIThread(Runnable r,int delay){ui.add(r);}static void cancelRunOnUIThread(Runnable r){ui.remove(r);}}
class NebulaTranslationSettings {static String identity="nano:stable",lang="en";static boolean available=true,enabled=true,automatic=true;static String draftLanguage(int a,long d){return lang;}static String translationIdentity(){return identity;}static boolean translationAvailable(){return available;}static boolean draft(int a,long d){return enabled;}static boolean automatic(int a,long d){return automatic;}static int delay(int a,long d){return 150;}}
class NebulaTranslationClient {static final List<String> inputs=new CopyOnWriteArrayList<>();static volatile CountDownLatch wait;boolean cancelled;void cancel(){cancelled=true;}static String errorText(Exception e){return "failed";}String translate(String s,String lang,boolean interactive,java.util.function.Consumer<String> phase)throws Exception{inputs.add(s);if(wait!=null)while(!wait.await(5,TimeUnit.MILLISECONDS))if(cancelled)throw new java.io.InterruptedIOException();if(cancelled)throw new java.io.InterruptedIOException();return s.replace("Привет","Hello").replace("мир","world");}}
class TLRPC { static class TL_textWithEntities { String text; CharSequence styled; } }
class NebulaRichText {
 static CharSequence snapshot(CharSequence s){return new android.text.SpannableStringBuilder(s);}
 static TLRPC.TL_textWithEntities capture(int a,CharSequence s){var t=new TLRPC.TL_textWithEntities();t.text=s.toString();t.styled=snapshot(s);return t;}
 static String key(TLRPC.TL_textWithEntities t){return t.text+((android.text.SpannableStringBuilder)t.styled).signature();}
 static String key(int a,CharSequence s){return key(capture(a,s));}
 static CharSequence render(TLRPC.TL_textWithEntities t,Object metrics){return t.styled;}
 static TLRPC.TL_textWithEntities translate(NebulaTranslationClient c,TLRPC.TL_textWithEntities t,String lang,boolean priority,java.util.function.Consumer<String> progress)throws Exception{return capture(0,c.translate(t.text,lang,priority,progress));}
}
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
  check(original.restore(field.text).toString().equals("Привет мир"),"full original preserved");
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
  draft.stop();field.text("Привет C");draft.changed(1,10,true);ui();ui();check(original.restore(field.text).toString().equals("Привет C"),"account and dialog have separate original");
  NebulaTranslationSettings.automatic=false;field.text("Hello C мир");draft.changed(1,10,true);ui();ui();
  check(field.text.equals("Hello C мир"),"manual preview does not replace automatically");
  var click=NebulaDraftTranslation.class.getDeclaredMethod("useOrRestore");click.setAccessible(true);click.invoke(draft);
  check(field.text.equals("Hello C world"),"manual Apply remains available when an earlier original exists");
  click.invoke(draft);check(field.text.equals("Привет C мир"),"manual result also restores the full original");
  NebulaTranslationSettings.automatic=true;
  field.text("Привет off");draft.changed(1,10,true);draft.stop();check(AndroidUtilities.ui.isEmpty(),"pause cancels pending debounce");
  NebulaDraftOriginal state=new NebulaDraftOriginal();state.replaced("исходный","translated");
  check(state.restore("prefix translated").toString().equals("prefix исходный"),"prefix additions preserved");
  check(state.restore("trans-edited").toString().equals("исходный"),"editing translated phrase never destroys its original");
  state.source("trans-edited");state.replaced("trans-edited","edited translation");
  check(state.restore("edited translation").toString().equals("исходный"),"retranslating edits keeps the earlier source intact");
  var styled=new android.text.SpannableStringBuilder("Привет 😀").mark(0,6,"bold").mark(7,9,"custom:42");
  state.clear();state.replaced(styled,"Hello 😀");
  var restored=(android.text.SpannableStringBuilder)state.restore("Hello 😀!");
  check(restored.toString().equals("Привет 😀!")&&restored.signature().equals(styled.signature()),"restored original retains bold and custom emoji with appended text");
  restored=(android.text.SpannableStringBuilder)state.restore("!Hello 😀");
  check(restored.marks.get(0).start==1&&restored.marks.get(1).start==8,"prefix shifts original span offsets");
  field.text(styled);draft.changed(1,10,true);ui();
  ((android.text.SpannableStringBuilder)field.rich).mark(0,6,"italic");ui();
  check(field.text.equals("Привет 😀"),"format-only edit rejects stale result");
  draft.stop();original.clear();NebulaTranslationSettings.automatic=false;
  original.replaced("older source","older result");
  field.text(new android.text.SpannableStringBuilder("Привет preview 😀").mark(0,6,"bold"));
  draft.changed(1,10,true);ui();ui();
  ((android.text.SpannableStringBuilder)field.rich).mark(0,6,"italic");
  String changedStyle=NebulaRichText.key(1,field.getText());click.invoke(draft);
  check(changedStyle.equals(NebulaRichText.key(1,field.getText())),"manual Apply cannot overwrite new styles or restore an unrelated original");
  draft.stop();
  System.out.println("Actual draft requests: automatic insertion, original, continued typing, IME, A/B race, model/account changes and pause passed");System.exit(0);
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-draft-original-') as folder:
 p=Path(folder);span=p/'android/text/SpannableStringBuilder.java';span.parent.mkdir(parents=True);span.write_text(runpy.run_path(str(root/'scripts/translation-span-fixture.py'))['SPAN_BUILDER'],encoding='utf-8');(p/'NebulaDraftTranslation.java').write_text(source,encoding='utf-8');(p/'DraftOriginalCheck.java').write_text(stubs,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(span),str(p/'NebulaDraftTranslation.java'),str(p/'DraftOriginalCheck.java'),str(ui/'NebulaDraftOriginal.java'),str(ui/'NebulaDraftRequestGate.java')],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.DraftOriginalCheck'],check=True,timeout=20)
