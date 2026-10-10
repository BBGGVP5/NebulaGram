"""Exercise local PCM/stream policies and compile production code against actual ML Kit/Android."""
from pathlib import Path
import os, hashlib, subprocess, urllib.request, zipfile

root = Path(__file__).resolve().parent.parent
work = root / 'build/local-audio-check'
work.mkdir(parents=True, exist_ok=True)
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
pure = ['NebulaLocalAudioPolicy.java', 'NebulaPcmResampler.java', 'NebulaPcmFeeder.java', 'NebulaLocalTranscript.java']
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *[str(ui / name) for name in pure], str(root / 'tests/android/LocalAudioCheck.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'LocalAudioCheck'], check=True)

inputs = {
    'speech.aar': ('https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-speech-recognition/1.0.0-alpha1/genai-speech-recognition-1.0.0-alpha1.aar', '380d00a0dd15ea7a6618f57405708e1ec34fc8eb7df5e45cd3fa4190a27bf410'),
    'common.aar': ('https://dl.google.com/dl/android/maven2/com/google/mlkit/genai-common/1.0.0-beta3/genai-common-1.0.0-beta3.aar', 'b88266e12b1704c560be8ecbc9204b6983b87c49cfcf4cb7cbc98a81e4d5a447'),
    'kotlin.jar': ('https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.2.20/kotlin-stdlib-2.2.20.jar', '8836ccffd3585fadda9901244b20d42901d2f3cd581058d8434e2ffabcf3a3e7'),
    'coroutines.jar': ('https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.11.0/kotlinx-coroutines-core-jvm-1.11.0.jar', 'd1d75aa01dffbb4d1c520e67e4c4e7f5f6174718e7cb4632412503f2f0e604fa'),
}
for name, (url, digest) in inputs.items():
    path = work / name
    if not path.exists(): urllib.request.urlretrieve(url, path)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, name
    if name.endswith('.aar'):
        with zipfile.ZipFile(path) as archive: path.with_suffix('.jar').write_bytes(archive.read('classes.jar'))
sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'AppData/Local/Android/Sdk'))))
android = sdk / 'platforms/android-36/android.jar'
assert android.is_file(), 'Android SDK 36 is required for real audio API compilation'
app = work / 'org/telegram/messenger/ApplicationLoader.java'
app.parent.mkdir(parents=True, exist_ok=True)
app.write_text('package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext;}', encoding='utf-8')
stub = work / 'app/nebulagram/ui/NebulaText.java'; stub.parent.mkdir(parents=True, exist_ok=True)
stub.write_text('package app.nebulagram.ui; public class NebulaText {public static String text(String ru,String en){return en;}}', encoding='utf-8')
limits = work / 'app/nebulagram/ui/NebulaAudioProtocol.java'
limits.write_text('package app.nebulagram.ui; public class NebulaAudioProtocol {public static final int MAX_INPUT=14000000;}', encoding='utf-8')
cp = os.pathsep.join(map(str, [android, work / 'speech.jar', work / 'common.jar', work / 'kotlin.jar', work / 'coroutines.jar']))
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', str(work / 'sdk'), str(app), str(stub), str(limits),
    *[str(ui / name) for name in pure + ['NebulaLocalAudioDecoder.java', 'NebulaLocalTranscription.java', 'NebulaNanoInferenceGate.java']]], check=True)
source = (ui / 'NebulaLocalTranscription.java').read_text(encoding='utf-8')
assert 'AudioSource.fromPfd(reader)' in source and 'fromMic(' not in source
assert 'openConnection' not in source and 'NebulaAiSecrets' not in source
assert 'NebulaNanoInferenceGate.acquire' in source and 'if(gate)NebulaNanoInferenceGate.release()' in source
assert 'if(pcm!=null)pcm.delete()' in source and 'if(!fullyFed.get()||!completed.get())' in source
print('Production decoder/recognizer compile against Android 36 and pinned ML Kit Speech alpha1; microphone, cloud fallback and incomplete-result guards retained')


# Execute the exact Java coroutine bridge with the real Kotlin runtime, offline.
blocking = source[source.index('    private static <T> T blocking('):source.index('    public int checkStatus()')]
collect = source[source.index('    private interface Output<T>'):source.index('    public String transcribe(')]
program = r"""
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import kotlin.Unit;
import kotlin.coroutines.*;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
public class LocalBridgeCheck {
 boolean cancelled;
 __BLOCKING__
 __COLLECT__
 public static void main(String[] args)throws Exception{
  int value=blocking(100,(scope,continuation)->Integer.valueOf(3));if(value!=3)throw new AssertionError("Synchronous status return");
  LocalBridgeCheck bridge=new LocalBridgeCheck();ArrayList<String> seen=new ArrayList<>();bridge.collect(FlowKt.flowOf(new String[]{"partial","final"}),100,seen::add);if(!seen.equals(Arrays.asList("partial","final")))throw new AssertionError("Flow callbacks");
  long started=System.nanoTime();try{blocking(40,(scope,continuation)->DelayKt.delay(10000,continuation));throw new AssertionError("Deadline ignored");}catch(TimeoutCancellationException expected){}if(System.nanoTime()-started>2000000000L)throw new AssertionError("Slow timeout cleanup");
  CountDownLatch entered=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();Thread worker=new Thread(()->{try{blocking(10000,(scope,continuation)->{entered.countDown();return DelayKt.delay(10000,continuation);});}catch(InterruptedException expected){interrupted.set(true);}});worker.start();if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError("Worker did not start");worker.interrupt();worker.join(1500);if(worker.isAlive()||!interrupted.get())throw new AssertionError("Coroutine interrupt did not cancel");
  bridge.cancelled=true;try{bridge.collect(FlowKt.flowOf("not emitted"),100,v->{throw new AssertionError("Cancelled value emitted");});throw new AssertionError("Cancel ignored");}catch(CompletionException expected){if(!(expected.getCause() instanceof InterruptedIOException))throw expected;}
  System.out.println("Real coroutine bridge: synchronous status, Flow delivery, deadline and interrupt/cancel cleanup passed");
 }
}
""".replace('__BLOCKING__',blocking).replace('__COLLECT__',collect)
bridge = work / 'LocalBridgeCheck.java'; bridge.write_text(program,encoding='utf-8')
runtime_cp = os.pathsep.join(map(str,[work,work/'kotlin.jar',work/'coroutines.jar']))
subprocess.run(['javac','-encoding','UTF-8','-cp',runtime_cp,'-d',str(work),str(bridge)],check=True)
subprocess.run(['java','-cp',runtime_cp,'LocalBridgeCheck'],check=True)
