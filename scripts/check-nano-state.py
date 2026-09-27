"""Exercise production Nano error handling, future cancellation and stale-result guards."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
work = root / 'build/nano-state-check'
work.mkdir(parents=True, exist_ok=True)
nano = (ui / 'NebulaNanoAi.java').read_text(encoding='utf-8')
fragment = (ui / 'NebulaAiFragment.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        if source[end] == '{': depth += 1
        elif source[end] == '}': depth -= 1
        end += 1
    return source[start:end]

# No Android service, request text, credentials or network are involved.
source = '''import java.util.concurrent.*;
public class NanoStateCheck {
    static class NebulaText { static String text(String ru, String en) { return en; } }
    static class GenAiException extends Exception {
        final int code;
        GenAiException(String message, int code) { super(message); this.code = code; }
        int getErrorCode() { return code; }
        static class ErrorCode {
            static final int BUSY=1, BACKGROUND_USE_BLOCKED=2, NOT_ENOUGH_DISK_SPACE=3,
                NEEDS_SYSTEM_UPDATE=4, AICORE_INCOMPATIBLE=5, PER_APP_BATTERY_USE_QUOTA_EXCEEDED=6,
                NOT_SUPPORTED=7, NOT_AVAILABLE=8;
        }
    }
    static void expect(boolean value, String name) { if (!value) throw new AssertionError(name); }
''' + method(nano, '    public static String errorText(') + '\n' + method(nano, '    private static <T> T await(') + '''
    static class NebulaAiClient { static final int NANO=4; }
    static class AndroidUtilities { static void cancelRunOnUIThread(Runnable r){} }
    Runnable nanoPoll=()->{}; long nanoTotal;
    boolean destroyed, nanoDownloading; int nanoRequest=1, provider=4; Thread nanoWorker;
    Object activity=new Object(); Object getParentActivity() { return activity; }
''' + method(fragment, '    private boolean currentNano(') + '\n' + method(fragment, '    private void invalidateNanoCheck(') + '''
    public static void main(String[] args) throws Exception {
        expect(errorText(new ExecutionException(new TimeoutException())).contains("timed out"), "timeout is not unsupported");
        expect(errorText(new IllegalStateException("GEMINI_NANO_BUSY")).contains("previous request"), "parallel inference explains wait");
        expect(errorText(new GenAiException("FEATURE_NOT_FOUND 606", 0)).contains("(606)"), "AICore provisioning");
        String[] messages={"busy", "background", "storage", "Update", "Update", "limit", "current configuration", "not ready"};
        for (int i=0;i<messages.length;i++) {
            String result=errorText(new ExecutionException(new GenAiException("private request SECRET", i+1)));
            expect(result.contains(messages[i]), "reason " + i);
            expect(!result.contains("SECRET"), "no raw service text");
        }
        expect(errorText(new Exception("SECRET")).contains("does not mean"), "unknown service failure");
        expect(!errorText(new Exception("SECRET")).contains("SECRET"), "unknown error redaction");
        FutureTask<Integer> ready=new FutureTask<>(()->42); ready.run();
        expect(await(ready,1,TimeUnit.SECONDS)==42, "successful result");
        FutureTask<Integer> stalled=new FutureTask<>(()->1);
        try { await(stalled,1,TimeUnit.MILLISECONDS); throw new AssertionError("timeout expected"); }
        catch(TimeoutException expected) { expect(stalled.isCancelled(), "cancel timed out check"); }
        FutureTask<Integer> interrupted=new FutureTask<>(()->1);
        Thread.currentThread().interrupt();
        try { await(interrupted,1,TimeUnit.SECONDS); throw new AssertionError("interrupt expected"); }
        catch(InterruptedException expected) {
            expect(interrupted.isCancelled(), "cancel interrupted check");
            expect(Thread.interrupted(), "restore interruption flag");
        }
        NanoStateCheck state=new NanoStateCheck();
        expect(state.currentNano(1), "current model");
        state.invalidateNanoCheck();
        expect(!state.currentNano(1) && state.currentNano(2), "old selection cannot overwrite new");
        state.provider=0; expect(!state.currentNano(2), "provider changed");
        state.provider=4; state.destroyed=true; expect(!state.currentNano(2), "fragment destroyed");
        state.destroyed=false; state.activity=null; expect(!state.currentNano(2), "fragment detached");
        System.out.println("Nano state: error reasons, redaction, timeout/interrupt cancellation and stale results passed");
    }
}
'''
path = work / 'NanoStateCheck.java'
path.write_text(source, encoding='utf-8')
java_home = os.environ.get('JAVA_HOME')
def tool(name):
    return str(Path(java_home) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))) if java_home else name
subprocess.run([tool('javac'), '-J-Xmx256m', '-encoding', 'UTF-8', '-d', str(work), str(path)], check=True)
subprocess.run([tool('java'), '-Xmx128m', '-cp', str(work), 'NanoStateCheck'], check=True)
