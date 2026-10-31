package app.nebulagram.ui;

import android.graphics.*;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;

/** Four soft colour fields sampled from the current image or a tiny video frame. */
public final class NebulaMediaAmbient {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] colors = new int[4];
    private final RadialGradient[] gradients = new RadialGradient[4];
    private Bitmap videoSample;
    private int imageKey, width, height;
    private long sampled, nextInvalidate;
    public void draw(Canvas canvas, View host, Bitmap image, TextureView video) {
        if (!NebulaFeatureSettings.enabled("media_ambient") || host == null) return;
        int w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        int key = image == null || image.isRecycled() ? 0 : System.identityHashCode(image) ^ image.getGenerationId();
        boolean changed = key != imageKey || w != width || h != height;
        long now = SystemClock.uptimeMillis();
        if (changed || video != null && now - sampled >= 500) {
            sampled = now; imageKey = key; width = w; height = h;
            Bitmap sample = image;
            try {
                if (video != null && video.isAvailable()) {
                    if (videoSample == null) videoSample = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
                    Bitmap frame = video.getBitmap(videoSample);
                    if (frame != null) sample = frame;
                }
                if (sample == null || sample.isRecycled()) { java.util.Arrays.fill(gradients, null); return; }
                for (int i = 0; i < 4; i++) {
                    int x = Math.min(sample.getWidth() - 1, sample.getWidth() * (i % 2 == 0 ? 1 : 3) / 4);
                    int y = Math.min(sample.getHeight() - 1, sample.getHeight() * (i < 2 ? 1 : 3) / 4);
                    int next = sample.getPixel(x, y) & 0xffffff;
                    if (changed || colors[i] != next || gradients[i] == null) {
                        colors[i] = next;
                        gradients[i] = new RadialGradient(i % 2 == 0 ? 0 : w, i < 2 ? 0 : h,
                            Math.max(w, h) * .8f, new int[]{0x78000000 | next, next}, null, Shader.TileMode.CLAMP);
                    }
                }
            } catch (RuntimeException ignored) { java.util.Arrays.fill(gradients, null); }
        }
        for (RadialGradient gradient : gradients) if (gradient != null) { paint.setShader(gradient); canvas.drawRect(0, 0, w, h, paint); }
        paint.setShader(null);
        if (video != null && video.isAvailable() && now >= nextInvalidate) {
            nextInvalidate = now + 500;
            host.postInvalidateDelayed(500);
        }
    }
}
