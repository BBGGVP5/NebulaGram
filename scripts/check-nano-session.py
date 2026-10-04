"""Run production Nano session/generation methods with deterministic service futures."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source = (ui / 'NebulaNanoAi.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    end, depth = brace + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

fields = source[source.index('    private static Session warmSession;'):source.index('    private NebulaNanoAi()')]
fields = re.sub(r'java.util.concurrent.Executors.newSingleThreadScheduledExecutor\(r -> \{.*?\}\)', 'new FakeScheduler()', fields, flags=re.S)
methods = '\n'.join(method(s) for s in [
    '    private static Session acquireSession(', '    private static void closeWarmSession(',
    '    private static void releaseSession(',
    '    private static <T> T await(java.util.concurrent.Future<T> future, long timeout, TimeUnit unit,',
    '    public static String generate(String instructions, String input, java.util.function.BooleanSupplier cancelled)',
    '    public static String generate(String instructions, String input, java.util.function.BooleanSupplier cancelled, boolean interactive)'])
program = r'''package app.nebulagram.ui;
import java.util.*;
import java.util.concurrent.*;
public class NanoSessionCheck {
 FIELDS
 METHODS
 static boolean supportedByOs(){ return true; }
 static class Prefs { boolean preview,fast; boolean getBoolean(String k,boolean d){return k.equals("nano_preview")?preview:fast;} }
 static final Prefs preferences=new Prefs(); static Prefs prefs(){return preferences;}
 static class FeatureStatus { static final int AVAILABLE=1,DOWNLOADABLE=2,DOWNLOADING=3; }
 static class GenerateContentResponse { List<GenerateContentResponse> getCandidates(){return List.of(this);} String getText(){return "translated";} }
 static class Client {
  static int checks,requests; static boolean fail,cancel; static CompletableFuture<GenerateContentResponse> pending;
  CompletableFuture<Integer> checkStatus(){checks++;return CompletableFuture.completedFuture(FeatureStatus.AVAILABLE);}
  CompletableFuture<GenerateContentResponse> generateContent(String p){requests++; pending=new CompletableFuture<>();
   if(fail)pending.completeExceptionally(new IllegalStateException("test failure")); else if(!cancel)pending.complete(new GenerateContentResponse());
   return pending;}
 }
 static class Session { static int opened,closed; final Client client=new Client(); Session(boolean p,boolean f){opened++;} void close(){closed++;} }
 static class Dummy extends FutureTask<Void> implements ScheduledFuture<Void> {
  Dummy(){super(()->null);}public long getDelay(TimeUnit u){return 0;}public int compareTo(Delayed d){return 0;}
 }
 static class FakeScheduler extends ScheduledThreadPoolExecutor {
  final List<Runnable> callbacks=new ArrayList<>(); FakeScheduler(){super(1);}
  public ScheduledFuture<?> schedule(Runnable r,long delay,TimeUnit unit){
   check(unit.toSeconds(delay)==30,"bounded idle retention");callbacks.add(r);return new Dummy();}
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  FakeScheduler timer=(FakeScheduler)idleWorker;
  check(generate("translate","one",()->false).equals("translated"),"first generation");
  Runnable old=timer.callbacks.get(0);
  generate("translate","two",()->false);
  check(Session.opened==1 && Session.closed==0 && Client.checks==1 && Client.requests==2,"reuse client and availability across adjacent prompts");
  old.run(); check(Session.closed==0,"late idle callback cannot close a new lease");
  preferences.fast=true; generate("translate","three",()->false);
  check(Session.opened==2 && Session.closed==1 && Client.checks==2,"configuration change replaces and validates model");
  Client.fail=true;
  try {generate("translate","four",()->false);throw new AssertionError("failure expected");}catch(ExecutionException expected){}
  check(warmSession==null && Session.closed==2,"discard failed engine");
  check(NebulaNanoInferenceGate.tryAcquire(),"failure releases slot");NebulaNanoInferenceGate.release();
  Client.fail=false;generate("translate","five",()->false);
  Runnable idle=timer.callbacks.get(timer.callbacks.size()-1);
  check(NebulaNanoInferenceGate.tryAcquire(),"idle race owner");idle.run();
  check(warmSession!=null,"never close active model");NebulaNanoInferenceGate.release();
  idle.run();check(warmSession==null && Session.closed==3,"idle cleanup");
  Client.cancel=true;
  try {generate("translate","six",()->Client.pending!=null && !Client.pending.isDone());throw new AssertionError("cancel expected");}
  catch(java.io.InterruptedIOException expected){}
  check(Client.pending.isCancelled() && warmSession==null && Session.closed==4,"cancel future and discard engine");
  check(NebulaNanoInferenceGate.tryAcquire(),"cancellation releases slot");NebulaNanoInferenceGate.release();
  System.out.println("Nano session: model/status reuse, configuration, failure, cancellation and idle races passed");
 }
}
'''.replace('FIELDS', fields).replace('METHODS', methods)
with tempfile.TemporaryDirectory(prefix='nebula-nano-session-') as folder:
    work = Path(folder)
    test = work / 'NanoSessionCheck.java'
    test.write_text(program, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(ui / 'NebulaNanoInferenceGate.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', folder, 'app.nebulagram.ui.NanoSessionCheck'], check=True, timeout=15)
