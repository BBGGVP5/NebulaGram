package app.nebulagram.ui;

import java.util.ArrayList;

/**
 * Zoom scale of the Camera2 round-video engine (InstantCameraView2). The camera controller publishes
 * the ratio it really starts from (below 1× with "Start with ultrawide"), so the slider labels match
 * the picture. With "lens_stops" the slider snaps to the device's optical lenses (0.6×, 1×, 3×, as
 * NebulaCameraZoom finds them by focal length) instead of the single 1× mark.
 */
public final class NebulaRoundZoom {
    private NebulaRoundZoom() { }
    private static volatile float rearMinimum = 1f, frontMinimum = 1f;
    private static volatile Runnable listener;
    private static final NebulaZoomCapabilities[] catalogs = new NebulaZoomCapabilities[2];

    /** Camera thread: the lowest ratio progress 0 maps to. */
    public static void publish(boolean front, float minimum) {
        if (!Float.isFinite(minimum) || minimum <= 0f || minimum > 1f) minimum = 1f;
        float previous = front ? frontMinimum : rearMinimum;
        if (Math.abs(previous - minimum) < .001f) return;
        if (front) frontMinimum = minimum; else rearMinimum = minimum;
        Runnable target = listener;
        if (target != null) org.telegram.messenger.AndroidUtilities.runOnUIThread(target);
    }
    /** UI thread: called again when the published minimum changes; null detaches. */
    public static void listen(Runnable target) { listener = target; }
    public static float minimum(boolean front) { return front ? frontMinimum : rearMinimum; }

    /** Slider value for controller progress 0..1. */
    public static float factor(float progress, float minimum, float maximum) {
        return minimum + Math.max(0f, Math.min(1f, progress)) * Math.max(0f, maximum - minimum);
    }
    /** Controller progress 0..1 for a slider value. */
    public static float progress(float factor, float minimum, float maximum) {
        if (maximum <= minimum) return 0f;
        return Math.max(0f, Math.min(1f, (factor - minimum) / (maximum - minimum)));
    }

    public static boolean lensStops() { return NebulaCameraSettings.enabled("lens_stops"); }

    /** Marks on the slider: optical lenses inside the range when enabled, else the old single 1×. */
    public static float[] stops(boolean front, float minimum, float maximum) {
        float[] optical = null;
        if (lensStops()) {
            int index = front ? 0 : 1;
            NebulaZoomCapabilities catalog = catalogs[index];
            if (catalog == null) {
                try { catalog = catalogs[index] = NebulaCameraZoom.discover(front); } catch (RuntimeException ignored) { }
            }
            optical = catalog == null ? null : catalog.stops;
        }
        return inRange(optical, minimum, maximum);
    }
    static float[] inRange(float[] optical, float minimum, float maximum) {
        ArrayList<Float> out = new ArrayList<>();
        if (optical != null) for (float stop : optical)
            if (Float.isFinite(stop) && stop >= minimum - .01f && stop <= maximum + .01f && !near(out, stop)) out.add(stop);
        if (minimum <= 1.01f && maximum >= .99f && !near(out, 1f)) out.add(1f);
        java.util.Collections.sort(out);
        float[] result = new float[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }
    private static boolean near(ArrayList<Float> values, float value) {
        for (float v : values) if (Math.abs(v - value) < .05f) return true;
        return false;
    }
}
