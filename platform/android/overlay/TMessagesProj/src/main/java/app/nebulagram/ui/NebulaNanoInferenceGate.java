package app.nebulagram.ui;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One cancellable AICore inference across every Nebula AI entry point. */
final class NebulaNanoInferenceGate {
    private static final Object LOCK = new Object();
    private static final class Ticket { final boolean interactive; Ticket(boolean value) { interactive = value; } }
    private static final ArrayList<Ticket> waiters = new ArrayList<>();
    private static boolean held;
    private static int interactiveTurns;
    private NebulaNanoInferenceGate() { }
    static boolean tryAcquire() {
        synchronized (LOCK) { if (held || !waiters.isEmpty()) return false; held = true; return true; }
    }
    static void acquire(BooleanSupplier cancelled) throws Exception {
        acquire(cancelled, false);
    }
    static void acquire(BooleanSupplier cancelled, boolean interactive) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        Ticket ticket = new Ticket(interactive);
        synchronized (LOCK) {
            waiters.add(ticket);
            try {
                while (!cancelled.getAsBoolean()) {
                    int next = 0;
                    if (interactiveTurns < 4) {
                        for (int i = 0; i < waiters.size(); i++) if (waiters.get(i).interactive) { next = i; break; }
                    } else {
                        for (int i = 0; i < waiters.size(); i++) if (!waiters.get(i).interactive) { next = i; break; }
                    }
                    if (!held && waiters.get(next) == ticket) {
                        held = true; interactiveTurns = interactive ? interactiveTurns + 1 : 0;
                        return;
                    }
                    if (System.nanoTime() >= deadline) throw new IllegalStateException("GEMINI_NANO_BUSY");
                    LOCK.wait(50);
                }
                throw new InterruptedIOException();
            } finally {
                for (int i = 0; i < waiters.size(); i++) if (waiters.get(i) == ticket) { waiters.remove(i); break; }
                LOCK.notifyAll();
            }
        }
    }
    static void release() {
        synchronized (LOCK) {
            if (!held) throw new IllegalStateException("Nano inference owner missing");
            held = false; LOCK.notifyAll();
        }
    }
}
