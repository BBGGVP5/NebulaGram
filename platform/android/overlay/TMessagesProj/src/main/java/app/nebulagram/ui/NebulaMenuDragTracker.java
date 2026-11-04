package app.nebulagram.ui;

/**
 * Состояние жеста «нажал, протянул, отпустил» поверх открытого меню.
 *
 * <p>Без Android-классов, чтобы проверка могла собрать его отдельно. Меню по
 * удержанию часто открывается прямо под пальцем, поэтому выбор начинается
 * только после сдвига дальше порога: отпустить палец на месте — значит
 * оставить меню открытым, как и раньше.
 */
public final class NebulaMenuDragTracker {
    private boolean tracking, active;
    private float startX, startY, slop;

    /** Меню показано, пока палец ещё на экране. */
    public void begin(float x, float y, float slop) {
        tracking = true;
        active = false;
        startX = x;
        startY = y;
        this.slop = slop;
    }

    /** @return true ровно в тот момент, когда жест становится выбором пункта. */
    public boolean move(float x, float y) {
        if (!tracking || active) return false;
        float dx = x - startX, dy = y - startY;
        if (dx * dx + dy * dy < slop * slop) return false;
        active = true;
        return true;
    }

    public boolean tracking() { return tracking; }

    public boolean active() { return active; }

    public void reset() {
        tracking = false;
        active = false;
    }

    /**
     * Индекс прямоугольника под точкой или -1. Прямоугольники идут четвёрками
     * left, top, right, bottom; при наложении побеждает последний — он выше.
     */
    public static int pick(float x, float y, int[] rects, int count) {
        int found = -1;
        for (int i = 0; i < count; i++) {
            int o = i * 4;
            if (x >= rects[o] && x < rects[o + 2] && y >= rects[o + 1] && y < rects[o + 3]) found = i;
        }
        return found;
    }
}
