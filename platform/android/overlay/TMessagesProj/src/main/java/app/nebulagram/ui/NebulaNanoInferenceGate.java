package app.nebulagram.ui;

import java.util.concurrent.atomic.AtomicBoolean;

/** One AICore inference across every Nebula AI entry point in this process. */
final class NebulaNanoInferenceGate {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private NebulaNanoInferenceGate() { }

    static boolean tryAcquire() { return RUNNING.compareAndSet(false, true); }
    static void release() { RUNNING.set(false); }
}
