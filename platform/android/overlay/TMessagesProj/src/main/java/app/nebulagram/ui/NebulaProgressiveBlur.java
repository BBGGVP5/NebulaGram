package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;

/**
 * "Header blur": messages passing under the chat header blur progressively, strongest at the top
 * edge and fading out towards the content, instead of the plain edge. Android 12+ only (RenderEffect);
 * the choice "Off" (default) leaves the list's drawing untouched.
 * The list draws its children once into a RenderNode; that node is drawn normally and, in the band
 * under the header, a blurred copy masked by a vertical gradient is drawn over it.
 */
public final class NebulaProgressiveBlur {
    public static final String KEY = "header_blur_style";
    public static final int OFF = 0, SOFT = 1, STRONG = 2;
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Object content, blurred;
    private int maskHeight = -1, maskStyle = -1;
    private float radius = -1;

    public static int style() {
        try {
            int value = org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getInt(KEY, OFF);
            return value < OFF || value > STRONG ? OFF : value;
        } catch (Throwable e) {
            return OFF;
        }
    }
    public static void setStyle(int value) {
        org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).edit().putInt(KEY, Math.max(OFF, Math.min(STRONG, value))).apply();
    }
    public static boolean supported() { return Build.VERSION.SDK_INT >= 31; }
    public static String[] titles() {
        return new String[]{NebulaText.text("Выключено", "Off"), NebulaText.text("Мягкое", "Soft"), NebulaText.text("Сильное", "Strong")};
    }

    /** Blur radius in dp and the band height under the header in dp for a style. */
    static float radiusDp(int style) { return style == STRONG ? 18f : style == SOFT ? 9f : 0f; }
    static float bandDp(int style) { return style == STRONG ? 56f : style == SOFT ? 40f : 0f; }
    /** Mask alpha at a point of the band, 0 at the header edge = full blur, fading to none: ease-out. */
    static float strength(float y, float band) {
        if (band <= 0) return 0f;
        float t = Math.max(0f, Math.min(1f, y / band));
        return (1f - t) * (1f - t);
    }

    /** A canvas to record the list into, or null to draw as usual. */
    public Canvas begin(View list, Canvas canvas) {
        if (!supported() || canvas == null || !canvas.isHardwareAccelerated() || style() == OFF || list.getWidth() <= 0 || list.getHeight() <= 0) return null;
        android.graphics.RenderNode node = content instanceof android.graphics.RenderNode ? (android.graphics.RenderNode) content : null;
        if (node == null) content = node = new android.graphics.RenderNode("nebulaChatContent");
        node.setPosition(0, 0, list.getWidth(), list.getHeight());
        return node.beginRecording();
    }

    /** Finishes the recording, draws the list, then the blurred band under a header ending at {@code top}. */
    public void end(View list, Canvas canvas, float top) {
        android.graphics.RenderNode node = (android.graphics.RenderNode) content;
        node.endRecording();
        canvas.drawRenderNode(node);
        int style = style();
        int width = list.getWidth();
        int bandTop = Math.max(0, Math.round(top));
        int band = AndroidUtilities.dp(bandDp(style));
        int bottom = Math.min(list.getHeight(), bandTop + band);
        if (bottom <= 0 || width <= 0) return;
        android.graphics.RenderNode blur = blurred instanceof android.graphics.RenderNode ? (android.graphics.RenderNode) blurred : null;
        if (blur == null) blurred = blur = new android.graphics.RenderNode("nebulaHeaderBlur");
        float r = AndroidUtilities.dp(radiusDp(style));
        if (r != radius) {
            radius = r;
            blur.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
        }
        blur.setPosition(0, 0, width, bottom);
        Canvas recording = blur.beginRecording();
        recording.drawRenderNode(node);
        blur.endRecording();
        if (maskHeight != bottom || maskStyle != style) {
            maskHeight = bottom; maskStyle = style;
            // Fully blurred down to the header edge, then fading out over the band.
            float edge = bottom <= 0 ? 0 : bandTop / (float) bottom;
            mask.setShader(new LinearGradient(0, 0, 0, bottom, new int[]{0xFF000000, 0xFF000000, 0x40000000, 0x00000000},
                    new float[]{0f, edge, edge + (1f - edge) * .5f, 1f}, Shader.TileMode.CLAMP));
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }
        int save = canvas.saveLayer(0, 0, width, bottom, null);
        canvas.drawRenderNode(blur);
        canvas.drawRect(0, 0, width, bottom, mask);
        canvas.restoreToCount(save);
    }
}
