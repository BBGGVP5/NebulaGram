package app.nebulagram.ui;

/** Pure state policy shared by the real home button and its four previews. */
public final class NebulaLinkShortcutState {
    public static final int OFF = 0, CONNECTING = 1, ON = 2, ERROR = 3;
    private NebulaLinkShortcutState() { }
    public static int resolve(String state, boolean routed, boolean pending, boolean failed) {
        if (pending) return CONNECTING;
        if (failed || "failed".equals(state)) return ERROR;
        if ("connecting".equals(state)) return CONNECTING;
        return "connected".equals(state) && routed ? ON : OFF;
    }
    public static boolean shouldStop(String state) { return "connected".equals(state) || "connecting".equals(state); }
}
