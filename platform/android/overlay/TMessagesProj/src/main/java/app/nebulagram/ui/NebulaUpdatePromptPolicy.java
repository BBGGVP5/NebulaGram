package app.nebulagram.ui;

/** Pure wall-clock policy. The caller additionally checks account, foreground and lock state. */
public final class NebulaUpdatePromptPolicy {
    private NebulaUpdatePromptPolicy() { }
    public static final long DAY = 24 * 60 * 60 * 1000L;
    public static boolean shouldOffer(int code, int previousCode, long shownAt, long now) {
        if (code <= 0) return false;
        if (code != previousCode || shownAt <= 0) return true;
        long elapsed = now - shownAt;
        return elapsed < 0 || elapsed >= DAY;
    }
}
