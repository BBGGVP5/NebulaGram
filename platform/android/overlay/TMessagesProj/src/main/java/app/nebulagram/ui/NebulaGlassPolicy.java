package app.nebulagram.ui;

/** Pure policy: quality changes never overwrite the user's appearance values. */
public final class NebulaGlassPolicy {
    private NebulaGlassPolicy() { }
    public static boolean reduced(int mode, boolean powerSave, boolean thermalHot, boolean lowRam) {
        return mode == 2 || (mode != 1 && (powerSave || thermalHot || lowRam));
    }
}
