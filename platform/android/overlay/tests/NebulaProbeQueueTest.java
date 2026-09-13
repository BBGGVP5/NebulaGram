package app.nebulagram.nebulalink;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NebulaProbeQueueTest {
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("queue blocked"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    public static void main(String[] args) {
        NebulaCommandQueue queue = new NebulaCommandQueue();
        CountDownLatch initialized = new CountDownLatch(1), started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1), control = new CountDownLatch(3), second = new CountDownLatch(1);
        AtomicBoolean tooEarly = new AtomicBoolean();
        try {
            queue.execute(() -> initialized.countDown());
            queue.execute("probe.servers", () -> {
                tooEarly.set(initialized.getCount() != 0);
                started.countDown(); await(release);
            });
            await(started);
            queue.execute("probe.url", () -> second.countDown());
            queue.execute("server.select", () -> control.countDown());
            queue.execute("tunnel.stop", () -> control.countDown());
            queue.execute("probe.cancel", () -> control.countDown());
            await(control);
            if (tooEarly.get() || second.getCount() != 1) throw new AssertionError("initialization or probe serialization");
            release.countDown(); await(second);
            System.out.println("PASS: initialization, serialized probes and independent select/stop/cancel");
        } finally { release.countDown(); queue.shutdownForTests(); }
    }
}
