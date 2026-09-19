package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Значки рядом с именем: кому какой, знает сервер.
 *
 * <p>Значок нужен, чтобы его видели другие, поэтому в профиле он показывается
 * любому — и своему, и чужому. А вот список целиком наружу не отдаётся: клиент
 * спрашивает про один идентификатор, тот, чей профиль открыли, и получает ответ
 * только про него. Выгрузить, кому что выдано, так нельзя.
 *
 * <p>Ответ живёт в кэше сутки, поэтому открытый профиль — это один маленький
 * запрос в сутки, а не запрос на каждое перелистывание.
 *
 * <p>Выдаёт значки тот, у кого есть админ-токен: приложение отправляет его
 * серверу, и решение принимает сервер. Ни токена, ни списка в открытых
 * исходниках нет, и патч клиента чужой экран не меняет — он меняет свой.
 */
public final class NebulaBadges {
    private static final String PREFS = "nebulagram_badges";
    private static final String KEY_HOST = "host";
    private static final String KEY_TOKEN = "admin_token";
    /** Ответ про одного человека. Ключ — идентификатор, значение — «вид:время». */
    private static final String KEY_PREFIX = "badge_";
    private static final long TTL = 24 * 60 * 60 * 1000L;

    private NebulaBadges() { }

    private static final java.util.Set<Long> inFlight = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static Runnable onChanged;

    /** Экран профиля просит сообщить, когда ответ придёт: имя уже нарисовано. */
    public static void setChangeListener(Runnable listener) {
        onChanged = listener;
    }

    // --- что показывать ------------------------------------------------------

    /** Вид значка этого пользователя или {@code null}. Не ходит в сеть. */
    public static String badge(long userId) {
        if (userId == 0 || host().isEmpty()) {
            return null;
        }
        String cached = prefs().getString(KEY_PREFIX + userId, null);
        if (cached == null) {
            request(userId);
            return null;
        }
        int split = cached.lastIndexOf(':');
        if (split < 0) {
            return null;
        }
        long when = Utilities.parseLong(cached.substring(split + 1));
        if (System.currentTimeMillis() - when > TTL) {
            request(userId);
        }
        String kind = cached.substring(0, split);
        return kind.isEmpty() ? null : kind;
    }

    /** Bundled artwork; unknown server kinds never fall back to emoji. */
    public static int iconResource(String kind) {
        if (kind == null) return 0;
        switch (kind) {
            case "supporter": return R.drawable.nebula_badge_supporter;
            case "dev": return R.drawable.nebula_badge_dev;
            case "tester": return R.drawable.nebula_badge_tester;
            case "star": return R.drawable.nebula_badge_star;
            case "heart": return R.drawable.nebula_badge_heart;
            default: return 0;
        }
    }

    private static CharSequence withBadge(CharSequence text, String kind) {
        int resource = iconResource(kind);
        if (resource == 0) return text;
        android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(
                ApplicationLoader.applicationContext, resource);
        if (drawable == null) return text;
        SpannableStringBuilder result = new SpannableStringBuilder(text).append(" ");
        int start = result.length();
        result.append("\uFFFC");
        result.setSpan(new NebulaBadgeSpan(drawable), start, result.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    public static CharSequence label(String kind) {
        String title = title(kind);
        return title == null ? "" : withBadge(title, kind);
    }

    /** Человеческое имя вида значка — для экрана настроек. */
    public static String title(String kind) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case "supporter": return NebulaText.text("Поддержал проект", "Supporter");
            case "dev": return NebulaText.text("Разработчик", "Developer");
            case "tester": return NebulaText.text("Тестировщик", "Tester");
            case "star": return NebulaText.text("Звезда", "Star");
            case "heart": return NebulaText.text("Спасибо", "Thanks");
            default: return null;
        }
    }

    /** Виды, которые умеет выдавать панель. */
    public static String[] kinds() {
        return new String[]{"supporter", "dev", "tester", "star", "heart"};
    }

    /**
     * Имя со значком. Возвращает исходное имя, когда значка нет, — вызывающему
     * не нужно об этом помнить.
     */
    public static CharSequence decorate(CharSequence name, long userId, TextPaint paint) {
        if (name == null) {
            return null;
        }
        return withBadge(name, badge(userId));
    }

