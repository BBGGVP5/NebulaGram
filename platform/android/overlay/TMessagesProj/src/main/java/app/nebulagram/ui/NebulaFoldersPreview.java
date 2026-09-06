package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;

/** The same tab renderer as the chat list, with isolated example folders. */
public final class NebulaFoldersPreview extends FrameLayout {
    private final FilterTabsView tabs;
    private int style = -1;
    private boolean hidden, counters;
    public NebulaFoldersPreview(Context c) {
        super(c);
        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20), NebulaTheme.of(c).surfaceContainer()));
        tabs = new FilterTabsView(c, null);
        tabs.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override public void onPageSelected(FilterTabsView.Tab tab, boolean forward) { }
            @Override public void onPageScrolled(float progress) { }
            @Override public void onSamePageSelected() { }
            @Override public int getTabCounter(int tabId) { return NebulaAppearance.hideTabCounters() ? 0 : tabId == 0 ? 7 : 0; }
            @Override public boolean didSelectTab(FilterTabsView.TabView view, boolean selected) { return false; }
            @Override public boolean isTabMenuVisible() { return false; }
            @Override public void onDeletePressed(int id) { }
            @Override public void onPageReorder(int fromId, int toId) { }
            @Override public boolean canPerformActions() { return false; }
        });
        addView(tabs, LayoutHelper.createFrame(-1, 44, android.view.Gravity.CENTER, 12, 0, 12, 0));
        refresh();
    }
    public void refresh() {
        if (style != NebulaAppearance.folderStyle() || hidden != NebulaAppearance.hideAllChats() || counters != NebulaAppearance.hideTabCounters()) {
            style = NebulaAppearance.folderStyle(); hidden = NebulaAppearance.hideAllChats(); counters = NebulaAppearance.hideTabCounters();
            tabs.removeTabs();
            if (!hidden) tabs.addTab(0, 0, Emoji.replaceEmoji("💬 " + LocaleController.getString(R.string.FilterAllChats), null, false), true, true, false);
            String[] names = {NebulaText.text("👥 Группы", "👥 Groups"), NebulaText.text("🤖 Боты", "🤖 Bots"), NebulaText.text("📢 Каналы", "📢 Channels"), NebulaText.text("🏠 Личное", "🏠 Personal")};
            for (int i = 0; i < names.length; i++) tabs.addTab(i + 1, i + 1, Emoji.replaceEmoji(names[i], null, false), true, false, false);
            tabs.finishAddingTabs(false);
        }
        tabs.invalidate();
    }
    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(88), MeasureSpec.EXACTLY)); }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) { return true; }
}
