"""Run the production ML Kit adapter against controllable asynchronous tasks."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sources = {
'com/google/android/gms/tasks/Task.java': '''package com.google.android.gms.tasks;
import java.util.concurrent.*;import java.util.function.Consumer;
public class Task<T> {
 public final CompletableFuture<T> future=new CompletableFuture<>();
 public boolean isComplete(){return future.isDone();}public boolean isSuccessful(){return isComplete()&&!future.isCompletedExceptionally();}
 public Task<T> addOnCompleteListener(Executor e,Consumer<Task<T>> c){future.whenCompleteAsync((v,x)->c.accept(this),e);return this;}
 public static <T> Task<T> done(T value){Task<T> t=new Task<>();t.future.complete(value);return t;}
}''',
'com/google/android/gms/tasks/Tasks.java': '''package com.google.android.gms.tasks;import java.util.concurrent.*;public class Tasks {public static <T>T await(Task<T> t,long time,TimeUnit unit)throws Exception{return t.future.get(time,unit);}}''',
'com/google/mlkit/common/model/DownloadConditions.java': '''package com.google.mlkit.common.model;public class DownloadConditions {public static class Builder {public Builder requireWifi(){return this;}public DownloadConditions build(){return new DownloadConditions();}}}''',
'com/google/mlkit/nl/translate/TranslateLanguage.java': '''package com.google.mlkit.nl.translate;public class TranslateLanguage {public static String fromLanguageTag(String t){return t==null||t.equals("und")||t.equals("xx")?null:t;}}''',
'com/google/mlkit/nl/translate/TranslatorOptions.java': '''package com.google.mlkit.nl.translate;public class TranslatorOptions {public String from,to;public static class Builder {TranslatorOptions o=new TranslatorOptions();public Builder setSourceLanguage(String s){o.from=s;return this;}public Builder setTargetLanguage(String s){o.to=s;return this;}public TranslatorOptions build(){return o;}}}''',
'com/google/mlkit/nl/translate/Translator.java': '''package com.google.mlkit.nl.translate;
import com.google.android.gms.tasks.Task;import com.google.mlkit.common.model.DownloadConditions;
public class Translator {
 public Task<Void> preparation=Task.done(null);public Task<String> next;public volatile int closes,calls;public final String key;
 public Translator(String key){this.key=key;}
 public Task<Void> downloadModelIfNeeded(DownloadConditions d){return preparation;}
 public Task<String> translate(String s){calls++;return next!=null?next:Task.done(s);}
 public void close(){closes++;}
}''',
'com/google/mlkit/nl/translate/Translation.java': '''package com.google.mlkit.nl.translate;import java.util.*;public class Translation {public static final Map<String,Translator> clients=new HashMap<>();public static Translator getClient(TranslatorOptions o){return clients.computeIfAbsent(o.from+":"+o.to,Translator::new);}}''',
'com/google/mlkit/nl/languageid/LanguageIdentifier.java': '''package com.google.mlkit.nl.languageid;import com.google.android.gms.tasks.Task;public class LanguageIdentifier {public int closes;public Task<String> identifyLanguage(String text){return LanguageIdentification.pending!=null?LanguageIdentification.pending:Task.done(LanguageIdentification.language);}public void close(){closes++;}}''',
'com/google/mlkit/nl/languageid/LanguageIdentification.java': '''package com.google.mlkit.nl.languageid;import com.google.android.gms.tasks.Task;public class LanguageIdentification {public static String language="en";public static Task<String> pending;public static volatile LanguageIdentifier last;public static LanguageIdentifier getClient(){return last=new LanguageIdentifier();}}''',
'app/nebulagram/ui/NebulaText.java': '''package app.nebulagram.ui;public class NebulaText {public static String text(String ru,String en){return en;}}''',
'LocalTranslationCheck.java': r'''
import app.nebulagram.ui.NebulaLocalTranslation;
import com.google.android.gms.tasks.*;import com.google.mlkit.nl.translate.*;import com.google.mlkit.nl.languageid.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.lang.reflect.*;
public class LocalTranslationCheck {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Object field(Object object,String name)throws Exception{Field f=object instanceof Class?((Class<?>)object).getDeclaredField(name):object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object instanceof Class?null:object);}
 static Map<?,?> sessions()throws Exception{return (Map<?,?>)field(NebulaLocalTranslation.class,"sessions");}
 static int leases(String key)throws Exception{return (int)field(sessions().get(key),"leases");}
 static void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);check(condition.getAsBoolean(),"async callback did not finish");}
 static void barrier()throws Exception{((ScheduledExecutorService)field(NebulaLocalTranslation.class,"cleanup")).submit(()->{}).get();}
 static String run(String to)throws Exception{return NebulaLocalTranslation.translate("hello",to,()->false,p->{});}
 public static void main(String[] args)throws Exception {
  check(run("ru").equals("hello"),"initial request");
  Object sweep=field(NebulaLocalTranslation.class,"idleSweep");
  for(int i=0;i<10000;i++)run("ru");
  check(sweep==field(NebulaLocalTranslation.class,"idleSweep"),"one timer per request instead of coalescing");
  Translator client=Translation.clients.get("en:ru");client.next=new Task<>();
  AtomicBoolean cancel=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();int calls=client.calls;
  Thread worker=new Thread(()->{try{NebulaLocalTranslation.translate("hello","ru",cancel::get,p->{});}catch(Throwable e){failure.set(e);}});worker.start();
  waitFor(()->client.calls>calls);cancel.set(true);worker.join(2000);
  check(failure.get() instanceof java.io.InterruptedIOException,"cancel must return promptly");
  check(leases("en:ru")==1&&client.closes==0,"cancel released a running inference");
  Task<String> pending=client.next;client.next=null;run("ru");
  check(leases("en:ru")==1,"shared inference lost its lease");
  // LRU pressure must not close the cancelled task's translator either.
  for(String target:new String[]{"fr","de","it","es","pt","ja"})run(target);
  check(client.closes==0,"LRU evicted in-flight cancelled inference");
  pending.future.complete("done");barrier();
  check(leases("en:ru")==0,"completed task did not release lease");
  Object idle=sessions().get("en:ru");Field lastUse=idle.getClass().getDeclaredField("lastUse");lastUse.setAccessible(true);lastUse.setLong(idle,System.nanoTime()-TimeUnit.SECONDS.toNanos(61));
  Method closeIdle=NebulaLocalTranslation.class.getDeclaredMethod("closeIdle");closeIdle.setAccessible(true);closeIdle.invoke(null);
  check(client.closes==1,"completed unreferenced LRU session not reclaimed");
  LanguageIdentification.pending=new Task<>();cancel.set(false);failure.set(null);
  Thread idWorker=new Thread(()->{try{NebulaLocalTranslation.translate("hello","ru",cancel::get,p->{});}catch(Throwable e){failure.set(e);}});
  LanguageIdentifier before=LanguageIdentification.last;idWorker.start();waitFor(()->LanguageIdentification.last!=before);
  LanguageIdentifier identifier=LanguageIdentification.last;cancel.set(true);idWorker.join(2000);
  check(identifier.closes==0,"identifier closed while identifyLanguage still running");
  LanguageIdentification.pending.future.complete("en");barrier();check(identifier.closes==1,"identifier leaked after cancellation");LanguageIdentification.pending=null;
  LanguageIdentification.language="und";
  for(String text:new String[]{"Hola amigo","Привіт друже"}) {
   try{NebulaLocalTranslation.translate(text,"de",()->false,p->{});throw new AssertionError("alphabet guessed as language");}
   catch(IllegalArgumentException e){check(e.getMessage().equals("LOCAL_LANGUAGE_UNKNOWN"),"unknown language code");}
  }
  check(NebulaLocalTranslation.translate("123!","de",()->false,p->{}).equals("123!"),"nonlinguistic text changed");
  LanguageIdentification.language="en";
  Translator broken=new Translator("en:ko");broken.preparation=new Task<>();broken.preparation.future.completeExceptionally(new Exception("offline"));Translation.clients.put("en:ko",broken);
  try{run("ko");throw new AssertionError("failed model accepted");}catch(Exception e){check(NebulaLocalTranslation.errorText(e).contains("Language pack"),"download phase lost");}
  check(broken.closes==1&&!sessions().containsKey("en:ko"),"failed model session leaked");
  check(!NebulaLocalTranslation.errorText(new Exception("inference")).contains("Language pack"),"inference misreported as missing model");
  System.out.println("Local translation: 10,000 releases/one timer, cancellation/shared leases/LRU, identifier lifetime, language detection and error phases passed");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-local-translation-') as directory:
    path = Path(directory)
    for name, content in sources.items():
        file = path / name; file.parent.mkdir(parents=True, exist_ok=True); file.write_text(content, encoding='utf-8')
    production = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaLocalTranslation.java'
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, *map(str, path.rglob('*.java')), str(production)], check=True)
    subprocess.run(['java', '-cp', directory, 'LocalTranslationCheck'], check=True)
