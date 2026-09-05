package app.nebulagram.ui;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.glass.GlassTabView;

import java.util.Arrays;

/** Preferences for Telegram's native tabs and overflow navigation. */
public final class NebulaBottomBar {
    private static final String KEY_ENABLED = "bottom_bar";
    private static final String KEY_TAB_ORDER = "bottom_bar_order";
    public static final String TAB_CHATS = "chats";
    public static final String TAB_CONTACTS = "contacts";
    public static final String TAB_SETTINGS = "settings";
    public static final String TAB_PROFILE = "profile";
    private static final String[] DEFAULT_TAB_ORDER = {
            TAB_CHATS, TAB_CONTACTS, TAB_SETTINGS, TAB_PROFILE
    };

    private NebulaBottomBar() { }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }

    public static boolean enabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY_ENABLED, value).apply();
    }

    /** Calls share the settings slot in Telegram. Keep settings reachable in every layout. */
    public static boolean settingsInOverflow(boolean callsTabVisible) {
        return !enabled() || !tabEnabled(TAB_SETTINGS) || callsTabVisible;
    }

    public static boolean tabEnabled(String tab) {
        return prefs().getBoolean("bottom_bar_" + tab, true);
    }

    public static void setTabEnabled(String tab, boolean value) {
        prefs().edit().putBoolean("bottom_bar_" + tab, value).apply();
    }

    /** Ordered logical tabs. Settings and Calls share the same pager slot. */
    public static String[] tabOrder() {
        String[] order = prefs().getString(KEY_TAB_ORDER, "").split(",");
        if (!validOrder(order)) {
            return DEFAULT_TAB_ORDER.clone();
        }
        return order;
    }

    public static void setTabOrder(String[] order) {
        if (!validOrder(order)) {
            return;
        }
        prefs().edit().putString(KEY_TAB_ORDER, android.text.TextUtils.join(",", order)).apply();
    }

    public static void moveTab(int from, int to) {
        String[] order = tabOrder();
        if (from < 0 || from >= order.length || to < 0 || to >= order.length || from == to) {
            return;
        }
        String moving = order[from];
        if (from < to) {
            System.arraycopy(order, from + 1, order, from, to - from);
        } else {
            System.arraycopy(order, to, order, to + 1, from - to);
        }
        order[to] = moving;
        setTabOrder(order);
    }

    private static boolean validOrder(String[] order) {
        if (order == null || order.length != DEFAULT_TAB_ORDER.length) {
            return false;
        }
        String[] sorted = order.clone();
        String[] expected = DEFAULT_TAB_ORDER.clone();
        Arrays.sort(sorted);
        Arrays.sort(expected);
        return Arrays.equals(sorted, expected);
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

    public static boolean compact() {
        return prefs().getBoolean("compact_bottom_bar", false);
    }

    public static void setCompact(boolean value) {
        prefs().edit().putBoolean("compact_bottom_bar", value).apply();
    }

    /** Labels can grow beyond this minimum; touch targets stay at least 72 dp wide. */
    public static int minimumTabsWidth(int visibleCount) {
        return compact() ? Math.min(320, Math.max(1, visibleCount) * 72) : 320;
    }

    /**
     * Прячет подпись вкладки и ставит значок по центру.
     *
     * <p>Без подписи значок остаётся прижатым к верху и висит над пустотой:
     * место под текст в разметке никуда не девается. Поэтому мало скрыть
     * TextView — надо переопустить и сам значок.
     */
    public static void applyTabLabel(android.view.View icon, android.view.View label) {
        if (label == null) {
            return;
        }
        boolean show = tabLabels();
        label.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (icon != null && icon.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams params =
                    (android.widget.FrameLayout.LayoutParams) icon.getLayoutParams();
            // Без подписи значок встаёт по центру, с подписью — под верх, как
            // задумано в Telegram. Возврат важен не меньше скрытия: иначе
            // включить подписи обратно означало бы перезапуск.
            params.gravity = show
                    ? (android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP)
                    : android.view.Gravity.CENTER;
            params.topMargin = show ? AndroidUtilities.dp(4) : 0;
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

    /** Target for a swipe, skipping pages whose bottom tabs are hidden. */
    public static int nextEnabledPosition(int current, boolean forward) {
        int direction = forward ? 1 : -1;
        for (int position = current + direction; position >= 0 && position <= 3;
             position += direction) {
            if (positionEnabled(position)) {
                return position;
            }
        }
        return forward ? 4 : -1;
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
        if (layout == null || tabs == null || tabs.length < 5) {
            return;
        }

        // Reorder the real native views. Their click handlers keep the pager
        // destinations intact, while the bar follows the organizer.
        for (String tab : tabOrder()) {
            if (TAB_CHATS.equals(tab)) {
                layout.bringChildToFront(tabs[0]);
            } else if (TAB_CONTACTS.equals(tab)) {
                layout.bringChildToFront(tabs[1]);
            } else if (TAB_SETTINGS.equals(tab)) {
                layout.bringChildToFront(tabs[2]);
                layout.bringChildToFront(tabs[3]);
            } else if (TAB_PROFILE.equals(tab)) {
                layout.bringChildToFront(tabs[4]);
            }
        }
                // Подписи применяем здесь же: экран настроек вызывает обновление при
        // возврате в чаты, и перезапуск приложения не нужен.
        for (GlassTabView tab : tabs) {
            if (tab != null) {
                tab.nebulaApplyLabel();
            }
        }
        layout.setViewVisible(tabs[0], true, animated);
        layout.setViewVisible(tabs[1], tabEnabled(TAB_CONTACTS), animated);
        layout.setViewVisible(tabs[2], tabEnabled(TAB_SETTINGS) && !callsVisible, animated);
        layout.setViewVisible(tabs[3], tabEnabled(TAB_SETTINGS) && callsVisible, animated);
        layout.setViewVisible(tabs[4], tabEnabled(TAB_PROFILE), animated);
        layout.requestLayout();
    }
}
