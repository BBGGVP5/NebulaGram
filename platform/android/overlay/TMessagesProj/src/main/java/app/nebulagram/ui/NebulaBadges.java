package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.view.View;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    // Badge HTTP can wait for a remote timeout; it must not occupy Telegram's
    // shared global queue while dialogs and message updates are loading.
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "NebulaBadges");
        thread.setDaemon(true);
        return thread;
    });
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
            case "donor": return R.drawable.nebula_badge_donor;
            case "sponsor": return R.drawable.nebula_badge_sponsor;
            case "moderator": return R.drawable.nebula_badge_moderator;
            default: return 0;
        }
    }

    private static CharSequence withBadge(CharSequence text, String kind) {
        return withBadge(text, kind, null);
    }

    private static CharSequence withBadge(CharSequence text, String kind, View host) {
        int resource = iconResource(kind);
        if (resource == 0) return text;
        android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(
                ApplicationLoader.applicationContext, resource);
        if (drawable == null) return text;
        SpannableStringBuilder result = new SpannableStringBuilder(text).append(" ");
        int start = result.length();
        result.append("\uFFFC");
        result.setSpan(new NebulaBadgeSpan(drawable, host), start, result.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    public static CharSequence label(String kind) {
        String title = title(kind);
        return title == null ? "" : withBadge(title, kind);
    }

    /** Profile names keep Telegram's premium emoji as the first right drawable. */
    public static Drawable profileSuffix(Drawable existing, long userId) {
        if (existing instanceof ProfileSuffix) existing = ((ProfileSuffix) existing).base;
        String kind = badge(userId);
        int resource = iconResource(kind);
        if (resource == 0) return existing;
        Drawable artwork = ContextCompat.getDrawable(ApplicationLoader.applicationContext, resource);
        return artwork == null ? existing : new ProfileSuffix(existing, artwork, accent(kind));
    }

    /** Main colour of a badge: the glow behind it in the profile. */
    public static int accent(String kind) {
        if (kind == null) return 0xFFF2B33D;
        switch (kind) {
            case "dev": return 0xFF6C5CE7;
            case "tester": return 0xFF16A89A;
            case "heart": return 0xFFEB4F86;
            case "donor": return 0xFFF29A1F;
            case "sponsor": return 0xFF2F7FEA;
            case "moderator": return 0xFFF06A2E;
            case "supporter": return 0xFF8E7CF6;
            default: return 0xFFF2B33D;
        }
    }

    /**
     * Badge beside the profile name: a little larger than the text, with a soft
     * glow that breathes behind it and a few sparks circling it, as in
     * exteraGram. Static when system animations are off.
     */
    private static final class ProfileSuffix extends Drawable implements Drawable.Callback {
        private static final long PERIOD = 2600, ORBIT = 7000;
        private final Drawable base;
        private final Drawable badge;
        private final int badgeSize = AndroidUtilities.dp(20);
        private final int gap = AndroidUtilities.dp(5);
        private final int accent;
        private final android.graphics.Paint glow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint spark = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.RadialGradient gradient;
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private final long start = android.os.SystemClock.uptimeMillis();
        private int alpha = 255;

        ProfileSuffix(Drawable base, Drawable badge, int accent) {
            this.base = base;
            this.badge = badge.mutate();
            this.accent = accent;
            if (base != null) base.setCallback(this);
            this.badge.setCallback(this);
        }

        @Override public int getIntrinsicWidth() {
            return (base == null ? 0 : base.getIntrinsicWidth() + gap) + badgeSize + gap;
        }

        @Override public int getIntrinsicHeight() {
            return Math.max(badgeSize, base == null ? 0 : base.getIntrinsicHeight());
        }

        private static boolean animate() {
            return android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled();
        }

        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            float scale = getBounds().width() / (float) Math.max(1, getIntrinsicWidth());
            canvas.scale(scale, scale);
            int height = getIntrinsicHeight();
            int x = 0;
            if (base != null) {
                int width = base.getIntrinsicWidth(), itemHeight = base.getIntrinsicHeight();
                base.setBounds(0, (height - itemHeight) / 2, width, (height + itemHeight) / 2);
                base.draw(canvas);
                x = width + gap;
            }
            float cx = x + badgeSize / 2f, cy = height / 2f;
            boolean moving = animate();
            long now = android.os.SystemClock.uptimeMillis() - start;
            float breath = moving ? (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * (now % PERIOD) / PERIOD)) : 0.5f;
            float radius = badgeSize * (0.62f + 0.12f * breath);
            if (gradient == null) {
                gradient = new android.graphics.RadialGradient(0, 0, 1f,
                        new int[]{(accent & 0x00FFFFFF) | 0x8C000000, (accent & 0x00FFFFFF) | 0x33000000, accent & 0x00FFFFFF},
                        new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP);
            }
            matrix.setScale(radius, radius);
            matrix.postTranslate(cx, cy);
            gradient.setLocalMatrix(matrix);
            glow.setShader(gradient);
            glow.setAlpha(Math.round(alpha * (0.55f + 0.45f * breath)));
            canvas.drawCircle(cx, cy, radius, glow);
            // Three sparks on a slow orbit, each twinkling on its own phase.
            float orbit = badgeSize * 0.72f;
            double turn = moving ? 2 * Math.PI * (now % ORBIT) / ORBIT : 0;
            for (int i = 0; i < 3; i++) {
                double angle = turn + i * 2 * Math.PI / 3;
                float twinkle = moving ? (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * (now % 1800) / 1800 + i * 2.1)) : 0.6f;
                float sx = cx + (float) Math.cos(angle) * orbit, sy = cy + (float) Math.sin(angle) * orbit * 0.85f;
                float r = AndroidUtilities.dp(1.1f) + AndroidUtilities.dp(0.9f) * twinkle;
                spark.setColor(0xFFFFFFFF);
                spark.setAlpha(Math.round(alpha * (0.35f + 0.55f * twinkle)));
                canvas.drawCircle(sx, sy, r, spark);
                spark.setColor(accent);
                spark.setAlpha(Math.round(alpha * 0.35f * twinkle));
                canvas.drawCircle(sx, sy, r * 2.2f, spark);
            }
            badge.setBounds(x, (height - badgeSize) / 2, x + badgeSize, (height + badgeSize) / 2);
            badge.draw(canvas);
            canvas.restoreToCount(save);
            if (moving) invalidateSelf();
        }

        @Override public void setAlpha(int alpha) {
            this.alpha = alpha;
            if (base != null) base.setAlpha(alpha);
            badge.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter filter) {
            if (base != null) base.setColorFilter(filter);
            badge.setColorFilter(filter);
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
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
            // Keep the service key so existing assignments automatically receive Mira artwork.
            case "star": return "Mira";
            case "heart": return NebulaText.text("Спасибо", "Thanks");
            case "donor": return NebulaText.text("Донатер", "Donor");
            case "sponsor": return NebulaText.text("Спонсор", "Sponsor");
            case "moderator": return NebulaText.text("Модератор", "Moderator");
            default: return null;
        }
    }

    /** Bottom sheet shown when the badge beside a profile name is tapped. */
    public static void showInfo(BaseFragment fragment, long userId) {
        String kind = badge(userId);
        if (fragment == null || title(kind) == null || fragment.getParentActivity() == null) return;
        NebulaBadgeSheet.show(fragment, kind);
    }

    /** How a badge is earned, shown under its description. */
    public static String howToGet(String kind) {
        if (kind == null) return "";
        switch (kind) {
            case "donor": return NebulaText.text("Выдаётся после любого доната проекту", "Given after any donation to the project");
            case "sponsor": return NebulaText.text("Выдаётся за регулярную поддержку проекта", "Given for regular support of the project");
            case "supporter": return NebulaText.text("Выдаётся тем, кто помогает проекту делом или донатом", "Given to those who help the project with work or a donation");
            case "dev": return NebulaText.text("Только у разработчиков NebulaGram", "Only NebulaGram developers have it");
            case "moderator": return NebulaText.text("Выдаётся модераторам чатов NebulaGram", "Given to NebulaGram chat moderators");
            case "tester": return NebulaText.text("Выдаётся участникам бета-тестирования", "Given to beta testers");
            case "star": return NebulaText.text("Особый значок сообщества, выдаётся командой", "A special community badge given by the team");
            case "heart": return NebulaText.text("Выдаётся командой в знак благодарности", "Given by the team as a thank-you");
            default: return "";
        }
    }

    /** Badges anyone can earn by donating; their sheet offers to support the project. */
    public static boolean donation(String kind) {
        return "donor".equals(kind) || "sponsor".equals(kind) || "supporter".equals(kind);
    }

    public static String description(String kind) {
        if (kind == null) return "";
        switch (kind) {
            case "supporter": return NebulaText.text("Значок участника, который поддерживает развитие NebulaGram", "A community badge for someone supporting NebulaGram's development");
            case "dev": return NebulaText.text("Значок команды разработчиков NebulaGram", "A badge for the NebulaGram development team");
            case "tester": return NebulaText.text("Значок участника тестирования NebulaGram", "A badge for a NebulaGram beta tester");
            case "star": return NebulaText.text("Значок Mira — символ сообщества NebulaGram", "The Mira badge, a symbol of the NebulaGram community");
            case "heart": return NebulaText.text("Особый знак благодарности от команды NebulaGram", "A special thank-you from the NebulaGram team");
            case "donor": return NebulaText.text("Этот человек поддержал NebulaGram донатом. Каждый донат помогает выпускать обновления", "This person supported NebulaGram with a donation. Every donation helps ship updates");
            case "sponsor": return NebulaText.text("Спонсор NebulaGram — регулярно и заметно поддерживает развитие проекта", "A NebulaGram sponsor who supports the project regularly and substantially");
            case "moderator": return NebulaText.text("Модератор сообщества NebulaGram: помогает в чатах поддержки и следит за порядком", "A NebulaGram community moderator who helps in the support chats and keeps order");
            default: return "";
        }
    }

    /** Виды, которые умеет выдавать панель. */
    public static String[] kinds() {
        return new String[]{"donor", "sponsor", "supporter", "dev", "moderator", "tester", "star", "heart"};
    }

    /**
     * Имя со значком. Возвращает исходное имя, когда значка нет, — вызывающему
     * не нужно об этом помнить.
     */
    public static CharSequence decorate(CharSequence name, long userId, TextPaint paint) {
        return decorate(name, userId, paint, null);
    }

    /** Adds a small animated halo while the name view is attached to the screen. */
    public static CharSequence decorate(CharSequence name, long userId, TextPaint paint, View host) {
        if (name == null) {
            return null;
        }
        return withBadge(name, badge(userId), host);
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
        NETWORK.execute(() -> {
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
        NETWORK.execute(() -> {
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
