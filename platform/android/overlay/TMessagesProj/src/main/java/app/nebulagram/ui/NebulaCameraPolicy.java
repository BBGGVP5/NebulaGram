package app.nebulagram.ui;

/** Device-independent capture decisions. Zero means leave the native default. */
public final class NebulaCameraPolicy {
    private NebulaCameraPolicy() { }
    public static long audioDurationUs(long bytes,int rate,int channels) {
        if(bytes<=0||rate<=0||channels<1||channels>2)return 0;
        return bytes*1_000_000L/rate/(2*channels);
    }
    public static int quality(int requested, int[] supported) {
        if (requested <= 0 || supported == null) return 0;
        int best = 0, smallest = Integer.MAX_VALUE;
        for (int size : supported) if (size > 0) {
            smallest = Math.min(smallest, size);
            if (size <= requested) best = Math.max(best, size);
        }
        return best > 0 ? best : smallest == Integer.MAX_VALUE ? 0 : smallest;
    }
    /** OIS and EIS are not combined: Android devices may reject or override that request. */
    public static int stabilization(boolean ois, boolean eis, boolean hasOis, boolean hasEis) {
        return ois && hasOis ? 1 : eis && hasEis ? 2 : 0;
    }
    public static float startZoom(boolean wide, boolean front, float min, float max) {
        if (!Float.isFinite(min) || !Float.isFinite(max) || min <= 0 || max < min) return 1f;
        return Math.max(min, Math.min(max, wide && !front ? Math.min(1f, min) : 1f));
    }
    public static float switchBlur(boolean switching, float incomingAlpha) {
        if (!switching || !Float.isFinite(incomingAlpha)) return 0f;
        float remaining = 1f - Math.max(0f, Math.min(1f, incomingAlpha));
        return remaining * remaining * (3f - 2f * remaining);
    }
}
