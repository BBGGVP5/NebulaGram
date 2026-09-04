package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/**
 * Название приложения в шапке списка чатов.
 *
 * <p>Telegram рисует его картинкой — вектором с обведёнными буквами слова
 * «Telegram». Слово намертво в путях, подменить его строкой нельзя, поэтому
 * при свёрнутых историях в нашем приложении читалось чужое имя.
 *
 * <p>Рисуем текстом: то же место, тот же жирный шрифт приложения, а название
 * берётся из ресурсов и переводится вместе с остальными. Отрисовка идёт белым
 * — цвет накладывает сам ImageView своим фильтром, как и на исходной картинке.
 */
public final class NebulaWordmark extends Drawable {

    /** Высота исходной картинки Telegram: держим её, чтобы не поехала разметка. */
    private static final float HEIGHT_DP = 20.4f;

    /**
     * Что написано на месте логотипа. Меняется вместе с заголовком: при
     * включённой настройке там стоит название текущей папки, иначе имя
     * приложения. Значение общее, потому что и заголовок, и логотип
     * показывают одно и то же — просто в разных состояниях историй.
     */
    private static CharSequence current;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private NebulaWordmark() {
        paint.setTypeface(AndroidUtilities.bold());
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(AndroidUtilities.dp(HEIGHT_DP));
    }

    /** Задаёт надпись: то же значение, что и в заголовке панели. */
    public static void setText(CharSequence text) {
        current = text;
    }

    private static String text() {
        CharSequence value = current;
        if (value == null || value.length() == 0) {
            value = LocaleController.getString(R.string.NebulaAppName);
        }
        return value.toString();
    }

    /** Ставит наше название вместо картинки с чужим. */
    public static void apply(ImageView view) {
        if (view == null) {
            return;
        }
        view.setImageDrawable(new NebulaWordmark());
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text(), bounds.left, baseline, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) Math.ceil(paint.measureText(text()));
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(HEIGHT_DP + 4);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
