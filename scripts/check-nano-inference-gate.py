"""Verify that independent UI surfaces cannot start concurrent Nano sessions."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / "platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui"
source = (ui / "NebulaNanoAi.java").read_text(encoding="utf-8")
assert "NebulaNanoInferenceGate.acquire(cancelled)" in source
assert "releaseSession(reusable);" in source
assert "finally { NebulaNanoInferenceGate.release(); }" in source

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
        check(NebulaNanoInferenceGate.tryAcquire());
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Thread waiting = new Thread(() -> {
            try { NebulaNanoInferenceGate.acquire(cancelled::get); error.set(new AssertionError("cancelled waiter entered")); NebulaNanoInferenceGate.release(); }
            catch (java.io.InterruptedIOException expected) { }
            catch (Throwable e) { error.set(e); }
        });
        waiting.start(); cancelled.set(true); waiting.join(2000);
        check(!waiting.isAlive() && error.get() == null);
        check(!NebulaNanoInferenceGate.tryAcquire()); // cancelling the waiter must not release the owner's slot
        NebulaNanoInferenceGate.release();
        java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] callers = new Thread[12];
        for (int i=0;i<callers.length;i++) {
            callers[i] = new Thread(() -> {
                try {
                    NebulaNanoInferenceGate.acquire(() -> false);
                    try { check(active.incrementAndGet() == 1); Thread.yield(); check(active.decrementAndGet() == 0); }
                    finally { NebulaNanoInferenceGate.release(); }
                } catch (Throwable e) { error.set(e); }
            }); callers[i].start();
        }
        for (Thread caller : callers) { caller.join(2000); check(!caller.isAlive()); }
        check(error.get() == null);
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
