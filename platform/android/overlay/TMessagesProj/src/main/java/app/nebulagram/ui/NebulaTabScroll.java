package app.nebulagram.ui;

/**
 * Скрытие нижней панели при прокрутке списка чатов — с порогом.
 *
 * <p>Панель не реагирует на лёгкое касание списка: скрывается она, только когда
 * палец непрерывно проехал вниз заметное расстояние, и возвращается, когда
 * список так же уверенно тянут назад или он упёрся в начало. Смена направления
 * обнуляет пройденный путь, поэтому дрожь пальца на месте ничего не меняет.
 * Класс без Android-зависимостей: пороги в пикселях передаёт вызывающий.
 */
public final class NebulaTabScroll {
    private float travelled;
    private boolean hidden;

    /**
     * @param dy        смещение списка (больше нуля — содержимое уходит вверх)
     * @param atTop     список в самом начале
     * @param manual    прокрутку ведёт палец, а не программа
     * @return true, если видимость панели изменилась
     */
    public boolean scrolled(float dy, boolean atTop, boolean manual, float hideDistance, float showDistance) {
        boolean was = hidden;
        if (atTop) {
            travelled = 0;
            hidden = false;
            return was != hidden;
        }
        if (!manual || dy == 0) return false;
        if (travelled != 0 && Math.signum(travelled) != Math.signum(dy)) travelled = 0;
        travelled += dy;
        if (!hidden && travelled >= hideDistance) {
            hidden = true;
            travelled = 0;
        } else if (hidden && -travelled >= showDistance) {
            hidden = false;
            travelled = 0;
        }
        return was != hidden;
    }

    public boolean hidden() { return hidden; }

    public void reset() {
        travelled = 0;
        hidden = false;
    }

    // --- общее состояние экрана чатов ---------------------------------------

    private static final NebulaTabScroll main = new NebulaTabScroll();

    public static boolean enabled() {
        try {
            return NebulaFeatureSettings.enabled("tabs_hide_on_scroll");
        } catch (Throwable e) {
            return false;
        }
    }

    /** Хук из {@code DialogsActivity}: true — пора перепроверить видимость панели. */
    public static boolean onListScrolled(int dy, boolean atTop, boolean manual) {
        if (!enabled()) {
            boolean was = main.hidden;
            main.reset();
            return was;
        }
        return main.scrolled(dy, atTop, manual,
                org.telegram.messenger.AndroidUtilities.dp(72), org.telegram.messenger.AndroidUtilities.dp(32));
    }

    public static boolean visible() {
        return !enabled() || !main.hidden;
    }

    public static void show() {
        main.reset();
    }
}
