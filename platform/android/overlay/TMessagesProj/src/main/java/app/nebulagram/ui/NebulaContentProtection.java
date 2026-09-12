package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Снимать ли ограничения на копирование в чатах, которые их выставили.
 *
 * <p>Флаг отправителя остаётся флагом: сервер по-прежнему присылает его, мы
 * лишь перестаём применять его к собственному экрану. Речь о содержимом,
 * которое устройство уже получило и уже хранит в своём кэше, — запрет касался
 * только того, что с ним разрешено делать дальше в этом клиенте.
 *
 * <p>Выключено по умолчанию: это осознанный выбор пользователя, а не поведение
 * по умолчанию.
 *
 * <p>Значение живёт в памяти, потому что {@link #enabled()} спрашивают из
 * {@code updateWindowSecure} и из проверок пересылки — то есть на путях, где
 * поход в SharedPreferences повторялся бы без нужды. Слушателя держим сами:
 * SharedPreferences хранит их слабо.
 */
public final class NebulaContentProtection {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "allow_protected_content";

    private NebulaContentProtection() { }

    private static volatile Boolean cached;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (preferences, key) -> { if (key == null || KEY.equals(key)) cached = null; };

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    /** Разрешены ли снимки экрана, пересылка и сохранение из защищённых чатов. */
    public static boolean enabled() {
        Boolean value = cached;
        if (value != null) {
            return value;
        }
        try {
            SharedPreferences prefs = preferences();
            synchronized (NebulaContentProtection.class) {
                if (cached == null) {
                    prefs.registerOnSharedPreferenceChangeListener(listener);
                    cached = prefs.getBoolean(KEY, false);
                }
                return cached;
            }
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setEnabled(boolean value) {
        cached = value;
        preferences().edit().putBoolean(KEY, value).apply();
    }
}
