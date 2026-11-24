package app.nebulagram.ui;

/**
 * "Default profile tab": which shared-media tab a profile opens on. Auto keeps Telegram's choice
 * (the owner's main tab, then posts, gifts, the first non-empty media). A chosen tab wins only when
 * the profile has it; otherwise Telegram's choice stays, so the strip never selects a missing tab.
 */
public final class NebulaProfileTabs {
    private NebulaProfileTabs() { }
    public static final String KEY = "profile_default_tab";
    // SharedMediaLayout.TAB_* ids, copied so the pure decision can be checked without Telegram.
    static final int PHOTOVIDEO = 0, FILES = 1, VOICE = 2, LINKS = 3, AUDIO = 4, GIF = 5, COMMON_GROUPS = 6,
            STORIES = 8, RECOMMENDED = 10, SAVED_DIALOGS = 11, GIFTS = 14;
    private static final int PROFILE_VIEW = 1;
    /** Index in the chooser to tab id; 0 is Auto. */
    static final int[] TABS = {-1, PHOTOVIDEO, STORIES, GIFTS, FILES, LINKS, AUDIO, VOICE, GIF};

    public static String[] titles() {
        return new String[]{
                NebulaText.text("Автоматически", "Automatic"), NebulaText.text("Медиа", "Media"),
                NebulaText.text("Публикации", "Posts"), NebulaText.text("Подарки", "Gifts"),
                NebulaText.text("Файлы", "Files"), NebulaText.text("Ссылки", "Links"),
                NebulaText.text("Музыка", "Music"), NebulaText.text("Голосовые", "Voice"), "GIF"};
    }
    public static int mode() { return NebulaMessagePreferences.number(KEY, 0, 0, TABS.length - 1); }
    public static void setMode(int mode) { NebulaMessagePreferences.set(KEY, Math.max(0, Math.min(TABS.length - 1, mode))); }
    public static String title() { return titles()[mode()]; }

    /** SharedMediaLayout constructor: the tab to open, given Telegram's computed one. */
    public static int initial(int computed, int requested, int viewType, long topicId, int[] hasMedia, boolean stories, boolean gifts) {
        return choose(mode(), computed, requested, viewType, topicId, hasMedia, stories, gifts);
    }

    static int choose(int mode, int computed, int requested, int viewType, long topicId, int[] hasMedia, boolean stories, boolean gifts) {
        if (mode <= 0 || mode >= TABS.length || viewType != PROFILE_VIEW || topicId != 0) return computed;
        // A caller that asked for a tab (gifts button, common groups, similar channels) keeps it.
        if (requested == GIFTS || requested == RECOMMENDED || requested == SAVED_DIALOGS || requested == COMMON_GROUPS) return computed;
        int tab = TABS[mode];
        boolean present;
        if (tab == STORIES) present = stories;
        else if (tab == GIFTS) present = gifts;
        else present = hasMedia != null && tab < hasMedia.length && (hasMedia[tab] == -1 || hasMedia[tab] > 0);
        return present ? tab : computed;
    }
}
