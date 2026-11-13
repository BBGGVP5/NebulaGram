package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Live preview for "Colours from avatar / wallpaper": the picture itself, the
 * swatches Palette found in it, and a button plus an outgoing bubble painted
 * in the accent that picture gives the current theme.
 */
public final class NebulaPictureAccentPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private NebulaPictureTheme.Result result;
    private Runnable onChanged;
    private final NebulaPictureTheme.Listener listener = value -> {
        setResult(value);
        if (onChanged != null) onChanged.run();
    };

    public NebulaPictureAccentPreview(Context context) {
        super(context);
        text.setTextSize(AndroidUtilities.dp(13));
        text.setTypeface(AndroidUtilities.bold());
    }

    /** Runs after a new extraction arrives, so the host can repaint its own accent. */
    public void setOnChanged(Runnable value) {
        onChanged = value;
    }

    public void setResult(NebulaPictureTheme.Result value) {
        result = value;
        image.setShader(null);
        Bitmap bitmap = value == null ? null : value.thumbnail;
        if (bitmap != null && !bitmap.isRecycled()) {
            image.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        setContentDescription(value == null || value.seed == 0
                ? NebulaText.text("В картинке не нашлось цвета", "No colour found in the picture")
                : NebulaText.text("Акцент из картинки", "Accent from the picture"));
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = result == null || result.source == NebulaPictureTheme.OFF ? 0 : AndroidUtilities.dp(112);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (result == null || result.source == NebulaPictureTheme.OFF) return;
        NebulaTheme theme = NebulaTheme.of(getContext());
        boolean dark = theme.isDark();
        int pad = AndroidUtilities.dp(16), side = AndroidUtilities.dp(80), top = AndroidUtilities.dp(16);
        rect.set(pad, top, pad + side, top + side);
        float radius = result.source == NebulaPictureTheme.AVATAR ? side / 2f : AndroidUtilities.dp(14);
        Bitmap bitmap = result.thumbnail;
        if (image.getShader() != null && bitmap != null && !bitmap.isRecycled()) {
            Matrix matrix = new Matrix();
            float scale = Math.max(side / (float) bitmap.getWidth(), side / (float) bitmap.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(rect.left + (side - bitmap.getWidth() * scale) / 2f, rect.top + (side - bitmap.getHeight() * scale) / 2f);
            image.getShader().setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, radius, radius, image);
        } else {
            paint.setColor(theme.outline());
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        int accent = NebulaPictureAccent.tone(result.seed, dark);
        float x = rect.right + AndroidUtilities.dp(16);
        float right = getWidth() - pad;
        // Swatches: what the picture is made of, best candidates first.
        float chip = AndroidUtilities.dp(18), gap = AndroidUtilities.dp(6);
        float y = top + chip / 2f;
        for (int i = 0; i < result.swatches.length && x + (chip + gap) * (i + 1) - gap <= right; i++) {
            paint.setColor(result.swatches[i]);
            canvas.drawCircle(x + chip / 2f + i * (chip + gap), y, chip / 2f, paint);
            if ((result.swatches[i] | 0xFF000000) == result.seed) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2));
                paint.setColor(theme.onSurface());
                canvas.drawCircle(x + chip / 2f + i * (chip + gap), y, chip / 2f + AndroidUtilities.dp(3), paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        if (accent == 0) {
            text.setColor(theme.onSurfaceVariant());
            canvas.drawText(NebulaText.text("Цвета не нашлось — акцент прежний", "No colour found — accent unchanged"),
                    x, top + chip + AndroidUtilities.dp(28), text);
            return;
        }
        // A filled button and an outgoing bubble, the two places an accent shows most.
        float rowTop = top + chip + AndroidUtilities.dp(14), rowHeight = AndroidUtilities.dp(34);
        String label = NebulaText.text("Кнопка", "Button");
        float buttonWidth = text.measureText(label) + AndroidUtilities.dp(32);
        rect.set(x, rowTop, Math.min(right, x + buttonWidth), rowTop + rowHeight);
        paint.setColor(accent);
        canvas.drawRoundRect(rect, rowHeight / 2f, rowHeight / 2f, paint);
        text.setColor(androidx.core.graphics.ColorUtils.calculateLuminance(accent) > .5 ? 0xFF101318 : 0xFFFFFFFF);
        canvas.drawText(label, rect.left + AndroidUtilities.dp(16), rect.centerY() + text.getTextSize() / 3f, text);

        float bubbleLeft = rect.right + AndroidUtilities.dp(10);
        if (bubbleLeft + AndroidUtilities.dp(56) <= right) {
            rect.set(bubbleLeft, rowTop, right, rowTop + rowHeight);
            paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(theme.surfaceContainer(), accent, dark ? .32f : .22f));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), paint);
            text.setColor(accent);
            String link = "@nebulagram";
            canvas.drawText(link, 0, text.breakText(link, true, rect.width() - AndroidUtilities.dp(24), null),
                    rect.left + AndroidUtilities.dp(12), rect.centerY() + text.getTextSize() / 3f, text);
        }
        text.setColor(theme.onSurfaceVariant());
        text.setTypeface(null);
        String hex = String.format("#%06X", accent & 0xFFFFFF);
        canvas.drawText(hex, x, rowTop + rowHeight + AndroidUtilities.dp(20), text);
        text.setTypeface(AndroidUtilities.bold());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NebulaPictureTheme.setListener(listener);
        NebulaPictureTheme.Result known = NebulaPictureTheme.last();
        if (known != null) setResult(known);
        else if (NebulaPictureTheme.source() != NebulaPictureTheme.OFF) NebulaPictureTheme.refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NebulaPictureTheme.clearListener(listener);
    }
}
