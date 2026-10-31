package app.nebulagram.ui;

public final class NebulaSelectionPolicy {
    private NebulaSelectionPolicy() { }
    public static boolean full(int count, boolean report, boolean unlimited) { return count >= 100 && (report || !unlimited); }
    public static boolean full(int count, boolean report) { return full(count, report, NebulaFeatureSettings.enabled("unlimited_selection")); }
}
