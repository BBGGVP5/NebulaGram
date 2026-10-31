package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Насколько крупно показывать стикеры, эмодзи и GIF.
 *
 * <p>Telegram считает размер стикера от стороны экрана — половину. Настройка
 * не задаёт размер в пикселях, а множит эту долю, поэтому ведёт себя одинаково
 * на узком телефоне и на планшете.
 *
 * <p>GIF делят расчёт с фотографиями и видео, поэтому их доля множится
 * отдельно и только для них: трогать общую коробку значило бы менять заодно
 * размер снимков.
 *
 * <p>Анимированные эмодзи проходят тот же расчёт, что стикеры, но у них есть
 * собственный множитель {@code animatedEmojisZoom}. Настройка множит именно
 * его — поэтому ползунки независимы на деле, а не только на вид.
 */
public final class NebulaMediaSize {
    private static final String PREFS = "nebulagram";
    private static final int MIN = 50, MAX = 150;

    private NebulaMediaSize() { }

    public static int stickerPercent() {
        return clamp(prefs().getInt("sticker_percent", 100));
    }

    public static void setStickerPercent(int value) {
        prefs().edit().putInt("sticker_percent", clamp(value)).apply();
    }

    public static int emojiPercent() {
        return clamp(prefs().getInt("emoji_percent", 100));
    }

    public static void setEmojiPercent(int value) {
        prefs().edit().putInt("emoji_percent", clamp(value)).apply();
    }

    public static int gifPercent() {
        return clamp(prefs().getInt("gif_percent", 100));
    }

    public static void setGifPercent(int value) {
        prefs().edit().putInt("gif_percent", clamp(value)).apply();
    }

    /** Множитель для стикеров. */
    public static float stickerScale() {
        return stickerPercent() / 100f;
    }

    /** Множитель для анимированных эмодзи. */
    public static float emojiScale() {
        return emojiPercent() / 100f;
    }

    /** Множитель для GIF. */
    public static float gifScale() {
        return gifPercent() / 100f;
    }

    public static int minimum() { return MIN; }
    public static int maximum() { return MAX; }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
