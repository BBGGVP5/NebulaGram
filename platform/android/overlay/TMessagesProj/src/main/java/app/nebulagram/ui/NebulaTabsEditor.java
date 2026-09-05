package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;

/** Native Telegram tabs with NebulaGram's tap/drag organization gestures. */
public class NebulaTabsEditor extends FrameLayout {
    private final MainTabsLayout tabs;
    private final java.util.Map<String, GlassTabView> tabViews = new java.util.HashMap<>();
    private final int touchSlop;
    private Runnable onChanged;
    private String[] order = NebulaBottomBar.tabOrder();
    private int pressed = -1, dragIndex = -1;
    private boolean dragging, tapCancelled;
    private float downX, downY;

    private final Runnable startDrag = () -> {
        if (pressed < 0) return;
        dragging = true;
        dragIndex = pressed;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        invalidate();
    };

    public NebulaTabsEditor(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        tabs = new MainTabsLayout(context, null) {
            @Override protected void setChildVisibilityFactor(View view, float factor) {
                super.setChildVisibilityFactor(view, factor);
                String key = (String) view.getTag();
                boolean enabled = NebulaBottomBar.TAB_CHATS.equals(key) || NebulaBottomBar.tabEnabled(key);
                view.setAlpha(factor * (enabled ? 1f : .4f));
            }
        };
        tabs.setClipChildren(false);
        int padding = AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4);
        tabs.setPadding(padding, padding, padding, padding);
        tabs.setMaxWidth(AndroidUtilities.dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));
        BlurredBackgroundSourceColor source = new BlurredBackgroundSourceColor();
        source.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawable background = new BlurredBackgroundDrawableViewFactory(source)
                .create(tabs, BlurredBackgroundProviderImpl.mainTabs(null));
        background.setRadius(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        background.setPadding(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN - .334f));
        tabs.setBackground(background);
        addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.CENTER));
        refresh();
    }

    public void setOnChanged(Runnable listener) { onChanged = listener; }

    public void refresh() {
        order = NebulaBottomBar.tabOrder();
        boolean reorder = tabs.getChildCount() != order.length;
        for (int i = 0; !reorder && i < order.length; i++) {
            reorder = !order[i].equals(tabs.getChildAt(i).getTag());
        }
        for (String key : order) {
            boolean chats = NebulaBottomBar.TAB_CHATS.equals(key);
            GlassTabView tab = tabViews.get(key);
            if (tab == null) {
                tab = createTab(key);
                tab.setTag(key);
                tab.setSelected(chats, false);
                tabViews.put(key, tab);
                tabs.addView(tab);
                tabs.setViewVisible(tab, true, false);
            }
            // Keep the native selection/Lottie state when labels or visibility change.
            tab.nebulaApplyLabel();
            tab.setAlpha(chats || NebulaBottomBar.tabEnabled(key) ? 1f : .4f);
        }
        if (reorder) for (String key : order) tabs.bringChildToFront(tabViews.get(key));
        tabs.requestLayout();
        requestLayout();
    }

    private GlassTabView createTab(String key) {
        if (NebulaBottomBar.TAB_PROFILE.equals(key)) {
            return GlassTabView.createAvatar(getContext(), null, UserConfig.selectedAccount, R.string.MainTabsProfile);
        }
        GlassTabView.TabAnimation animation = NebulaBottomBar.TAB_CONTACTS.equals(key)
                ? GlassTabView.TabAnimation.CONTACTS : NebulaBottomBar.TAB_SETTINGS.equals(key)
                ? GlassTabView.TabAnimation.SETTINGS : GlassTabView.TabAnimation.CHATS;
        int label = NebulaBottomBar.TAB_CONTACTS.equals(key) ? R.string.MainTabsContacts
                : NebulaBottomBar.TAB_SETTINGS.equals(key) ? R.string.Settings : R.string.MainTabsChats;
        return GlassTabView.createMainTab(getContext(), null, animation, label);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(startDrag);
        finishGesture();
        super.onDetachedFromWindow();
    }

    private int indexAt(float x) {
        float localX = x - tabs.getX();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View child = tabs.getChildAt(i);
            if (localX >= child.getX() && localX < child.getX() + child.getWidth()) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = indexAt(event.getX());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                pressed = index;
                dragIndex = -1;
                dragging = false;
                tapCancelled = false;
                if (pressed >= 0) {
                    postDelayed(startDrag, 360);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && !tapCancelled && (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop)) {
                    removeCallbacks(startDrag);
                    tapCancelled = true;
                    pressed = -1;
                }
                if (dragging && index >= 0 && index != dragIndex) {
                    NebulaBottomBar.moveTab(dragIndex, index);
                    order = NebulaBottomBar.tabOrder();
                    dragIndex = index;
                    pressed = index;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (onChanged != null) onChanged.run();
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(startDrag);
                if (!dragging && !tapCancelled && index >= 0 && index == pressed) {
                    toggle(index);
                }
                finishGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(startDrag);
                finishGesture();
                return true;
            default:
                return true;
        }
    }

    private void finishGesture() {
        if (dragging && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        pressed = -1;
        dragIndex = -1;
        dragging = false;
        tapCancelled = false;
        invalidate();
    }

    private void toggle(int index) {
        String tab = order[index];
        if (NebulaBottomBar.TAB_CHATS.equals(tab)) {
            AndroidUtilities.shakeView(this);
            return;
        }
        NebulaBottomBar.setTabEnabled(tab, !NebulaBottomBar.tabEnabled(tab));
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (onChanged != null) onChanged.run();
        invalidate();
    }
}