    // --- откуда брать --------------------------------------------------------

    /**
     * Адрес сервера значков. Свой, и по умолчанию наш: пользователю вводить
     * нечего. Служебный адрес не показывается в обычных настройках.
     */
    private static final String DEFAULT_HOST = "https://hooks.nebulaguard.mooo.com";

    public static String host() {
        return prefs().getString(KEY_HOST, DEFAULT_HOST);
    }

    /** Пустая строка возвращает адрес по умолчанию, а не выключает значки. */
    public static void setHost(String value) {
        String host = value == null || value.trim().isEmpty() ? DEFAULT_HOST : value.trim();
        while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        prefs().edit().putString(KEY_HOST, host).apply();
        forget();
    }

    /** Админ-токен. Есть токен — в настройках появляется панель выдачи. */
    public static String token() {
        return prefs().getString(KEY_TOKEN, "");
    }

    public static void setToken(String value) {
        prefs().edit().putString(KEY_TOKEN, value == null ? "" : value.trim()).apply();
    }

    public static boolean admin() {
        return !token().isEmpty() && !host().isEmpty();
    }

    /** Забыть все ответы: после смены адреса прежние к нему не относятся. */
    public static void forget() {
        SharedPreferences.Editor editor = prefs().edit();
        for (String key : prefs().getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) editor.remove(key);
        }
        editor.apply();
    }

    private static void request(long userId) {
        if (!inFlight.add(userId)) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String kind = null;
            try {
                HttpURLConnection connection = open("/v1/badge/" + userId, "GET");
                if (connection.getResponseCode() == 200) {
                    JSONObject answer = new JSONObject(read(connection));
                    kind = answer.optString("badge", "");
                }
                connection.disconnect();
            } catch (Throwable e) {
                FileLog.e(e);
                inFlight.remove(userId);
                return;
            }
            // Отсутствие значка тоже кэшируем: иначе каждый чужой профиль стоил
            // бы запроса, а таких профилей у человека большинство.
            prefs().edit().putString(KEY_PREFIX + userId,
                    (kind == null ? "" : kind) + ":" + System.currentTimeMillis()).apply();
            inFlight.remove(userId);
            AndroidUtilities.runOnUIThread(() -> { if (onChanged != null) onChanged.run(); });
        });
    }

    // --- выдача --------------------------------------------------------------

    /**
     * Выдать или снять значок. Пустой {@code kind} снимает. Ответ приходит на
     * главный поток: {@code null} — получилось, иначе текст ошибки.
     */
    public static void grant(long userId, String kind, Utilities.Callback<String> done) {
        if (!admin()) {
            if (done != null) done.run(NebulaText.text("Нет адреса сервера или токена", "No server or token"));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String error = null;
            try {
                HttpURLConnection connection = open("/v1/badge", "POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                JSONObject body = new JSONObject();
                body.put("id", userId);
                body.put("badge", kind == null ? "" : kind);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                if (code == 401 || code == 403) {
                    error = NebulaText.text("Сервер не принял токен", "The server rejected the token");
                } else if (code / 100 != 2) {
                    error = NebulaText.text("Сервер ответил ", "The server answered ") + code;
                }
                connection.disconnect();
            } catch (Throwable e) {
                FileLog.e(e);
                error = NebulaText.text("Не удалось связаться с сервером", "Could not reach the server");
            }
            if (error == null) {
                prefs().edit().remove(KEY_PREFIX + userId).apply();
            }
            final String result = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null && onChanged != null) onChanged.run();
                if (done != null) done.run(result);
            });
        });
    }

    private static HttpURLConnection open(String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(host() + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        String token = token();
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        return connection;
    }

    private static String read(HttpURLConnection connection) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = connection.getInputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            // Ответ про одного человека крошечный; потолок защищает от чужого сервера.
            while ((read = in.read(chunk)) > 0 && buffer.size() < 64 * 1024) buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }

    /** Значок владельца устройства — для строки в настройках. */
    public static String own() {
        return badge(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
