package app.nebulagram.ui;

/**
 * Чистая математика для индикатора загрузки и смены папок — без Android,
 * чтобы проверка могла собрать её javac'ом и прогнать на фикстурах.
 *
 * <p>Фигуры индикатора задаются в полярных координатах: радиус
 * {@code 1 + a·cos(n·θ)}, нормированный так, чтобы самая дальняя точка
 * касалась окружности. Перетекание одной фигуры в другую — поточечная
 * смесь двух радиусов при одном и том же угле, поэтому форма меняется
 * без скачков даже между разным числом лепестков.
 */
public final class NebulaMotionShapes {
    private NebulaMotionShapes() { }

    /** Число лепестков у фигур цикла: круг, 9 волн, клевер, овал, «солнце», пятиугольник, цветок. */
    static final int[] LOBES = {0, 9, 4, 2, 12, 5, 7};
    /** Глубина волны для каждой фигуры, доля радиуса. */
    static final float[] DEPTH = {0f, .07f, .13f, .11f, .045f, .075f, .09f};
    /** Сколько фигура стоит на месте и сколько перетекает в следующую, мс. */
    static final long HOLD = 420, MORPH = 380;
    static final long STEP = HOLD + MORPH;

    /** Фигура для «уменьшения движения»: мягкий цветок без перетекания. */
    static final int CALM = 1;

    /** Номер фигуры, из которой сейчас идёт перетекание. */
    public static int shapeAt(long time) {
        long cycle = Math.floorMod(time, STEP * LOBES.length);
        return (int) (cycle / STEP);
    }

    /** Доля перетекания 0..1 в следующую фигуру, с мягким разгоном и торможением. */
    public static float morphAt(long time) {
        long inStep = Math.floorMod(time, STEP);
        if (inStep < HOLD) return 0f;
        float t = (inStep - HOLD) / (float) MORPH;
        return t * t * (3f - 2f * t);
    }

    /**
     * Радиус фигуры в долях внешнего радиуса (0..1].
     *
     * @param shape  фигура, из которой идём
     * @param morph  доля перехода в следующую
     * @param angle  угол точки, радианы
     * @param phase  поворот лепестков, радианы
     * @param settle 0 — полная фигура, 1 — ровный круг (финал загрузки)
     */
    public static float radius(int shape, float morph, double angle, double phase, float settle) {
        int next = (shape + 1) % LOBES.length;
        float a = single(shape, angle, phase);
        float b = single(next, angle, phase);
        float r = a + (b - a) * clamp(morph);
        return r + (1f - r) * clamp(settle);
    }

    static float single(int shape, double angle, double phase) {
        float depth = DEPTH[shape];
        if (depth == 0f) return 1f;
        double wave = Math.cos(LOBES[shape] * (angle - phase));
        return (float) ((1.0 + depth * wave) / (1.0 + depth));
    }

    /** Сколько точек строить на полный оборот: мелкому значку хватит меньшего. */
    public static int points(boolean mini) {
        return mini ? 48 : 96;
    }

    /** Ниже этой доли прогресс считаем неопределённым: файл ждёт сети или очереди. */
    static final float IDLE = .02f;

    /**
     * Какая часть контура светится: {@code out[0]} — начало в долях оборота
     * (поверх вращения Telegram), {@code out[1]} — длина.
     *
     * <p>При известном проценте это ровно процент. Пока его нет («Соединение…»,
     * очередь) светится «дышащий» отрезок 12–34% оборота, который ещё и
     * обгоняет вращение, — иначе на экране оставалась бы крутящаяся точка.
     */
    public static void sweep(float progress, float exit, long time, boolean calm, float[] out) {
        if (exit > 0f) { out[0] = 0f; out[1] = 1f; return; }
        float p = clamp(progress);
        if (p >= IDLE) { out[0] = 0f; out[1] = p; return; }
        if (calm) { out[0] = 0f; out[1] = .25f; return; }
        double breath = (Math.sin(time / 1100.0 * Math.PI * 2) + 1) / 2;
        out[1] = (float) (.12 + .22 * breath);
        out[0] = (float) ((time % 1400) / 1400.0);
    }

    /** Яркость дорожки: пока процента нет, фигура должна читаться целиком. */
    public static float trackAlpha(float progress, float exit) {
        return exit > 0f || progress >= IDLE ? .3f : .55f;
    }

    /** Лёгкий «вдох» при завершении: 1 → 1.12 → 1 по синусу. */
    public static float pulse(float exit) {
        return 1f + .12f * (float) Math.sin(Math.PI * clamp(exit));
    }

    /** Галочка рисуется за первые 45% выхода и гаснет в последние 35%. */
    public static float checkTrim(float exit) {
        return clamp(exit / .45f);
    }

    public static float checkAlpha(float exit) {
        float e = clamp(exit);
        return e < .65f ? 1f : clamp((1f - e) / .35f);
    }

    // --- смена папок --------------------------------------------------------

    public static final int SLIDE = 0, CARDS = 1, FADE = 2, DEPTH_STYLE = 3, INSTANT = 4;
    public static final int STYLES = 5;

    /**
     * Видимое положение страницы папки.
     *
     * <p>Пейджер Telegram двигает страницы {@code translationX} и сам же
     * читает это значение при свайпе, поэтому логический сдвиг не трогаем:
     * всё, что ниже, — поправка поверх него. {@code out[0]} — добавка к
     * сдвигу, {@code out[1]} — масштаб, {@code out[2]} — прозрачность,
     * {@code out[3]} — правая граница видимой части в координатах страницы
     * (или ширина, если резать не нужно), {@code out[4]} — радиус углов в
     * долях от максимального.
     *
     * @param offset логический сдвиг страницы в долях её ширины, -1..1
     */
    public static void page(int style, float offset, float width, float[] out) {
        float f = Math.max(-1f, Math.min(1f, offset));
        float away = Math.abs(f);
        out[0] = 0f; out[1] = 1f; out[2] = 1f; out[3] = width; out[4] = 0f;
        if (away == 0f || width <= 0f) return;
        switch (style) {
            case CARDS: {
                float bend = (float) Math.sin(Math.PI * away);
                out[1] = 1f - .08f * bend;
                out[4] = bend;
                break;
            }
            case FADE: {
                // Страница почти не едет: 12% ширины вместо всей.
                out[0] = f * width * (.12f - 1f);
                float near = 1f - away;
                out[2] = clamp((near - .3f) / .7f);
                out[1] = .94f + .06f * near;
                break;
            }
            case DEPTH_STYLE: {
                if (f < 0f) {
                    // Левая страница уходит под правую: едет втрое медленнее,
                    // чуть уменьшается, и её край режется по краю правой —
                    // так порядок отрисовки соседей не важен.
                    float visual = f * width * .3f;
                    out[0] = visual - f * width;
                    out[1] = 1f - .06f * away;
                    out[2] = 1f - .35f * away;
                    // Граница клипа задаётся до масштаба вокруг центра.
                    float rightEdge = (1f + f) * width;
                    float half = width / 2f;
                    out[3] = Math.max(0f, Math.min(width, half + (rightEdge - visual - half) / out[1]));
                }
                break;
            }
            case INSTANT: {
                out[0] = -f * width;
                out[2] = away < .5f ? 1f : 0f;
                break;
            }
            default:
                break;
        }
    }

    static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
