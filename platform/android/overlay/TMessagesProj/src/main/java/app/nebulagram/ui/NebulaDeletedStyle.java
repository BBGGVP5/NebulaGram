package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Color;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * Что именно сохранять из удалённого и как оно выглядит в ленте.
 *
 * <p>Раньше архив был один на всё: включил — и остаются сообщения из всех чатов,
 * а выделение было одно, обесцвечивание. Здесь две независимые настройки. Где
 * сохранять — четыре вида собеседника: личные переписки, группы, каналы и боты.
 * Как выделять — не выделять совсем, приглушить или подложить цвет.
 *
 * <p>По умолчанию всё как было: сохраняем везде, выделяем приглушением. Это
 * поведение прежних сборок, и включённый архив не должен меняться от обновления.
 */
public final class NebulaDeletedStyle {
    private static final String PREFS = "nebulagram";

    private NebulaDeletedStyle() { }

    // --- где сохранять -------------------------------------------------------

    public static final int PRIVATE = 0;
    public static final int GROUPS = 1;
    public static final int CHANNELS = 2;
    public static final int BOTS = 3;

    private static final String[] SCOPE_KEYS = {
            "deleted_scope_private", "deleted_scope_groups",
            "deleted_scope_channels", "deleted_scope_bots",
    };

    public static String scopeTitle(int kind) {
        switch (kind) {
            case GROUPS: return NebulaText.text("Группы", "Groups");
            case CHANNELS: return NebulaText.text("Каналы", "Channels");
            case BOTS: return NebulaText.text("Боты", "Bots");
            default: return NebulaText.text("Личные чаты", "Private chats");
        }
    }

    public static boolean scope(int kind) {
        return prefs().getBoolean(SCOPE_KEYS[clampScope(kind)], true);
    }

    public static void setScope(int kind, boolean enabled) {
        prefs().edit().putBoolean(SCOPE_KEYS[clampScope(kind)], enabled).apply();
    }

    /** Сколько видов выбрано — для подписи строки. */
    public static int scopeCount() {
        int count = 0;
        for (int kind = PRIVATE; kind <= BOTS; kind++) {
            if (scope(kind)) count++;
        }
        return count;
    }

    /**
     * Сохранять ли удалённое из этого чата.
     *
     * <p>Вызывается с очереди базы на каждое событие удаления, поэтому смотрим
     * в те же памятные карты {@code MessagesController}, что и проверка запрета
     * пересылки рядом. Незнакомый чат считаем обычным: пропустить чужое
     * сообщение хуже, чем сохранить лишнее, — сохранённое всегда можно стереть.
     */
    public static boolean retains(int account, long peer) {
        if (DialogObject.isEncryptedDialog(peer)) {
            // Секретные чаты — личная переписка, и у них есть свой переключатель.
            return scope(PRIVATE);
        }
        if (peer < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-peer);
            return scope(chat != null && ChatObject.isChannelAndNotMegaGroup(chat) ? CHANNELS : GROUPS);
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(peer);
        return scope(user != null && user.bot ? BOTS : PRIVATE);
    }

    private static int clampScope(int kind) {
        return Math.max(PRIVATE, Math.min(BOTS, kind));
    }

    // --- как выделять --------------------------------------------------------

    /** Ничем: сообщение выглядит как обычное, только со значком у времени. */
    public static final int MARK_NONE = 0;
    /** Приглушить: прежнее поведение — почти без цвета и чуть прозрачнее. */
    public static final int MARK_FADE = 1;
    /** Подложить цвет: поверх сообщения ложится выбранный оттенок. */
    public static final int MARK_TINT = 2;

    private static final String MARK_KEY = "deleted_mark";
    private static final String COLOUR_KEY = "deleted_mark_colour";

    /** Палитра для подложки. Ноль — «как акцент темы». */
    public static final int[] PALETTE = {
            0, 0xFFE0564A, 0xFFE58A2E, 0xFFDCC02B, 0xFF4FA85C, 0xFF3C8FD0, 0xFF7C5CD6, 0xFFD25AA6,
    };

    public static int mark() {
        return Math.max(MARK_NONE, Math.min(MARK_TINT, prefs().getInt(MARK_KEY, MARK_FADE)));
    }

    public static void setMark(int value) {
        prefs().edit().putInt(MARK_KEY, Math.max(MARK_NONE, Math.min(MARK_TINT, value))).apply();
    }

    public static String markTitle() {
        switch (mark()) {
            case MARK_NONE: return NebulaText.text("Не выделять", "Do not mark");
            case MARK_TINT: return NebulaText.text("Цветом", "With colour");
            default: return NebulaText.text("Приглушением", "By fading");
        }
    }

    /** Выбранный номер в палитре. */
    public static int colourIndex() {
        int value = prefs().getInt(COLOUR_KEY, 0);
        return value >= 0 && value < PALETTE.length ? value : 0;
    }

    public static void setColourIndex(int index) {
        prefs().edit().putInt(COLOUR_KEY, index >= 0 && index < PALETTE.length ? index : 0).apply();
    }

    /** Сам цвет подложки. Ноль в палитре означает акцент текущей темы. */
    public static int colour() {
        int chosen = PALETTE[colourIndex()];
        return chosen != 0 ? chosen : Theme.getColor(Theme.key_chat_messageLinkIn);
    }

    /**
     * Прозрачность подложки. Достаточно, чтобы отличить удалённое сообщение от
     * обычного, и мало, чтобы текст остался читаемым.
     */
    public static int tint() {
        return Color.argb(56, Color.red(colour()), Color.green(colour()), Color.blue(colour()));
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
