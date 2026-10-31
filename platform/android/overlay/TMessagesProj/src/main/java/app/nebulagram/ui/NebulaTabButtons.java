package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/**
 * Вкладки профиля отдельными кнопками вместо общей полосы.
 *
 * <p>Полоса выделения остаётся: она показывает выбранную вкладку и едет между
 * ними при переключении. Эта подложка только добавляет остальным вкладкам их
 * собственный контур, поэтому ничего не нужно знать о положении выделения и
 * анимация переключения не меняется.
 *
 * <p>Оттенок, а не цвет: профиль может идти поверх обоев и градиента шапки.
 */
public final class NebulaTabButtons {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "profile_tab_buttons";

    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF RECT = new RectF();

    private NebulaTabButtons() { }

    public static boolean enabled() {
        return prefs().getBoolean(KEY, false);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY, value).apply();
    }

    /** Рисует подложку под каждой вкладкой в координатах полосы. */
    public static void draw(Canvas canvas, ViewGroup container, Theme.ResourcesProvider provider) {
        if (!enabled() || container == null) return;
        PAINT.setColor(NebulaTheme.stateLayer(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider), .06f));
        final float inset = org.telegram.messenger.AndroidUtilities.dp(3);
        for (int i = 0, count = container.getChildCount(); i < count; i++) {
            View tab = container.getChildAt(i);
            if (tab.getVisibility() != View.VISIBLE || tab.getWidth() <= 0) continue;
            RECT.set(tab.getLeft() + tab.getTranslationX(), tab.getTop() + inset,
                    tab.getRight() + tab.getTranslationX(), tab.getBottom() - inset);
            final float radius = NebulaShape.radius(RECT.height() / 2f
                    / org.telegram.messenger.AndroidUtilities.density);
            canvas.drawRoundRect(RECT, radius, radius, PAINT);
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
