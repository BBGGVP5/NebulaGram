package app.nebulagram.ui;

/** Identity includes text, account/chat, target language and connection. */
public final class NebulaDraftRequestGate {
    private long revision;
    private String identity;
    public synchronized long begin(String value) {
        if (value.equals(identity)) return 0;
        identity = value;
        return ++revision;
    }
    public synchronized boolean accepts(long value) { return identity != null && value != 0 && value == revision; }
    public synchronized void cancel() { revision++; identity = null; }
}
