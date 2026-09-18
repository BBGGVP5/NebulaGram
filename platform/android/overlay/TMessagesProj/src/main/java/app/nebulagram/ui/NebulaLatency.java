package app.nebulagram.ui;

/** Presentation only. Never write these estimates back to the core or its cache. */
public final class NebulaLatency {
    private NebulaLatency() {}

    public static boolean isMeasured(int rawMs, long checkedAt) {
        return rawMs > 0 || (rawMs == 0 && checkedAt > 0);
    }

    public static long displayMillis(int rawMs, String method) {
        return rawMs >= 0 && "nimbo".equals(method) ? Math.round(rawMs / 3.3) : rawMs;
    }

    public static String format(int rawMs, String method, long checkedAt,
                                String unit, String unknown, String failed) {
        if (rawMs < 0) return failed;
        if (!isMeasured(rawMs, checkedAt)) return unknown;
        return ("nimbo".equals(method) ? "≈" : "") + displayMillis(rawMs, method) + " " + unit;
    }
}
