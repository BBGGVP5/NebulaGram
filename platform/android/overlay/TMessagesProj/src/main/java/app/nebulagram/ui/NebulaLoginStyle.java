package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * Приводит экраны входа Telegram к нашей типографике.
 *
 * <p>Почему именно так, а не своими экранами: `PhoneView` и `LoginActivitySmsView`
 * — внутренние классы `LoginActivity`, работающие с его приватным состоянием, а
 * завершение авторизации (`onAuthSuccess`) приватно и заканчивается вызовом
 * `needFinishActivity` на самом фрагменте. Своя вёрстка означала бы либо копию
 * этого кода, которая молча протухнет на следующем релизе, либо форк экрана
 * входа целиком. Цена ошибки здесь — сломанный вход, то есть неработающее
 * приложение.
 *
 * <p>Поэтому мы не трогаем ни логику, ни приватные поля: обходим готовое дерево
 * вью и правим то, что видим по типу. Если апстрим перестроит разметку, мы
 * просто найдём меньше элементов, и экран останется штатным.
 */
public final class NebulaLoginStyle {

    private static final String PREFS = "nebulagram";
    private static final String KEY_ENABLED = "login_style";

    /** Крупнее этого размера текст считается заголовком экрана. */
    private static final float TITLE_THRESHOLD_SP = 18f;

    private NebulaLoginStyle() {
    }

    /** Выключатель на случай, если оформление начнёт мешать. */
    public static boolean enabled() {
        try {
            SharedPreferences prefs =
                    ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
            return prefs.getBoolean(KEY_ENABLED, true);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setEnabled(boolean value) {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .edit().putBoolean(KEY_ENABLED, value).apply();
    }

    /** Вызывается после того, как Telegram собрал слайды входа. */
    public static void apply(View[] slides) {
        if (slides == null || !enabled()) {
            return;
        }
        try {
            for (View slide : slides) {
                restyle(slide, 0);
            }
        } catch (Throwable e) {
            // Оформление косметика: оно не должно мешать войти в аккаунт.
            FileLog.e(e);
        }
    }

    private static void restyle(View view, int depth) {
        if (view == null || depth > 12) {
            return;
        }
        if (view instanceof TextView) {
            restyleText((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                restyle(group.getChildAt(i), depth + 1);
            }
        }
    }

    /**
     * Заголовкам — размер и начертание из нашей шкалы, подписям — межстрочный
     * интервал. Цвета не трогаем: их уже задаёт тема, которой мы отдали
     * системный акцент, и переопределение здесь сломало бы светлую схему.
     */
    private static void restyleText(TextView text) {
        float sizeSp = text.getTextSize() / text.getResources().getDisplayMetrics().scaledDensity;

        if (sizeSp >= TITLE_THRESHOLD_SP) {
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            text.setLetterSpacing(0f);
            text.setLineSpacing(AndroidUtilities.dp(2), 1f);
        } else if (sizeSp >= 13f && sizeSp < 16f) {
            text.setLineSpacing(AndroidUtilities.dp(3), 1f);
        }
    }
}
