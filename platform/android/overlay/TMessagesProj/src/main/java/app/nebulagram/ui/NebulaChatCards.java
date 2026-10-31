package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/**
 * Каждый чат своей карточкой.
 *
 * <p>Подложка рисуется оттенком поверх того фона, который уже под строкой, а
 * не своим цветом: в списке чатов может стоять что угодно — тёмная тема,
 * светлая, обои. Собственный цвет совпал бы с фоном в одних темах и выбился
 * бы в других, а оттенок виден везде одинаково.
 *
 * <p>Содержимое строки не сдвигается. Карточка — именно подложка, и двигать
 * ради неё аватарки с текстом значило бы переверстать строку, у которой и
 * размеры, и анимации свайпа считаются от нынешних координат.
 */
public final class NebulaChatCards {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "chat_cards";

    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF RECT = new RectF();

    private NebulaChatCards() { }

    public static boolean enabled() {
        return prefs().getBoolean(KEY, false);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
    }

    /**
     * Рисует подложку под строкой списка чатов.
     *
     * @param translateY сдвиг, с которым строка уже рисует свои фоны, — чтобы
     *                   карточка ехала вместе с ней, а не оставалась на месте.
     */
    public static void draw(Canvas canvas, View cell, float translateY,
                            Theme.ResourcesProvider provider) {
        if (!enabled()) return;
        final int width = cell.getMeasuredWidth(), height = cell.getMeasuredHeight();
        if (width <= 0 || height <= 0) return;

        final float inset = AndroidUtilities.dp(8);
        final float gap = AndroidUtilities.dp(3);
        RECT.set(inset, gap - translateY, width - inset, height - gap - translateY);
        final float radius = NebulaShape.radius(16);
        PAINT.setColor(NebulaTheme.stateLayer(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider), .05f));
        canvas.drawRoundRect(RECT, radius, radius, PAINT);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
