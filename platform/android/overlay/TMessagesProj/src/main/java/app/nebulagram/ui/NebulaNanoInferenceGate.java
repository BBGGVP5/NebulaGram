package app.nebulagram.ui;

import java.io.InterruptedIOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One cancellable AICore inference across every Nebula AI entry point. */
final class NebulaNanoInferenceGate {
    private static final Semaphore SLOT = new Semaphore(1, true);
    private NebulaNanoInferenceGate() { }
    static boolean tryAcquire() { return SLOT.tryAcquire(); }
    static void acquire(BooleanSupplier cancelled) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        while (!cancelled.getAsBoolean()) {
            if (SLOT.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean()) { SLOT.release(); throw new InterruptedIOException(); }
                return;
            }
            if (System.nanoTime() >= deadline) throw new IllegalStateException("GEMINI_NANO_BUSY");
        }
        throw new InterruptedIOException();
    }
    static void release() { SLOT.release(); }
}
