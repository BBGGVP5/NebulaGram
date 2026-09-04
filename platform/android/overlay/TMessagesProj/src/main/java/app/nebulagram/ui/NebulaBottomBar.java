package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.glass.GlassTabView;

/** Preferences for Telegram's native tabs and NebulaGram's optional side panel. */
public final class NebulaBottomBar {
    private static final String KEY_ENABLED = "bottom_bar";
    private static final String KEY_SIDEBAR = "side_panel";
    public static final String TAB_CONTACTS = "contacts";
    public static final String TAB_SETTINGS = "settings";
    public static final String TAB_PROFILE = "profile";

    private NebulaBottomBar() { }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }

    public static boolean enabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean value) {
        SharedPreferences.Editor editor = prefs().edit().putBoolean(KEY_ENABLED, value);
        if (!value) {
            editor.putBoolean(KEY_SIDEBAR, true);
        }
        editor.apply();
    }

    public static boolean sidebarEnabled() {
        return prefs().getBoolean(KEY_SIDEBAR, false) || !enabled();
    }

    public static void setSidebarEnabled(boolean value) {
        SharedPreferences.Editor editor = prefs().edit().putBoolean(KEY_SIDEBAR, value);
        if (!value) {
            editor.putBoolean(KEY_ENABLED, true);
            editor.putBoolean("bottom_bar_" + TAB_CONTACTS, true);
            editor.putBoolean("bottom_bar_" + TAB_SETTINGS, true);
            editor.putBoolean("bottom_bar_" + TAB_PROFILE, true);
        }
        editor.apply();
    }

    public static boolean tabEnabled(String tab) {
        return prefs().getBoolean("bottom_bar_" + tab, true);
    }

    public static void setTabEnabled(String tab, boolean value) {
        SharedPreferences.Editor editor = prefs().edit().putBoolean("bottom_bar_" + tab, value);
        // The side panel keeps every destination reachable when tabs are hidden.
        if (!value) {
            editor.putBoolean(KEY_SIDEBAR, true);
        }
        editor.apply();
    }

    private static final String KEY_TAB_LABELS = "tab_labels";

    /** Показывать ли подписи под значками вкладок. */
    public static boolean tabLabels() {
        try {
            return prefs().getBoolean(KEY_TAB_LABELS, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setTabLabels(boolean value) {
        prefs().edit().putBoolean(KEY_TAB_LABELS, value).apply();
    }

    /**
     * Прячет подпись вкладки и ставит значок по центру.
     *
     * <p>Без подписи значок остаётся прижатым к верху и висит над пустотой:
     * место под текст в разметке никуда не девается. Поэтому мало скрыть
     * TextView — надо переопустить и сам значок.
     */
    public static void applyTabLabel(android.view.View icon, android.view.View label) {
        if (tabLabels() || label == null) {
            return;
        }
        label.setVisibility(android.view.View.GONE);
        if (icon != null && icon.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams params =
                    (android.widget.FrameLayout.LayoutParams) icon.getLayoutParams();
            params.gravity = android.view.Gravity.CENTER;
            params.topMargin = 0;
            icon.setLayoutParams(params);
        }
    }

    /** Последняя позиция, на которой мы законно остановились. */
    private static int lastPosition;

    /** Доступна ли страница пейджера: у скрытой вкладки нет кнопки. */
    public static boolean positionEnabled(int position) {
        if (!enabled()) {
            return true; // без панели скрывать нечего
        }
        switch (position) {
            case 1:
                return tabEnabled(TAB_CONTACTS);
            case 2:
                return tabEnabled(TAB_SETTINGS);
            case 3:
                return tabEnabled(TAB_PROFILE);
            default:
                return true;
        }
    }

    /**
     * Куда доехать, если свайп остановился на скрытой вкладке.
     *
     * <p>Запрещать свайп нельзя: скрытые «Контакты» стоят посередине и
     * отрезали бы всё, что за ними. Поэтому пропускаем страницу — едем дальше
     * в ту же сторону до первой включённой, а если её нет, возвращаемся туда,
     * откуда пришли.
     *
     * @return позиция, на которую нужно доехать, или -1, если всё в порядке
     */
    public static int redirectPosition(int landed) {
        if (positionEnabled(landed)) {
            lastPosition = landed;
            return -1;
        }
        int direction = landed >= lastPosition ? 1 : -1;
        for (int position = landed + direction; position >= 0 && position <= 3; position += direction) {
            if (positionEnabled(position)) {
                lastPosition = position;
                return position;
            }
        }
        return lastPosition;
    }

    // Ограничение свайпов убрано намеренно.
    //
    // Скрытая вкладка убирает кнопку, но страница остаётся в пейджере, а
    // перепрыгнуть через неё он не умеет. Пока запрет стоял, скрытые
    // «Контакты» на позиции 1 отрезали и всё, что за ними: свайпы пропадали
    // целиком. Свободный свайп с кнопкой-невидимкой честнее запрета,
    // который ломает жест до соседних экранов.

    /** Native indices: chats, contacts, settings, calls, profile. */
    public static void applyTabs(MainTabsLayout layout, GlassTabView[] tabs,
                                 boolean callsVisible, boolean animated) {
        if (layout == null || tabs == null) {
            return;
        }
        layout.setViewVisible(tabs[0], true, animated);
        layout.setViewVisible(tabs[1], tabEnabled(TAB_CONTACTS), animated);
        layout.setViewVisible(tabs[2], tabEnabled(TAB_SETTINGS) && !callsVisible, animated);
        layout.setViewVisible(tabs[3], tabEnabled(TAB_SETTINGS) && callsVisible, animated);
        layout.setViewVisible(tabs[4], tabEnabled(TAB_PROFILE), animated);
    }
}
