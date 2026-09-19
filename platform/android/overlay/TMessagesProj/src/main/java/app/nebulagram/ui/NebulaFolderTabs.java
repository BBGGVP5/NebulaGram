package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * Где стоят вкладки папок — над списком чатов или под ним.
 *
 * <p>Снизу до них достаёт большой палец, и это единственная причина настройки.
 * Всё остальное в этом классе — следствия: панель, ушедшая вниз, перестаёт быть
 * частью верхней стопки, где живут поиск, истории и заголовок, и её высота не
 * должна двигать ни их, ни список.
 *
 * <p>Высота панели при этом никуда не девается: внизу её нужно прибавить к тому
 * месту, которое уже резервирует нижняя панель, иначе последний чат в списке
 * окажется под вкладками.
 */
public final class NebulaFolderTabs {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "folder_tabs_bottom";

    /** Высота панели вкладок вместе с её внутренними отступами, в точках. */
    public static final int HEIGHT_DP = 36 + 7 + 7;
    /** Зазор между вкладками и списком, чтобы они не слипались. */
    private static final int GAP_DP = 4;

    private NebulaFolderTabs() { }

    public static boolean bottom() {
        return preferences().getBoolean(KEY, false);
    }

    public static void setBottom(boolean value) {
        preferences().edit().putBoolean(KEY, value).apply();
        // Список чатов — корневой экран, его никто не пересоздаёт при возврате
        // из настроек. Без этого переключатель оживал бы только после полного
        // перезапуска приложения, и выглядел бы сломанным.
        notifyChanged();
    }

    public static final int GLASS = 0, ORDINARY = 1, MINIMAL = 2;

    /** Separate from folder_style, which controls labels versus icons. */
    public static int panelStyle() {
        return Math.max(0, Math.min(2, preferences().getInt("folder_panel_style", 0)));
    }

    public static void setPanelStyle(int value) {
        preferences().edit().putInt("folder_panel_style", Math.max(0, Math.min(2, value))).apply();
        notifyChanged();
    }

    public static String panelStyleTitle(int style) {
        switch (style) {
            case ORDINARY: return NebulaText.text("Обычные", "Ordinary");
            case MINIMAL: return NebulaText.text("Минималистичные", "Minimal");
            default: return NebulaText.text("iOS — жидкое стекло", "iOS — Liquid Glass");
        }
    }

    private static void notifyChanged() {
        Runnable listener = onChanged;
        if (listener != null) listener.run();
    }

    /** Compact home has no floating top content requiring a rectangular blur. */
    public static boolean flatHeader(boolean topFolders, boolean stories, boolean searchSlot,
            float search, float actionMode, float rightSliding) {
        return !topFolders && !stories && !searchSlot && search == 0f
                && actionMode == 0f && rightSliding == 0f;
    }

    private static Runnable onChanged;

    /** Список чатов сообщает, что умеет переставить панель на ходу. */
    public static void setChangeListener(Runnable listener) {
        onChanged = listener;
    }

    /** Гравитация для панели вкладок. */
    public static int gravity() {
        return bottom() ? android.view.Gravity.BOTTOM : android.view.Gravity.TOP;
    }

    /**
     * Сколько места снизу занимают вкладки. Ноль, когда они наверху, — тогда их
     * место считает обычная верхняя стопка.
     */
    public static int reserved() {
        return bottom() ? AndroidUtilities.dp(HEIGHT_DP + GAP_DP) : 0;
    }

    /** Content coordinates; top chrome must never be added to a bottom-anchored tab. */
    public static int bottomTop(int contentHeight, int tabHeight, int systemBottom, int mainTabsHeight) {
        return Math.max(0, contentHeight - tabHeight - systemBottom - mainTabsHeight
                - AndroidUtilities.dp(GAP_DP));
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
