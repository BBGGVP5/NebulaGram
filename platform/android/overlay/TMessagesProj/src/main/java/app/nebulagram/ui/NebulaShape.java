package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Форма поверхностей: скругления, форма фото профиля, хвосты у сообщений.
 *
 * <p>Скругление здесь одно на всё приложение. Раньше оно было только у
 * аватарок и жило отдельной настройкой с процентами; карточки, меню и кнопки
 * при этом имели зашитые радиусы, и подогнать их друг к другу было нельзя.
 *
 * <p>Формы фото профиля не нуждаются в новом механизме: {@code ImageReceiver}
 * уже принимает четыре угла по отдельности, и всё, что выражается радиусами
 * углов, выражается и здесь. Поэтому в списке нет «скошенного» — срез угла
 * радиусом не задаётся, и показывать пункт, который нарисуется скруглением,
 * значило бы обещать не то.
 */
public final class NebulaShape {
    private static final String PREFS = "nebulagram";

    private NebulaShape() { }

    // --- Единые скругления -------------------------------------------------

    /** Насколько скруглены поверхности, 0..100. */
    public static int roundness() {
        return Math.max(0, Math.min(100, prefs().getInt("shape_roundness", 100)));
    }

    public static void setRoundness(int value) {
        prefs().edit().putInt("shape_roundness", Math.max(0, Math.min(100, value))).apply();
    }

    /** Включено ли единое скругление; иначе каждая поверхность со своим. */
    public static boolean uniform() {
        return prefs().getBoolean("shape_uniform", false);
    }

    public static void setUniform(boolean value) {
        prefs().edit().putBoolean("shape_uniform", value).apply();
    }

    /** Подпись ближайшей именованной ступени. */
    public static String roundnessTitle() {
        int value = roundness();
        if (value <= 16) return NebulaText.text("Прямые", "Straight");
        if (value <= 49) return NebulaText.text("Мягкие", "Soft");
        if (value <= 83) return NebulaText.text("Сквиркл", "Squircle");
        return NebulaText.text("Круглые", "Round");
    }

    /**
     * Радиус поверхности в пикселях. {@code naturalDp} — радиус, который у неё
     * был до настройки: при выключенном единым скруглении он и возвращается,
     * так что ни одна поверхность не меняется, пока это не попросят.
     */
    public static int radius(float naturalDp) {
        float dp = uniform() ? naturalDp * roundness() / 100f : naturalDp;
        return org.telegram.messenger.AndroidUtilities.dp(dp);
    }

    // --- Форма фото профиля ------------------------------------------------

    public static final int CIRCLE = 0, SQUIRCLE = 1, ROUNDED = 2, SQUARE = 3, LEAF = 4, DROP = 5;
    private static final int SHAPES = 6;

    /**
     * Доля половины стороны для каждого угла, по часовой от левого верхнего.
     * Круг — половина во всех углах; остальное — та же величина, ужатая или
     * выборочно снятая.
     */
    private static final float[][] CORNERS = {
        {1f, 1f, 1f, 1f},          // круг
        {.78f, .78f, .78f, .78f},  // сквиркл
        {.46f, .46f, .46f, .46f},  // скруглённый
        {0f, 0f, 0f, 0f},          // квадрат
        {1f, .22f, 1f, .22f},      // лепесток
        {1f, 1f, .22f, 1f},        // капля
    };

    public static int avatarShape() {
        return Math.max(0, Math.min(SHAPES - 1, prefs().getInt("avatar_shape", CIRCLE)));
    }

    public static void setAvatarShape(int value) {
        prefs().edit().putInt("avatar_shape", Math.max(0, Math.min(SHAPES - 1, value))).apply();
    }

    public static int shapeCount() { return SHAPES; }

    public static String shapeTitle(int shape) {
        switch (shape) {
            case SQUIRCLE: return NebulaText.text("Сквиркл", "Squircle");
            case ROUNDED: return NebulaText.text("Скруглённый", "Rounded");
            case SQUARE: return NebulaText.text("Квадрат", "Square");
            case LEAF: return NebulaText.text("Лепесток", "Leaf");
            case DROP: return NebulaText.text("Капля", "Drop");
            default: return NebulaText.text("Круг", "Circle");
        }
    }

    /** Доли для произвольной формы — для превью, которое рисует все сразу. */
    public static float[] cornerFactors(int shape) {
        return CORNERS[Math.max(0, Math.min(SHAPES - 1, shape))];
    }

    /**
     * Радиусы четырёх углов фото профиля для стороны {@code side} в пикселях,
     * в порядке {@code ImageReceiver.setRoundRadius}.
     *
     * <p>Пишет в чужой массив намеренно: вызывается это из {@code draw}, по
     * разу на каждое фото в каждом кадре, и возвращать оттуда новый массив
     * значит сорить в самом горячем месте отрисовки.
     */
    public static void avatarRadii(int side, int[] out) {
        float[] factors = cornerFactors(avatarShape());
        float half = side * .5f;
        // Старая настройка процентов остаётся главной: пока её не трогали, она
        // равна 100 и ничего не ужимает, а если пользователь её уводил — форма
        // подчиняется ей, а не спорит с ней.
        float scale = Math.max(0, Math.min(100, NebulaAppearance.avatarRound())) / 100f;
        for (int i = 0; i < 4; i++) out[i] = Math.round(half * factors[i] * scale);
    }

    /** То же для превью, где кадр один и лишний массив ничего не стоит. */
    public static int[] avatarRadii(int side) {
        int[] radii = new int[4];
        avatarRadii(side, radii);
        return radii;
    }

    // --- Хвосты у сообщений ------------------------------------------------

    public static boolean tails() {
        return prefs().getBoolean("message_tails", true);
    }

    public static void setTails(boolean value) {
        prefs().edit().putBoolean("message_tails", value).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
