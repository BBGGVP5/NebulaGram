package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Стикер, эмодзи и GIF в выбранных размерах, в одном масштабе друг с другом.
 *
 * <p>Рисует доли, а не пиксели: в приложении размер тоже считается долей
 * стороны экрана, и превью в пикселях врало бы на другом экране. Пунктир
 * показывает обычный размер — иначе по одному проценту трудно понять, много
 * это или мало.
 */
public final class NebulaMediaSizePreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public NebulaMediaSizePreview(Context context) {
        super(context);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(AndroidUtilities.dp(1));
        outline.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{AndroidUtilities.dp(3), AndroidUtilities.dp(3)}, 0));
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), dp(132));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        NebulaTheme theme = NebulaTheme.of(getContext());
        // Три слота равной ширины; обычный размер занимает слот целиком за
        // вычетом полей, поэтому 150 % упирается в край, а 50 % — вчетверо меньше.
        final float slot = (getWidth() - dp(32)) / 3f;
        final float normal = Math.min(slot - dp(14), dp(62));
        final float baseline = dp(100);

        draw(canvas, theme, dp(16) + slot * 0f, slot, normal, baseline,
                NebulaMediaSize.stickerPercent(), NebulaText.text("Стикер", "Sticker"));
        draw(canvas, theme, dp(16) + slot * 1f, slot, normal, baseline,
                NebulaMediaSize.emojiPercent(), NebulaText.text("Эмодзи", "Emoji"));
        draw(canvas, theme, dp(16) + slot * 2f, slot, normal, baseline,
                NebulaMediaSize.gifPercent(), "GIF");
    }

    private void draw(Canvas canvas, NebulaTheme theme, float left, float slot,
                      float normal, float baseline, int percent, String title) {
        final float centre = left + slot / 2f;
        final float corner = NebulaShape.radius(8);

        outline.setColor(NebulaTheme.stateLayer(theme.onSurface(), .28f));
        rect.set(centre - normal / 2f, baseline - normal, centre + normal / 2f, baseline);
        canvas.drawRoundRect(rect, corner, corner, outline);

        float side = normal * percent / 100f;
        paint.setColor(theme.primary());
        paint.setAlpha(percent >= 100 ? 190 : 140);
        rect.set(centre - side / 2f, baseline - side, centre + side / 2f, baseline);
        canvas.drawRoundRect(rect, corner, corner, paint);

        paint.setAlpha(255);
        paint.setColor(theme.onSurface());
        paint.setTextSize(dp(12));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, centre, baseline + dp(16), paint);
        paint.setColor(theme.onSurfaceVariant());
        paint.setTextSize(dp(11));
        canvas.drawText(percent + "%", centre, baseline + dp(30), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
