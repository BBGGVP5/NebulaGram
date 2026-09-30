package app.nebulagram.ui;

/** Pure decisions shared by native behavior hooks; never mutates messages. */
public final class NebulaFeaturePolicy {
    private NebulaFeaturePolicy() { }
    public static boolean silenceUnknown(boolean enabled, long dialog, boolean contact, boolean service) {
        return enabled && dialog > 0 && !contact && !service;
    }
    public static boolean ignoreMention(boolean enabled, String selection, long dialog) {
        if (!enabled) return false;
        if ("*".equals(selection)) return true;
        if (selection == null) return false;
        for (String id : selection.split(",")) if (id.equals(Long.toString(dialog))) return true;
        return false;
    }
    public static int quoteEnd(String text, int limit) {
        int end = Math.min(text.length(), Math.max(0, limit));
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return end;
    }
    public static float fade(float progress, boolean smooth) {
        if (!smooth) return progress;
        float p = Math.max(0f, Math.min(1f, progress));
        return p * p * (3f - 2f * p);
    }
}
