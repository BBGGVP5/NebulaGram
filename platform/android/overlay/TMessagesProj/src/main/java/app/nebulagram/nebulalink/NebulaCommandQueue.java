package app.nebulagram.nebulalink;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A slow latency probe must not block disconnect, settings or server selection. */
public final class NebulaCommandQueue {
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final ExecutorService probes = Executors.newSingleThreadExecutor();

    public void execute(Runnable command) { commands.execute(command); }

    public void execute(String method, Runnable command) {
        // Dispatch through the control queue first: core initialization already queued there
        // must finish before probes can enter Go. Mutations remain strictly FIFO.
        commands.execute(() -> {
            if ("probe.servers".equals(method) || "probe.url".equals(method)) probes.execute(command);
            else command.run();
        });
    }

    void shutdownForTests() { commands.shutdownNow(); probes.shutdownNow(); }
}
