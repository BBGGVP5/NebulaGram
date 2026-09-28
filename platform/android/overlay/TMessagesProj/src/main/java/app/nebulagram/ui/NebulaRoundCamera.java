package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BottomSheet;

/**
 * С какой камеры открывается видеосообщение — «кружок».
 *
 * <p>У Telegram выбора нет: на старом Camera1 кружок всегда начинался с
 * фронтальной, на Camera2 — с той, что осталась с прошлого раза, и повлиять на
 * это было нельзя. Кто снимает кружки не себя, а происходящее вокруг, каждый раз
 * переворачивал камеру вручную.
 */
public final class NebulaRoundCamera {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "round_camera";
    private static final String KEY_LAST = "round_camera_last_front";

    /** Та, которой снимали в прошлый раз. */
    public static final int LAST = 0;
    /** Всегда фронтальная. */
    public static final int FRONT = 1;
    /** Всегда основная. */
    public static final int BACK = 2;
    /** Спрашивать непосредственно перед записью кружка. */
    public static final int ASK = 3;

    private NebulaRoundCamera() { }

    public static int mode() {
        return Math.max(LAST, Math.min(ASK, prefs().getInt(KEY, ASK)));
    }

    public static void setMode(int value) {
        prefs().edit().putInt(KEY, Math.max(LAST, Math.min(ASK, value))).apply();
    }

    public static String title() {
        switch (mode()) {
            case FRONT: return NebulaText.text("Фронтальная", "Front");
            case BACK: return NebulaText.text("Основная", "Rear");
            case ASK: return NebulaText.text("Спрашивать", "Ask");
            default: return NebulaText.text("Как в прошлый раз", "Last used");
        }
    }

    /** A deliberate camera choice starts recording; dismissing leaves it idle. */
    public static void askForRecording(android.content.Context context,
                                       org.telegram.ui.ActionBar.Theme.ResourcesProvider provider,
                                       Runnable onSelected, Runnable onCancel) {
        if (context == null) {
            onCancel.run();
            return;
        }
        final boolean[] selected = {false};
        BottomSheet sheet = new NebulaDialog.Builder(context, provider)
                .setTitle(NebulaText.text("Камера кружка", "Round video camera"))
                .setSelectedIndex(prefs().getBoolean(KEY_LAST, true) ? 0 : 1).setItems(new CharSequence[]{
                        NebulaText.text("Фронтальная", "Front"),
                        NebulaText.text("Основная", "Rear"),
                }, (dialog, which) -> {
                    selected[0] = true;
                    remember(which == 0);
                    onSelected.run();
                })
                .setNegativeButton(NebulaText.text("Отмена", "Cancel"), null)
                .show();
        sheet.setOnDismissListener(dialog -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (!selected[0]) onCancel.run();
        }));
    }

    /**
     * Какую камеру открывать. {@code current} — та, что стоит сейчас: для
     * режима «как в прошлый раз» ответ зависит от неё и от запомненного выбора.
     */
    public static boolean frontface(boolean current) {
        switch (mode()) {
            case FRONT: return true;
            case BACK: return false;
            // «Спрашивать» тоже опирается на запомненное: ответ уже дан при
            // переходе в режим кружка, здесь его остаётся применить.
            default: return prefs().getBoolean(KEY_LAST, current);
        }
    }

    /** Запоминаем разворот камеры: только для режима «как в прошлый раз». */
    public static void remember(boolean frontface) {
        prefs().edit().putBoolean(KEY_LAST, frontface).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
