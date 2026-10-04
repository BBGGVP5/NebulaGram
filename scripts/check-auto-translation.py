"""Execute the production incoming queue against deterministic platform/provider doubles."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
source = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAutoTranslate.java').read_text(encoding='utf-8')
source = '\n'.join(line for line in source.splitlines() if not line.startswith(('import android.', 'import org.telegram.')))
stubs = r'''package app.nebulagram.ui;
import java.util.*;
import java.util.concurrent.*;
class SharedPreferences {
 final Map<String,Object> data = new HashMap<>();
 boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}
 String getString(String k,String d){return (String)data.getOrDefault(k,d);}
 int getInt(String k,int d){return (Integer)data.getOrDefault(k,d);}
 SharedPreferences edit(){return this;} SharedPreferences putBoolean(String k,boolean v){data.put(k,v);return this;}
 SharedPreferences putString(String k,String v){data.put(k,v);return this;} void apply(){}
}
class Context { SharedPreferences getSharedPreferences(String s,int n){return NebulaTranslationSettings.global();} }
class ApplicationLoader { static Context applicationContext=new Context(); }
class NebulaTranslationSettings {
 static final SharedPreferences p=new SharedPreferences(); static String identity="nano:stable";
 static SharedPreferences prefs(int a){return p;} static SharedPreferences global(){return p;}
 static String connectionIdentity(){return identity;} static String translationIdentity(){return identity;} static boolean local(){return false;} static String label(String s){return s;}
 static boolean outgoing(int a,long d){return p.getBoolean("outgoing_"+d,false);}
}
class NebulaTasks { static long user(int a){return a+100;} }
class NebulaAiAvailability { static boolean enabled(){return true;} }
class DialogObject { static boolean isEncryptedDialog(long d){return false;} }
class NebulaText { static String text(String ru,String en){return en;} }
class TLRPC { static class Message { String message="hello",translatedToLanguage; TL_textWithEntities translatedText; boolean noforwards; int ttl; }
 static class TL_textWithEntities { String text; } static class Chat { boolean noforwards; } }
class MessageObject { TLRPC.Message messageOwner=new TLRPC.Message(); int id; boolean own,isRestrictedMessage; MessageObject(int id){this.id=id;}
 long getDialogId(){return 9;} int getId(){return id;} boolean isOutOwner(){return own;} boolean isSecretMedia(){return false;} boolean isSponsored(){return false;} }
class TranslateController { static int cancellations; static boolean isTranslatable(MessageObject m){return true;} void cancelTranslations(long d){cancellations++;} }
class MessagesController { static MessagesController getInstance(int a){return new MessagesController();} TLRPC.Chat getChat(long d){return null;} TranslateController getTranslateController(){return new TranslateController();} }
class NotificationCenter { static final int messageTranslating=1,messageTranslated=2,dialogTranslate=3; static int starts,finishes;
 static NotificationCenter getInstance(int a){return new NotificationCenter();} void postNotificationName(int id,Object... args){if(id==1)starts++;if(id==2)finishes++;} }
class AndroidUtilities { static final BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>(); static void runOnUIThread(Runnable r){ui.add(r);} }
class Toast { static final int LENGTH_SHORT=0; static Toast makeText(Context c,String s,int n){return new Toast();} void show(){} }
class BaseFragment { int getCurrentAccount(){return 0;} Context getContext(){return new Context();} Object getResourceProvider(){return null;} void presentFragment(Object o){} void showDialog(Object o){} }
class NebulaTranslationFragment { NebulaTranslationFragment(int a,long d){} }
interface Click { void click(Object d,int w); }
class NebulaDialog { static class Builder { Builder(Context c,Object r){} Builder setTitle(String s){return this;} Builder setMessage(String s){return this;} Builder setPositiveButton(String s,Click c){return this;} Builder setNeutralButton(String s,Click c){return this;} Builder setNegativeButton(String s,Click c){return this;} Object create(){return this;} } }
class NebulaAiSecrets { static String read(int p){if(p<0||p>3)throw new IllegalArgumentException("Cloud credentials only");return "";} }
class NebulaNanoAi { static String responseErrorText(Throwable e){return "Download model";} }
class NebulaAiClient {
 static final int NANO=4; static volatile int calls; static volatile boolean fail; static volatile CountDownLatch wait;
 static final java.util.concurrent.atomic.AtomicInteger active=new java.util.concurrent.atomic.AtomicInteger(), peak=new java.util.concurrent.atomic.AtomicInteger();
 volatile boolean cancelled; void cancel(){cancelled=true;}
 String generate(int p,String e,String k,String m,String prompt,String text)throws Exception{
  calls++; peak.accumulateAndGet(active.incrementAndGet(),Math::max);
  try { CountDownLatch latch=wait; if(latch!=null) while(!latch.await(10,TimeUnit.MILLISECONDS))if(cancelled)throw new java.io.InterruptedIOException();
   if(cancelled)throw new java.io.InterruptedIOException();if(fail)throw new Exception("private request");return "AI:"+text;
  } finally { active.decrementAndGet(); }
 }
}
class NebulaTranslationClient {
 final NebulaAiClient ai=new NebulaAiClient(); void cancel(){ai.cancel();}
 String translate(String text,String lang,boolean interactive,java.util.function.Consumer<String> progress)throws Exception {
  return ai.generate(0,"","","","",text);
 }
 static String errorText(Exception error){return "Download model";}
}
public class AutoTranslationCheck {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void finish()throws Exception{Runnable r=AndroidUtilities.ui.poll(3,TimeUnit.SECONDS);check(r!=null,"completion");r.run();}
 public static void main(String[] args)throws Exception{
  NebulaTranslationSettings.p.putBoolean("on_9",true);NebulaTranslationSettings.p.data.put("provider",4);
  MessageObject m=new MessageObject(1);m.messageOwner.translatedToLanguage="ru";m.messageOwner.translatedText=new TLRPC.TL_textWithEntities();m.messageOwner.translatedText.text="Telegram result";
  NebulaAutoTranslate.request(0,m);check(NebulaAutoTranslate.isTranslating(0,m),"loading while queued/running");finish();
  check(NebulaAiClient.calls==1,"Nano reaches inference without requesting cloud credentials");
  check("AI:hello".equals(m.messageOwner.translatedText.text),"Telegram cache must not skip AI");
  check(!NebulaAutoTranslate.isTranslating(0,m),"loading ends");int calls=NebulaAiClient.calls;
  NebulaAutoTranslate.request(0,m);check(calls==NebulaAiClient.calls,"own result deduplicated");
  m.messageOwner.translatedText.text="Telegram overwrite";NebulaAutoTranslate.request(0,m);
  check("AI:hello".equals(m.messageOwner.translatedText.text),"restore provider result after foreign overwrite");
  MessageObject obsolete=new MessageObject(2);NebulaAiClient.wait=new CountDownLatch(1);NebulaAutoTranslate.request(0,obsolete);
  NebulaTranslationSettings.identity="nano:preview";NebulaAiClient.wait.countDown();finish();
  check(obsolete.messageOwner.translatedText==null,"changed model rejects old completion");NebulaAiClient.wait=null;
  NebulaAiClient.fail=true;MessageObject failed=new MessageObject(3);NebulaAutoTranslate.request(0,failed);finish();
  check(NebulaAutoTranslate.status(0,9).contains("check required"),"error state persists in panel");
  NebulaAiClient.fail=false;NebulaAutoTranslate.retry(0,9);NebulaAutoTranslate.request(0,failed);finish();
  check("AI:hello".equals(failed.messageOwner.translatedText.text),"retry bypasses failure backoff");
  check(TranslateController.cancellations>=3,"native requests cancelled on AI path");
  check(NotificationCenter.starts>=6,"loading notifications for requests and completions");
  MessageObject own=new MessageObject(10);own.own=true;own.messageOwner.message="my sent text";
  NebulaAutoTranslate.request(0,own);check(!NebulaAutoTranslate.isTranslating(0,own),"sent messages require independent consent");
  NebulaAutoTranslate.setOutgoing(0,9,true);NebulaAutoTranslate.disable(0,9);
  check(NebulaAutoTranslate.enabled(0,9),"outgoing-only remains enabled when incoming disabled");
  NebulaAutoTranslate.request(0,own);finish();check("AI:my sent text".equals(own.messageOwner.translatedText.text),"own sent message translated");
  check("my sent text".equals(own.messageOwner.message),"original sent text remains immutable");
  MessageObject disabledIncoming=new MessageObject(11);NebulaAutoTranslate.request(0,disabledIncoming);
  check(!NebulaAutoTranslate.isTranslating(0,disabledIncoming),"outgoing-only must not process incoming messages");
  NebulaAutoTranslate.disableAll(0,9);check(!NebulaAutoTranslate.enabled(0,9),"Original/Hide stops both displayed directions");
  NebulaTranslationSettings.p.putBoolean("on_9",true);NebulaTranslationSettings.p.data.put("provider",0);
  NebulaAiClient.wait=new CountDownLatch(1);NebulaAiClient.peak.set(0);
  MessageObject a=new MessageObject(20),b=new MessageObject(21),c=new MessageObject(22);a.messageOwner.message="A";b.messageOwner.message="B";c.messageOwner.message="C";
  NebulaAutoTranslate.request(0,a);NebulaAutoTranslate.request(0,b);NebulaAutoTranslate.request(0,c);
  long deadline=System.currentTimeMillis()+3000;while(NebulaAiClient.active.get()<2&&System.currentTimeMillis()<deadline)Thread.sleep(5);
  check(NebulaAiClient.active.get()==2,"two remote requests run concurrently instead of serially");
  NebulaAutoTranslate.retainVisible(0,9,Arrays.asList(c));
  check(!NebulaAutoTranslate.isTranslating(0,a)&&!NebulaAutoTranslate.isTranslating(0,b)&&NebulaAutoTranslate.isTranslating(0,c),"offscreen active work stops reporting progress; visible queue retained");
  NebulaAiClient.wait.countDown();finish();finish();finish();NebulaAiClient.wait=null;
  check(a.messageOwner.translatedText==null&&b.messageOwner.translatedText==null,"offscreen completions cache without applying");
  check("AI:C".equals(c.messageOwner.translatedText.text)&&NebulaAiClient.peak.get()==2,"visible work proceeds and concurrency remains bounded");
  MessageObject edited=new MessageObject(23);edited.messageOwner.message="before";NebulaAiClient.wait=new CountDownLatch(1);NebulaAutoTranslate.request(0,edited);
  deadline=System.currentTimeMillis()+3000;while(NebulaAiClient.active.get()==0&&System.currentTimeMillis()<deadline)Thread.sleep(5);
  check(NebulaAiClient.active.get()==1,"edited request entered transport before cancellation");
  edited.messageOwner.message="after";NebulaAutoTranslate.retainVisible(0,9,Arrays.asList(edited));NebulaAiClient.wait.countDown();finish();NebulaAiClient.wait=null;
  finish();check("AI:after".equals(edited.messageOwner.translatedText.text),"only the updated source completes, regardless of callback order");
  NebulaAutoTranslate.stop(0,9);
  List<MessageObject> many=new ArrayList<>();
  for(int i=0;i<45;i++){MessageObject item=new MessageObject(100+i);item.messageOwner.message="visible "+i;many.add(item);}
  NebulaAiClient.wait=new CountDownLatch(1);NebulaAutoTranslate.retainVisible(0,9,many);
  for(MessageObject item:many)NebulaAutoTranslate.request(0,item);
  NebulaAiClient.wait.countDown();NebulaAiClient.wait=null;
  for(int i=0;i<many.size();i++)finish();
  for(MessageObject item:many)check(item.messageOwner.translatedText!=null&&("AI:"+item.messageOwner.message).equals(item.messageOwner.translatedText.text),"overflow never drops a visible message");
  check(!NebulaAutoTranslate.status(0,9).contains("translating"),"busy ends after full queue");
  NebulaAutoTranslate.stop(0,9);
  MessageObject unsent=new MessageObject(-1);unsent.own=true;NebulaAutoTranslate.setOutgoing(0,9,true);NebulaAutoTranslate.request(0,unsent);
  check(!NebulaAutoTranslate.isTranslating(0,unsent),"draft/local outgoing message never enters sent translation");
  System.out.println("Translation: provenance, independent sent consent, immutable originals, visible cancellation, two remote slots, model change and retry passed");System.exit(0);
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-incoming-') as directory:
    work = Path(directory)
    (work / 'NebulaAutoTranslate.java').write_text(source, encoding='utf-8')
    (work / 'AutoTranslationCheck.java').write_text(stubs, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, str(work / 'NebulaAutoTranslate.java'), str(work / 'AutoTranslationCheck.java')], check=True)
    subprocess.run(['java', '-cp', directory, 'app.nebulagram.ui.AutoTranslationCheck'], check=True, timeout=30)
