package app.nebulagram.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;

/**
 * "Blurred messages" theme: a bubble's solid fill is replaced by the blurred
 * wallpaper under it with a translucent tint of the bubble's own colour on
 * top, the way exteraGram's BlurBGMessage plugin draws them.
 *
 * <p>The blurred wallpaper is the one Telegram already keeps for service
 * messages, so nothing extra is rendered or cached. Without a picture or
 * gradient wallpaper (or in Lite mode) that bitmap does not exist and bubbles
 * stay solid.
 */
public final class NebulaMessageBlur {
    public static final String KEY = "message_blur";
    public static final String KEY_TINT = "message_blur_tint";
    public static final int DEFAULT_TINT = 55;

    private static final Paint wallpaper = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Matrix matrix = new Matrix();
    private static Bitmap shaderBitmap;
    private static BitmapShader shader;

    private NebulaMessageBlur() { }

    public static boolean enabled() {
        return NebulaMessagePreferences.enabled(KEY, false);
    }

    public static void setEnabled(boolean value) {
        NebulaMessagePreferences.set(KEY, value);
    }

    /** Opacity of the bubble colour over the blur, in percent. */
    public static int tintPercent() {
        return NebulaMessagePreferences.number(KEY_TINT, DEFAULT_TINT, 20, 90);
    }

    public static void setTintPercent(int value) {
        NebulaMessagePreferences.set(KEY_TINT, Math.max(20, Math.min(90, value)));
    }

    /** Active only when the wallpaper has a blurred copy to show. */
    public static boolean active() {
        return enabled() && org.telegram.ui.ActionBar.Theme.getNebulaServiceBitmap() != null;
    }

    /** Alpha of the tint layer for a bubble colour, honouring the drawable's alpha. */
    public static int tintAlpha(int bubbleColor, int drawableAlpha, int percent) {
        float colorAlpha = Color.alpha(bubbleColor) / 255f;
        return Math.max(0, Math.min(255, Math.round(255f * colorAlpha * percent / 100f * drawableAlpha / 255f)));
    }

    /**
     * Same centre-crop mapping Telegram uses for service backgrounds: the
     * bitmap covers {@code width × height} and the bubble samples it at its
     * own place in the list.
     */
    public static void map(Matrix out, float bitmapWidth, float bitmapHeight, int width, int height, float offsetY) {
        float scale = Math.max(width / bitmapWidth, height / bitmapHeight);
        float x = (width - bitmapWidth * scale) / 2f;
        float y = (height - bitmapHeight * scale) / 2f;
        out.reset();
        out.setTranslate(x, y - offsetY);
        out.preScale(scale, scale);
    }

    /**
     * Draws the blurred bubble. Returns {@code false} when it cannot, so the
     * caller paints the solid bubble as usual.
     */
    public static boolean draw(Canvas canvas, Path path, int listHeight, int top, int bubbleColor, int drawableAlpha) {
        Bitmap bitmap = org.telegram.ui.ActionBar.Theme.getNebulaServiceBitmap();
        if (bitmap == null || bitmap.isRecycled() || listHeight <= 0) return false;
        if (shaderBitmap != bitmap || shader == null) {
            shaderBitmap = bitmap;
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        map(matrix, bitmap.getWidth(), bitmap.getHeight(), AndroidUtilities.displaySize.x, listHeight, top);
        shader.setLocalMatrix(matrix);
        wallpaper.setShader(shader);
        wallpaper.setAlpha(drawableAlpha);
        canvas.drawPath(path, wallpaper);
        tint.setColor(bubbleColor | 0xff000000);
        tint.setAlpha(tintAlpha(bubbleColor, drawableAlpha, tintPercent()));
        canvas.drawPath(path, tint);
        return true;
    }
}
