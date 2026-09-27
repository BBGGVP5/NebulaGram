"""Verify that independent UI surfaces cannot start concurrent Nano sessions."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / "platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui"
source = (ui / "NebulaNanoAi.java").read_text(encoding="utf-8")
assert "NebulaNanoInferenceGate.tryAcquire()" in source
assert "finally {\n            NebulaNanoInferenceGate.release();" in source

program = """package app.nebulagram.ui;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public class GateCheck {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = new Thread(() -> {
            check(NebulaNanoInferenceGate.tryAcquire());
            entered.countDown();
            try { check(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            finally { NebulaNanoInferenceGate.release(); }
        });
        first.start();
        check(entered.await(5, TimeUnit.SECONDS));
        check(!NebulaNanoInferenceGate.tryAcquire());
        release.countDown();
        first.join(5000);
        check(!first.isAlive());
        check(NebulaNanoInferenceGate.tryAcquire());
        NebulaNanoInferenceGate.release();
        System.out.println("Nano inference gate serialized independent callers and recovered after release");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="nebula-nano-gate-") as folder:
    work = Path(folder)
    test = work / "GateCheck.java"
    test.write_text(program, encoding="utf-8")
    java_home = os.environ.get("JAVA_HOME")
    exe = ".exe" if os.name == "nt" else ""
    javac = str(Path(java_home) / "bin" / ("javac" + exe)) if java_home else "javac"
    java = str(Path(java_home) / "bin" / ("java" + exe)) if java_home else "java"
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(work),
                    str(ui / "NebulaNanoInferenceGate.java"), str(test)], check=True)
    subprocess.run([java, "-cp", str(work), "app.nebulagram.ui.GateCheck"], check=True)
