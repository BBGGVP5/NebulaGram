package app.nebulagram.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import org.telegram.messenger.Utilities;

/**
 * Фон в духе Apple Music: размытая обложка во весь экран, поверх неё
 * медленно плывут мягкие пятна цветов обложки, сверху — затемнение для
 * читаемости. Новая обложка проявляется поверх прежней. Без движения, когда
 * анимации отключены.
 */
public final class NebulaAppleBackdrop extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private Bitmap current, previous;
    private long changedAt;
    private final int[] colors = {0xff3a2f4f, 0xff23415a, 0xff4a2a35};
    private final RadialGradient[] blobs = new RadialGradient[3];
    private boolean moving = true;

    public void setMoving(boolean value) { moving = value; invalidateSelf(); }

    /** Новая обложка: уменьшается и размывается один раз, а не на каждом кадре. */
    public void setCover(Bitmap source) {
        Bitmap blurred = null;
        if (source != null && !source.isRecycled()) {
            try {
                Bitmap software = source.getConfig() == Bitmap.Config.HARDWARE ? source.copy(Bitmap.Config.ARGB_8888, false) : source;
                blurred = Bitmap.createScaledBitmap(software, 48, 48, true);
                if (software != source) software.recycle();
                if (blurred == software) blurred = blurred.copy(Bitmap.Config.ARGB_8888, true);
                else if (!blurred.isMutable()) blurred = blurred.copy(Bitmap.Config.ARGB_8888, true);
                Utilities.stackBlurBitmap(blurred, 6);
            } catch (Throwable ignore) {
                blurred = null;
            }
        }
        previous = current;
        current = blurred;
        changedAt = SystemClock.uptimeMillis();
        invalidateSelf();
    }

    public void setPalette(int first, int second, int third) {
        colors[0] = first; colors[1] = second; colors[2] = third;
        blobs[0] = blobs[1] = blobs[2] = null;
        invalidateSelf();
    }

    /** Нужен ли следующий кадр. */
    public boolean animating() {
        return moving || SystemClock.uptimeMillis() - changedAt < 700;
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int w = bounds.width(), h = bounds.height();
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(0xff101014);
        long now = SystemClock.uptimeMillis();
        float fade = Math.min(1f, (now - changedAt) / 700f);
        if (previous != null && fade < 1f) drawCover(canvas, previous, bounds, 1f);
        if (current != null) drawCover(canvas, current, bounds, previous != null ? fade : 1f);
        float time = moving ? (now % 600000L) / 1000f : 0f;
        float radius = Math.max(w, h) * .62f;
        for (int i = 0; i < 3; i++) {
            if (blobs[i] == null) {
                blobs[i] = new RadialGradient(0, 0, radius, (colors[i] & 0x00ffffff) | 0x8c000000,
                        colors[i] & 0x00ffffff, Shader.TileMode.CLAMP);
            }
            float phase = time * (.07f + i * .025f) + i * 2.1f;
            float x = w * (.5f + .38f * (float) Math.cos(phase));
            float y = h * (.35f + .3f * i * .5f + .22f * (float) Math.sin(phase * 1.3f));
            matrix.setTranslate(x, y);
            blobs[i].setLocalMatrix(matrix);
            blobPaint.setShader(blobs[i]);
            canvas.drawRect(bounds, blobPaint);
        }
        blobPaint.setShader(null);
        // Затемнение: белый текст читается на любой обложке.
        canvas.drawColor(0x59000000);
    }

    private void drawCover(Canvas canvas, Bitmap bitmap, Rect bounds, float alpha) {
        float scale = Math.max(bounds.width() / (float) bitmap.getWidth(), bounds.height() / (float) bitmap.getHeight()) * 1.15f;
        float bw = bitmap.getWidth() * scale, bh = bitmap.getHeight() * scale;
        rect.set(bounds.centerX() - bw / 2f, bounds.centerY() - bh / 2f, bounds.centerX() + bw / 2f, bounds.centerY() + bh / 2f);
        paint.setAlpha(Math.round(255 * alpha));
        canvas.drawBitmap(bitmap, null, rect, paint);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
