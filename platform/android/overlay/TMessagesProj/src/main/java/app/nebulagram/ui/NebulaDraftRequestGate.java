package app.nebulagram.ui;

/** Identity includes text, account/chat, target language and connection. */
public final class NebulaDraftRequestGate {
    private long revision;
    private String identity;
    private String suppressedIdentity;
    public synchronized long begin(String value) {
        if (value.equals(identity) || value.equals(suppressedIdentity)) return 0;
        suppressedIdentity = null; identity = value;
        return ++revision;
    }
    public synchronized boolean accepts(long value) { return identity != null && value != 0 && value == revision; }
    public synchronized void suppress(String value) { cancel(); suppressedIdentity = value; }
    public synchronized void cancel() { revision++; identity = null; }
}
