package app.nebulagram.ui;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.PopupWindow;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;

/** Drawing-only window outsets. Content identity, placement and outside-touch bounds stay native. */
public final class NebulaMenuViewport implements View.OnAttachStateChangeListener {
    private final int[] location = new int[2], windowLocation = new int[2];
    private final Rect visible = new Rect();
    private final ArrayList<ViewGroup> ancestors = new ArrayList<>();
    private final ArrayList<Boolean> childrenClips = new ArrayList<>(), paddingClips = new ArrayList<>();
    private View root;
    private PopupWindow window;
    private ActionBarPopupWindowLayout menu;
    private ViewOutlineProvider outline;
    private float elevation, windowElevation;

    private static ActionBarPopupWindowLayout find(View view) {
        if (view instanceof ActionBarPopupWindowLayout) return (ActionBarPopupWindowLayout) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ActionBarPopupWindowLayout result = find(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
    public void prepare(PopupWindow popup, View content, View parent, int gravity, int x, int y, boolean dropdown) {
        if (popup.isShowing()) return;
        restore();
        ActionBarPopupWindowLayout layout = content == null ? null : find(content);
        if (layout == null || !NebulaMenuStyle.animated(layout) || android.os.Build.VERSION.SDK_INT < 21) return;
        Drawable background = popup.getBackground();
        // A nontransparent framework background owns its outline/shadow. Keep its native fade.
        if (background != null && (!(background instanceof ColorDrawable)
                || Color.alpha(((ColorDrawable) background).getColor()) != 0)) return;
        float width = content.getMeasuredWidth(), height = content.getMeasuredHeight();
        if (width <= 0 || height <= 0 || parent == null) return;
        float originX = width, originY = layout.shownFromBottom ? height : 0;
        View source = layout.nebulaReveal.getAnchor();
        if (source != null && source.isAttachedToWindow()) {
            source.getLocationOnScreen(location);
            float sourceX = location[0] + source.getWidth() / 2f;
            float sourceY = location[1] + source.getHeight() / 2f;
            parent.getLocationOnScreen(location);
            float left, top;
            parent.getWindowVisibleDisplayFrame(visible);
            if (dropdown) {
                left = location[0] + x; top = location[1] + parent.getHeight() + y;
                if (top + height > visible.bottom) top = location[1] + y - height;
            } else {
                parent.getLocationInWindow(windowLocation);
                View appRoot = parent.getRootView();
                int absolute = Gravity.getAbsoluteGravity(gravity, parent.getLayoutDirection());
                int horizontal = absolute & Gravity.HORIZONTAL_GRAVITY_MASK;
                int vertical = absolute & Gravity.VERTICAL_GRAVITY_MASK;
                left = location[0] - windowLocation[0] + (horizontal == Gravity.RIGHT ? appRoot.getWidth() - width - x
                        : horizontal == Gravity.CENTER_HORIZONTAL ? (appRoot.getWidth() - width) / 2 + x : x);
                top = location[1] - windowLocation[1] + (vertical == Gravity.BOTTOM ? appRoot.getHeight() - height - y
                        : vertical == Gravity.CENTER_VERTICAL ? (appRoot.getHeight() - height) / 2 + y : y);
            }
            if (popup.isClippingEnabled()) {
                left = Math.max(visible.left, Math.min(visible.right - width, left));
                top = Math.max(visible.top, Math.min(visible.bottom - height, top));
            }
            originX = sourceX - left; originY = sourceY - top;
        }
        float reach = NebulaMenuBubble.outset(width, height, originX, originY,
                AndroidUtilities.dp(48), AndroidUtilities.dp(32));
        // Containers can include a preview above the menu. Cover any menu center inside that root.
        reach = Math.max(reach, Math.max(width, height) * .53f + AndroidUtilities.dp(32));
        root = content; window = popup; menu = layout;
        outline = root.getOutlineProvider(); elevation = root.getElevation(); windowElevation = popup.getElevation();
        root.setOutlineProvider(null);
        // PopupWindow.preparePopup reserves ceil(elevation * 2) surface insets through the SDK.
        // No hidden fields, logical window resize or second window is involved.
        popup.setElevation((float) Math.ceil(reach / 2));
        View node = layout;
        while (true) {
            if (node instanceof ViewGroup) allowDrawing((ViewGroup) node);
            if (node == root || !(node.getParent() instanceof View)) break;
            node = (View) node.getParent();
        }
        menu.nebulaReveal.setViewportReady(true);
        root.addOnAttachStateChangeListener(this);
    }
    private void allowDrawing(ViewGroup group) {
        if (ancestors.contains(group)) return;
        ancestors.add(group); childrenClips.add(group.getClipChildren()); paddingClips.add(group.getClipToPadding());
        group.setClipChildren(false); group.setClipToPadding(false);
    }
    public void restore() {
        if (root == null) return;
        root.removeOnAttachStateChangeListener(this);
        root.setOutlineProvider(outline); root.setElevation(elevation);
        window.setElevation(windowElevation);
        for (int i = 0; i < ancestors.size(); i++) {
            ancestors.get(i).setClipChildren(childrenClips.get(i));
            ancestors.get(i).setClipToPadding(paddingClips.get(i));
        }
        menu.nebulaReveal.setViewportReady(false);
        ancestors.clear(); childrenClips.clear(); paddingClips.clear();
        root = null; window = null; menu = null; outline = null;
    }
    @Override public void onViewAttachedToWindow(View view) {
        // The framework creates its background/decor wrappers inside show(), after prepare().
        // Their default clipChildren would otherwise cut the extended surface back to the menu.
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup) {
            allowDrawing((ViewGroup) parent);
            parent = parent.getParent();
        }
    }
    @Override public void onViewDetachedFromWindow(View view) { restore(); }
}
