package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;

/** The same tab renderer as the chat list, with isolated example folders. */
public final class NebulaFoldersPreview extends FrameLayout {
    private final FilterTabsView tabs;
    private final View sampleChats;
    private final org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor glassSource =
            new org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor();
    private int style = -1;
    private boolean hidden, counters;
    public NebulaFoldersPreview(Context c) {
        super(c);
        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20), NebulaTheme.of(c).surfaceContainer()));
        sampleChats = new View(c) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                paint.setColor(NebulaTheme.of(getContext()).onSurfaceVariant());
                for (int row = 0; row < 2; row++) {
                    float y = AndroidUtilities.dp(24 + row * 44);
                    paint.setAlpha(35);
                    canvas.drawCircle(AndroidUtilities.dp(32), y, AndroidUtilities.dp(16), paint);
                    float left = AndroidUtilities.dp(60);
                    paint.setAlpha(60);
                    canvas.drawRoundRect(left, y - AndroidUtilities.dp(10),
                            getWidth() * (row == 0 ? .65f : .53f), y - AndroidUtilities.dp(3),
                            AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
                    paint.setAlpha(25);
                    canvas.drawRoundRect(left, y + AndroidUtilities.dp(4), getWidth() - AndroidUtilities.dp(32),
                            y + AndroidUtilities.dp(10), AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
                }
            }
        };
        sampleChats.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(sampleChats, LayoutHelper.createFrame(-1, 92, Gravity.TOP));
        tabs = new FilterTabsView(c, null);
        org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable glass =
                new org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory(glassSource)
                        .create(tabs, org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl.topPanel(null));
        glass.setRadius(AndroidUtilities.dp(18));
        glass.setPadding(AndroidUtilities.dp(7));
        tabs.setBlurredBackground(glass);
        tabs.setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));
        tabs.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override public void onPageSelected(FilterTabsView.Tab tab, boolean forward) { }
            @Override public void onPageScrolled(float progress) { }
            @Override public void onSamePageSelected() { }
            @Override public int getTabCounter(int tabId) { return NebulaAppearance.hideTabCounters() ? 0 : tabId == 0 ? 7 : 0; }
            @Override public boolean didSelectTab(FilterTabsView.TabView view, boolean selected) { return false; }
            @Override public boolean isTabMenuVisible() { return false; }
            @Override public void onDeletePressed(int id) { }
            @Override public void onPageReorder(int fromId, int toId) { }
            @Override public boolean canPerformActions() { return true; }
        });
        addView(tabs, LayoutHelper.createFrame(-1, NebulaFolderTabs.HEIGHT_DP, Gravity.TOP, 8, 8, 8, 8));
        refresh();
    }
    public void refresh() {
        glassSource.setColor(NebulaTheme.of(getContext()).surfaceContainer());
        tabs.updateColors();
        if (style != NebulaAppearance.folderStyle() || hidden != NebulaAppearance.hideAllChats() || counters != NebulaAppearance.hideTabCounters()) {
            style = NebulaAppearance.folderStyle(); hidden = NebulaAppearance.hideAllChats(); counters = NebulaAppearance.hideTabCounters();
            tabs.removeTabs();
            if (!hidden) tabs.addTab(0, 0, LocaleController.getString(R.string.FilterAllChats), true, true, false);
            String[] names = {NebulaText.text("Личные", "Personal"), NebulaText.text("Группы", "Groups"), NebulaText.text("Каналы", "Channels")};
            for (int i = 0; i < names.length; i++) tabs.addTab(i + 1, i + 1, names[i], true, false, false);
            tabs.finishAddingTabs(false);
        }
        boolean bottom = NebulaFolderTabs.bottom();
        tabs.setNebulaBottomPanel(bottom);
        FrameLayout.LayoutParams tabParams = (FrameLayout.LayoutParams) tabs.getLayoutParams();
        tabParams.gravity = bottom ? Gravity.BOTTOM : Gravity.TOP;
        tabs.setLayoutParams(tabParams);
        FrameLayout.LayoutParams chatParams = (FrameLayout.LayoutParams) sampleChats.getLayoutParams();
        chatParams.topMargin = AndroidUtilities.dp(bottom ? 8 : 62);
        sampleChats.setLayoutParams(chatParams);
        tabs.getTabsContainer().invalidateViews();
        tabs.invalidate();
    }
    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(164), MeasureSpec.EXACTLY)); }
}
